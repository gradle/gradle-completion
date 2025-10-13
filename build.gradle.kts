import org.gradle.initialization.BuildLayoutParametersBuildOptions
import org.gradle.initialization.ParallelismBuildOptions
import org.gradle.initialization.StartParameterBuildOptions
import org.gradle.internal.buildoption.AbstractBuildOption
import org.gradle.internal.buildoption.BooleanCommandLineOptionConfiguration
import org.gradle.internal.buildoption.BuildOption
import org.gradle.internal.buildoption.CommandLineOptionConfiguration
import org.gradle.internal.buildoption.ListBuildOption
import org.gradle.internal.logging.LoggingConfigurationBuildOptions
import org.gradle.launcher.cli.converter.WelcomeMessageBuildOptions
import org.gradle.launcher.daemon.configuration.DaemonBuildOptions
import org.gradle.launcher.daemon.toolchain.ToolchainBuildOptions
import java.lang.reflect.Field
import java.util.Locale

// Define a simple data class to hold the structured option data.
data class CliOption(
    val twoDashOption: String? = null,
    val oneDashOption: String? = null,
    val description: String? = null,
    val incubating: Boolean = false,
    val multipleOccurrencePossible: Boolean = false,
    val mutuallyExclusiveWith: MutableList<String> = mutableListOf<String>(),
)

fun findDeclaredField(obj: Any, fieldName: String): Field? {
    var currentClass: Class<*>? = obj.javaClass

    while (currentClass != null) {
        try {
            return currentClass.getDeclaredField(fieldName).apply {
                isAccessible = true
            }
        } catch (e: NoSuchFieldException) {
            // Field not found in this class, move to parent
            currentClass = currentClass.superclass
        }
    }

    return null
}

// this is from org.gradle.launcher.cli.converter.LayoutToPropertiesConverter:54
fun collectAllBuildOptions(): List<BuildOption<*>> {
    return listOf(
        BuildLayoutParametersBuildOptions(),
        StartParameterBuildOptions(),
        LoggingConfigurationBuildOptions(),
        WelcomeMessageBuildOptions(),
        DaemonBuildOptions(),
        ParallelismBuildOptions(),
        ToolchainBuildOptions.forToolChainConfiguration()
    ).flatMap { it.allOptions }
}

fun CliOption.addMutexOption(other: CliOption): Unit {
    if (other.twoDashOption != null) {
        mutuallyExclusiveWith += "--${other.twoDashOption}"
    }
    if (other.oneDashOption != null) {
        mutuallyExclusiveWith += "-${other.oneDashOption}"
    }
}

fun CliOption.addMutexOption(other: CommandLineOptionConfiguration): Unit {
    mutuallyExclusiveWith += "--${other.longOption}"
    if (other.shortOption != null) {
        mutuallyExclusiveWith += "-${other.shortOption}"
    }
}

