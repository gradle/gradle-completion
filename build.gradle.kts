import org.gradle.initialization.BuildLayoutParametersBuildOptions
import org.gradle.initialization.ParallelismBuildOptions
import org.gradle.initialization.StartParameterBuildOptions
import org.gradle.internal.buildoption.AbstractBuildOption
import org.gradle.internal.buildoption.BooleanCommandLineOptionConfiguration
import org.gradle.internal.buildoption.BuildOption
import org.gradle.internal.buildoption.CommandLineOptionConfiguration
import org.gradle.internal.buildoption.EnumBuildOption
import org.gradle.internal.buildoption.IntegerBuildOption
import org.gradle.internal.buildoption.ListBuildOption
import org.gradle.internal.buildoption.StringBuildOption
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
    val argumentExpected: Boolean = false,
    val mutuallyExclusiveWith: MutableList<String> = mutableListOf(),
    val possibleValues: List<String> = listOf(),
    val isDirectory: Boolean = false,
    val filePattern: String? = null  // e.g., "*.gradle" or "*.{gradle,kts}"
) {
    fun addMutexOption(other: CliOption) {
        if (other.twoDashOption != null) {
            mutuallyExclusiveWith += "--${other.twoDashOption}"
        }
        if (other.oneDashOption != null) {
            mutuallyExclusiveWith += "-${other.oneDashOption}"
        }
    }

    fun addMutexOption(other: CommandLineOptionConfiguration) {
        mutuallyExclusiveWith += "--${other.longOption}"
        if (other.shortOption != null) {
            mutuallyExclusiveWith += "-${other.shortOption}"
        }
    }

    fun getMultiplePrefix(): String = if (multipleOccurrencePossible) "\\*" else ""

    fun formatOptionStr(): String {
        val optBuilder = mutableListOf<String>()
        if (oneDashOption != null) {
            optBuilder.add("-$oneDashOption")
        }
        if (twoDashOption != null) {
            optBuilder.add("--$twoDashOption")
        }
        return if (optBuilder.size > 1) {
            // Multiple options: use braces {-a,--long}
            optBuilder.joinToString(",", prefix = "{", postfix = "}")
        } else {
            // Single option: no braces needed
            optBuilder.firstOrNull() ?: ""
        }
    }

    fun getOptionName(): String? =
        (twoDashOption ?: oneDashOption?.removePrefix("D")?.removeSuffix("="))
            ?.replace("-", " ")


    fun generateArgumentPart(
        includeArgumentExpected: Boolean
    ): String {
        if (!argumentExpected) return ""

        // Handle file options with patterns
        if (filePattern != null) {
            val label = getOptionName() ?: "file"
            val suffix = if (includeArgumentExpected) ":->argument-expected" else ""
            return ":$label:_files -g \\${filePattern}$suffix"
        }

        // Handle directory options
        if (isDirectory) {
            val label = getOptionName() ?: "directory"
            val suffix = if (includeArgumentExpected) ":->argument-expected" else ""
            return ":$label:_directories$suffix"
        }

        if (possibleValues.isEmpty()) {
            return if (includeArgumentExpected) ":->argument-expected" else ""
        }

        // Create a label from the option name
        val label = getOptionName() ?: "value"
        val suffix = if (includeArgumentExpected) ":->argument-expected" else ""
        return ":$label:(${possibleValues.joinToString(" ")})$suffix"
    }

    fun getOptionLine(includeArgumentExpected: Boolean): String {
        val multiplePrefix = getMultiplePrefix()
        val mutex = getMutexOptions()
        val incubatingText = if (incubating) " (incubating)" else ""
        val description = getCleanDescription()
        val optStr = formatOptionStr()
        val argumentPart = generateArgumentPart(includeArgumentExpected = includeArgumentExpected)

        val commonPostfix = "[${description}${incubatingText}]${argumentPart}"
        val commonPrefix = "${multiplePrefix}${mutex}"

        // Determine what goes outside quotes (multiplePrefix, mutex, and/or braces)
        return when {
            // Has braces but no mutex: braces go outside quotes (along with multiplePrefix)
            optStr.contains("{") -> {
                "${commonPrefix}${optStr}'$commonPostfix'"
            }
            // Everything else: all inside quotes
            else -> {
                "${commonPrefix}'${optStr}$commonPostfix'"
            }
        }
    }

    fun getCleanDescription(): String =
        description?.lineSequence()?.first()?.replace("[", "(")?.replace("]", ")") ?: ""

    fun getMutexOptions(): String =
        if (mutuallyExclusiveWith.isNotEmpty()) {
            "(${mutuallyExclusiveWith.joinToString(",")})"
        } else {
            ""
        }
}


