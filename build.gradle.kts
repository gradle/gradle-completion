import org.gradle.initialization.BuildLayoutParametersBuildOptions
import org.gradle.initialization.ParallelismBuildOptions
import org.gradle.initialization.StartParameterBuildOptions
import org.gradle.internal.buildoption.BooleanCommandLineOptionConfiguration
import org.gradle.internal.buildoption.BuildOption
import org.gradle.internal.buildoption.CommandLineOptionConfiguration
import org.gradle.internal.logging.LoggingConfigurationBuildOptions
import org.gradle.launcher.cli.converter.WelcomeMessageBuildOptions
import org.gradle.launcher.daemon.configuration.DaemonBuildOptions
import org.gradle.launcher.daemon.toolchain.ToolchainBuildOptions
import java.lang.reflect.Field

// Define a simple data class to hold the structured option data.
data class CliOption(
    val longOption: String,
    val shortOption: String? = null,
    val description: String,
    val incubating: Boolean = false,
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
    val allBuildOptions = mutableListOf<BuildOption<*>>()
    allBuildOptions.addAll(BuildLayoutParametersBuildOptions().allOptions)
    allBuildOptions.addAll(StartParameterBuildOptions().allOptions)
    allBuildOptions.addAll(LoggingConfigurationBuildOptions().allOptions)
    allBuildOptions.addAll(WelcomeMessageBuildOptions().allOptions)
    allBuildOptions.addAll(DaemonBuildOptions().allOptions)
    allBuildOptions.addAll(ParallelismBuildOptions().allOptions)
    allBuildOptions.addAll(ToolchainBuildOptions.forToolChainConfiguration().getAllOptions())
    return allBuildOptions
}

fun CliOption.addMutexOption(other: CliOption): Unit {
    mutuallyExclusiveWith += "--${other.longOption}"
    if (other.shortOption != null) {
        mutuallyExclusiveWith += "-${other.shortOption}"
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
            configurations.map { it as CommandLineOptionConfiguration }
        }
            .filter { it.isNotEmpty() }
            .flatMap { configurations ->
                configurations.flatMap { optionDetails ->
                    val opts = mutableListOf<CliOption>()
                    if (optionDetails.longOption != null) {
                        val enabledOption = CliOption(
                            longOption = optionDetails.longOption,
                            shortOption = optionDetails.shortOption,
                            description = optionDetails.description.replace("\"", "'"), // Escape quotes
                            incubating = optionDetails.isIncubating,
                        )
                        opts += enabledOption
                        if (optionDetails is BooleanCommandLineOptionConfiguration) {
                            val disabledOption = CliOption(
                                longOption = "no-${optionDetails.longOption}",
                                shortOption = null,
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
            }
        println("Successfully extracted ${allCliOptions.size} CLI options from Gradle API.")

        val allPropertyNames = allOptions.mapNotNull {
            it.property
        }

        // STEP 2: Format the extracted options into Bash and Zsh specific strings.
        val bashLongOpts = generateBashLongOpts(allCliOptions)
        // STEP 3: Write the generated strings into the script files.
        println("\n--- GENERATED BASH OUTPUT ---")
        println(bashLongOpts)

        val bashShortOpts = getBashShortOpts(allCliOptions)
        println("\n--- GENERATED BASH SHORT OPTIONS OUTPUT ---")
        println(bashShortOpts)

        println("\n--- GENERATED ZSH OUTPUT ---")
        val zshLongOpts = generateZshLongOpts(allCliOptions)
        println(zshLongOpts)

        println("\nGeneration complete. Copy the output into your completion script files.")
    }
}

fun generateBashLongOpts(options: List<CliOption>): String {
    val builder = StringBuilder()
    builder.appendLine("local args=\\\"")
    options.forEach {
        val incubatingText = if (it.incubating) " [incubating]" else ""
        val paddedOption = "--${it.longOption}".padEnd(30)
        builder.appendLine("    ${paddedOption}-${it.description.lineSequence().first()} $incubatingText")
    }
    builder.appendLine("\"\"")
    return builder.toString()
}

fun getBashShortOpts(options: List<CliOption>): String {
    val builder = StringBuilder()
    builder.appendLine("local args=\\\"")
    options.sortedBy { it.shortOption }.filter { it.shortOption != null }.forEach {
        val incubatingText = if (it.incubating) " [incubating]" else ""
        val paddedOption = "-${it.shortOption}".padEnd(30)
        builder.appendLine("    ${paddedOption}-${it.description.lineSequence().first()} $incubatingText")
    }
    builder.appendLine("\"\"")
    return builder.toString()
}

fun generateZshLongOpts(options: List<CliOption>): String {
    val builder = StringBuilder()
    options.sortedBy { it.longOption }.forEach { option ->
        val mutex = if (option.mutuallyExclusiveWith.isNotEmpty()) {
            "(${option.mutuallyExclusiveWith.joinToString(",")})"
        } else {
            ""
        }
        val incubatingText = if (option.incubating) " [incubating]" else ""
        val description = option.description.lineSequence().first().replace("[", "(").replace("]", ")")

        val shortOptStr = option.shortOption?.let { "{-$it,--${option.longOption}}" } ?: "--${option.longOption}"

        builder.appendLine("        '$mutex$shortOptStr'[${description}${incubatingText}]' \\\\")
    }
    return builder.toString()
}

// Make this the default task for convenience
//tasks.named("default") {
//    dependsOn("generateCompletionScripts")
//}