tasks.register("generateCompletionScripts") {
    doLast {
        println("Starting generation of completion scripts...")

        // STEP 1: Get the command line options from Gradle's internal API.
        val allOptions = collectAllBuildOptions()

        println("Successfully extracted start parameter from Gradle instance.")
        val allCliOptions = allOptions.map { option ->
            val field = findDeclaredField(option, "commandLineOptionConfigurations")
            val configurations = field?.get(option) as List<*>
            val configs = configurations.map { it as CommandLineOptionConfiguration }
            option to configs  // Return a pair of the option and its configs
        }
            .filter { it.second.isNotEmpty() }
            .flatMap { (option, configurations) ->  // Destructure the pair
                configurations.flatMap { optionDetails ->
                    val opts = mutableListOf<CliOption>()
                    if (optionDetails.longOption != null) {
                        val enabledOption = CliOption(
                            twoDashOption = optionDetails.longOption,
                            oneDashOption = optionDetails.shortOption,
                            description = optionDetails.description.replace("\"", "'"),
                            incubating = optionDetails.isIncubating,
                            multipleOccurrencePossible = option is ListBuildOption<*>  // Now option is in scope
                        )
                        opts += enabledOption
                        if (optionDetails is BooleanCommandLineOptionConfiguration) {
                            val disabledOption = CliOption(
                                twoDashOption = "no-${optionDetails.longOption}",
                                oneDashOption = null,
                                description = optionDetails.disabledDescription,
                                incubating = optionDetails.isIncubating
                            )

                            disabledOption.addMutexOption(enabledOption)
                            opts += disabledOption
                            enabledOption.addMutexOption(disabledOption)
                        }

                        configurations.filter { it != optionDetails }.forEach { cliO ->
                            enabledOption.addMutexOption(cliO)
                        }
                        opts
                    } else {
                        listOf()
                    }
                }
            }.toMutableList()
        println("Successfully extracted ${allCliOptions.size} CLI options from Gradle API.")

        // Add help options that aren't exposed via the API
        allCliOptions += CliOption(
            twoDashOption = "help",
            oneDashOption = "h",
            description = "Shows a help message."
        )

        val allPropertyNames = allOptions
            .filter { it.property != null && !it.property?.contains(".internal.")!! }
            .mapNotNull { option ->
                val field = findDeclaredField(option, "commandLineOptionConfigurations")
                val configurations = field?.get(option) as List<*>
                val cliConfigs = configurations.map { it as CommandLineOptionConfiguration }

                option.property?.let { propName ->

                    val shortOption = "D$propName="
                    if (option is AbstractBuildOption<*, *>) {
                        CliOption(
                            oneDashOption = shortOption, description = if (cliConfigs.size == 1) {
                                cliConfigs.first().description ?: ""
                            } else {
                                ""
                            }
                        )
                    } else {
                        CliOption(oneDashOption = shortOption)
                    }
                }
            }


        // STEP 2: Format the extracted options into Bash and Zsh specific strings.
        val bashLongOpts = generateBashLongOpts(allCliOptions)
        val bashShortOpts = getBashShortOpts(allCliOptions)
        allCliOptions += allPropertyNames
        val zshLongOpts = generateZshOpts(allCliOptions)
        val properties = generatePropertiesOpts(allPropertyNames)

        // STEP 3: Read templates and substitute placeholders
        val bashTemplate = file("gradle-completion.bash.template").readText()
        val zshTemplate = file("_gradle.template").readText()

        val bashCompletionScript = bashTemplate
            .replace("{{GENERATED_BASH_LONG_OPTIONS}}", bashLongOpts.trimEnd())
            .replace("{{GENERATED_BASH_SHORT_OPTIONS}}", bashShortOpts.trimEnd())
            .replace("{{GENERATED_GRADLE_PROPERTIES}}", properties.trimEnd())

        val zshCompletionScript = zshTemplate
            .replace("{{GENERATED_ZSH_OPTIONS}}", zshLongOpts)

        // STEP 4: Write generated files
        file("gradle-completion.bash").writeText(bashCompletionScript)
        file("_gradle").writeText(zshCompletionScript)

        println("Generated completion scripts:")
        println("  - gradle-completion.bash")
        println("  - _gradle")
        println("\nGeneration complete!")
    }
}

fun generateBashLongOpts(options: List<CliOption>): String {
    val builder = StringBuilder()
    options.filter { it.twoDashOption != null }.sortedBy { it.twoDashOption }.forEach {
        val incubatingText = if (it.incubating) " [incubating]" else ""
        val paddedOption = "--${it.twoDashOption}".padEnd(30)
        builder.appendLine("    $paddedOption - ${it.description?.lineSequence()?.first()} $incubatingText")
    }
    return builder.toString()
}

fun getBashShortOpts(options: List<CliOption>): String {
    val builder = StringBuilder()
    options.filter { it.oneDashOption != null }.sortedBy { it.oneDashOption?.lowercase(Locale.getDefault()) }.forEach {
        val incubatingText = if (it.incubating) " [incubating]" else ""
        val paddedOption = "-${it.oneDashOption}".padEnd(30)
        builder.appendLine("    $paddedOption - ${it.description?.lineSequence()?.first()} $incubatingText")
    }
    return builder.toString()
}

fun generateZshOpts(options: List<CliOption>): String {
    val builder = StringBuilder()
    options.sortedBy { it.twoDashOption }.forEach { option ->
        val multiplePrefix = if (option.multipleOccurrencePossible) "\\*" else ""
        val mutex = if (option.mutuallyExclusiveWith.isNotEmpty()) {
            "(${option.mutuallyExclusiveWith.joinToString(",")})"
        } else {
            ""
        }
        val incubatingText = if (option.incubating) " [incubating]" else ""
        val description = option.description?.lineSequence()?.first()?.replace("[", "(")?.replace("]", ")")

        val optBuilder = mutableListOf<String>()
        if (option.oneDashOption != null) {
            optBuilder.add("-${option.oneDashOption}")
        }
        if (option.twoDashOption != null) {
            optBuilder.add("--${option.twoDashOption}")
        }
        val optStr = optBuilder.joinToString(",", prefix = "{", postfix = "}")

        builder.appendLine("        '$multiplePrefix$mutex$optStr'[${description}${incubatingText}]' \\\\")
    }
    return builder.toString()
}

fun generatePropertiesOpts(allPropertyNames: List<CliOption>): String {
    val builder = StringBuilder()
    allPropertyNames.forEach {
        val paddingOption = "-${it.oneDashOption!!.padEnd(40)}"
        builder.appendLine("${paddingOption} - ${it.description?.lineSequence()?.first() ?: ""}")
                                                   //^ we need the dash for the bash completion to work
    }

    return builder.toString()
}