// Hardcoded possible values for specific options
// For --option use the long option name
// For -D properties use the property name (without "D" prefix)
val CACHING_VALUES = listOf("true", "false")
val CC_CACHE_PROBLEMS = listOf("fail", "warn")
val CONSOLE_TYPES = listOf("plain", "auto", "rich", "verbose")
val HARDCODED_POSSIBLE_VALUES = mapOf(
    "refresh" to listOf("dependencies"),
    "org.gradle.caching" to CACHING_VALUES,
    "build-cache" to CACHING_VALUES,
    "org.gradle.daemon.debug" to listOf("true", "false"),
    "org.gradle.debug" to listOf("true", "false"),
    "org.gradle.parallel" to listOf("true", "false"),
    "org.gradle.vfs.watch" to listOf("true", "false"),
    "org.gradle.priority" to listOf("normal", "low"),
    "org.gradle.console" to CONSOLE_TYPES,
    "console" to CONSOLE_TYPES,
    "org.gradle.logging.level" to listOf("quiet", "warn", "info", "debug"),
    "configuration-cache-problems" to CC_CACHE_PROBLEMS,
    "org.gradle.configuration-cache.problems" to CC_CACHE_PROBLEMS,
    "warning-mode" to listOf("all", "summary", "none"),
    "org.gradle.welcome" to listOf("once", "never")
)

// Options that expect directory paths
// For --option use the long option name
// For -D properties use the property name (without "D" prefix)
val DIRECTORY_OPTIONS = setOf(
    "gradle.user.home",
    "org.gradle.configuration-cache.heap-dump-dir",
    "org.gradle.daemon.registry.base",
    "org.gradle.java.installations.idea-jdks-directory",
    "org.gradle.java.installations.paths",
    "org.gradle.projectcachedir",
    "gradle-user-home",
    "org.gradle.java.home",
    "project-cache-dir",
    "project-dir",
    "include-build"
)

// Options that expect file paths with specific patterns
// For --option use the long option name
// For -D properties use the property name (without "D" prefix)
// Value is the glob pattern for _files (e.g., "*.gradle" or "*.{gradle,kts}")
val FILE_OPTIONS = mapOf(
    "init-script" to "*.gradle(|.kts)"
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

fun getPossibleValues(option: BuildOption<*>, longOptionName: String?): List<String> {
    // Check hardcoded values first based on the long option name
    longOptionName?.let { optName ->
        HARDCODED_POSSIBLE_VALUES[optName]?.let { return it }
    }

    // Otherwise extract from EnumBuildOption
    return getPossibleValuesList(option)
}

fun isDirectoryOption(longOptionName: String?): Boolean {
    return longOptionName?.let { it in DIRECTORY_OPTIONS } ?: false
}

fun getFilePattern(longOptionName: String?): String? {
    return longOptionName?.let { FILE_OPTIONS[it] }
}

fun getPossibleValuesList(option: BuildOption<*>): List<String> {
    return if (option is EnumBuildOption<*, *>) {
        val valuesField = findDeclaredField(option, "possibleValues")
        @Suppress("UNCHECKED_CAST")
        (valuesField?.get(option) as? List<Any>)?.map { it.toString().lowercase() } ?: listOf()
    } else {
        listOf()
    }
}

fun getPossibleValuesForProperty(option: BuildOption<*>, propertyName: String): List<String> {
    // Check hardcoded values first based on the property name
    HARDCODED_POSSIBLE_VALUES[propertyName]?.let { return it }

    // Otherwise extract from EnumBuildOption
    return getPossibleValuesList(option)
}

fun isDirectoryProperty(propertyName: String): Boolean {
    return propertyName in DIRECTORY_OPTIONS
}

fun getFilePatternForProperty(propertyName: String): String? {
    return FILE_OPTIONS[propertyName]
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
                        val filePattern = getFilePattern(optionDetails.longOption)
                        val isDirectory = isDirectoryOption(optionDetails.longOption)

                        val enabledOption = CliOption(
                            twoDashOption = optionDetails.longOption,
                            oneDashOption = optionDetails.shortOption,
                            description = optionDetails.description.replace("\"", "'"), // Escape quotes
                            incubating = optionDetails.isIncubating,
                            multipleOccurrencePossible = option is ListBuildOption<*>,
                            argumentExpected = option is StringBuildOption ||
                                    option is IntegerBuildOption ||
                                    option is EnumBuildOption<*, *> ||
                                    isDirectory ||
                                    filePattern != null,
                            possibleValues = getPossibleValues(option, optionDetails.longOption),
                            isDirectory = isDirectory,
                            filePattern = filePattern
                        )
                        opts += enabledOption
                        if (optionDetails is BooleanCommandLineOptionConfiguration) {
                            val disabledOption = CliOption(
                                twoDashOption = "no-${optionDetails.longOption}",
                                oneDashOption = null,
                                description = optionDetails.disabledDescription,
                                incubating = optionDetails.isIncubating,
                                argumentExpected = false
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

        allCliOptions += CliOption(
            twoDashOption = "version",
            oneDashOption = "v",
            description = "Shows the version info."
        )

        val allPropertyNames = allOptions
            .filter { it.property != null && !it.property?.contains(".internal.")!! }
            .mapNotNull { option ->
                val field = findDeclaredField(option, "commandLineOptionConfigurations")
                val configurations = field?.get(option) as List<*>
                val cliConfigs = configurations.map { it as CommandLineOptionConfiguration }

                option.property?.let { propName ->
                    val shortOption = "D$propName="
                    CliOption(
                        oneDashOption = shortOption,
                        description = if (option is AbstractBuildOption<*, *> && cliConfigs.size == 1) {
                            cliConfigs.first().description ?: ""
                        } else {
                            ""
                        },
                        argumentExpected = true,
                        possibleValues = getPossibleValuesForProperty(option, propName),
                        isDirectory = isDirectoryProperty(propName),
                        filePattern = getFilePatternForProperty(propName)
                    )
                }
            }


        // STEP 2: Format the extracted options into Bash and Zsh specific strings.
        val bashLongOpts = generateBashLongOpts(allCliOptions)
        val bashShortOpts = getBashShortOpts(allCliOptions)
        allCliOptions += allPropertyNames
        val zshMainOpts = generateZshMainOpts(allCliOptions)
        val zshSubcommandOpts = generateZshSubcommandOpts(allCliOptions)
        val properties = generatePropertiesOpts(allPropertyNames)

        // STEP 3: Read templates and substitute placeholders
        val bashTemplate = file("gradle-completion.bash.template").readText()
        val zshTemplate = file("_gradle.template").readText()

        val bashCompletionScript = bashTemplate
            .replace("{{GENERATED_BASH_LONG_OPTIONS}}", bashLongOpts.trimEnd())
            .replace("{{GENERATED_BASH_SHORT_OPTIONS}}", bashShortOpts.trimEnd())
            .replace("{{GENERATED_GRADLE_PROPERTIES}}", properties.trimEnd())

        val zshCompletionScript = zshTemplate
            .replace("{{GENERATED_ZSH_MAIN_OPTIONS}}", zshMainOpts)
            .replace("{{GENERATED_ZSH_SUBCOMMAND_OPTIONS}}", zshSubcommandOpts)

        // STEP 4: Write generated files
        file("gradle-completion.bash").writeText(bashCompletionScript)
        file("_gradle").writeText(zshCompletionScript)

        println("Generated completion scripts:")
        println("  - gradle-completion.bash")
        println("  - _gradle")
        println("\nGeneration complete!")
    }
}

fun generateBashLongOpts(options: List<CliOption>): String = options.filter { it.twoDashOption != null }
    .sortedBy { it.twoDashOption }
    .joinToString("\n") {
        val incubatingText = if (it.incubating) " [incubating]" else ""
        val paddedOption = "--${it.twoDashOption}".padEnd(30)
        "$paddedOption - ${it.description?.lineSequence()?.first()} $incubatingText"
    }

fun getBashShortOpts(options: List<CliOption>): String = options.filter { it.oneDashOption != null }
    .sortedBy { it.oneDashOption?.lowercase(Locale.getDefault()) }
    .joinToString("\n") {
        val incubatingText = if (it.incubating) " [incubating]" else ""
        val paddedOption = "-${it.oneDashOption}".padEnd(30)
        "$paddedOption - ${it.description?.lineSequence()?.first()} $incubatingText"
    }


fun generateZshMainOpts(options: List<CliOption>): String {
    return options.sortedBy { it.twoDashOption ?: it.oneDashOption }
        .joinToString(" \\\n        ") { option ->
            option.getOptionLine(includeArgumentExpected = true)
        }
}

fun generateZshSubcommandOpts(options: List<CliOption>): String {
    // Filter out standalone options (help, version, status, stop, gui)
    val standaloneOptions = setOf("help", "h", "version", "v", "status", "stop", "gui")
    return options
        .filter { option ->
            option.twoDashOption !in standaloneOptions &&
                    option.oneDashOption !in standaloneOptions
        }
        .sortedBy { it.twoDashOption ?: it.oneDashOption }
        .joinToString(" \\\n                ") { option ->
            option.getOptionLine(includeArgumentExpected = false)
        }
}

fun generatePropertiesOpts(allPropertyNames: List<CliOption>): String {
    return allPropertyNames.joinToString("\n") {
        val paddingOption = "-${it.oneDashOption!!.padEnd(40)}"
        "$paddingOption - ${it.description?.lineSequence()?.first() ?: ""}"
    }
}