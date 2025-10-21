import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.options.Option
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
import java.lang.reflect.Method
import java.util.Locale

/**
 * Gradle task that generates shell completion scripts for Bash and Zsh.
 *
 * This task extracts all CLI options from Gradle's internal API and generates
 * completion data that can be used in shell completion files.
 *
 * This works for Gradle 9.2.0. As it is dependent on the internal API, it might break with future Gradle versions.
 */
@CacheableTask
abstract class GenerateCompletionScriptsTask : DefaultTask() {

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val bashTemplate: RegularFileProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val zshTemplate: RegularFileProperty

    @get:OutputFile
    abstract val bashOutputFile: RegularFileProperty

    @get:OutputFile
    abstract val zshOutputFile: RegularFileProperty

    init {
        group = "build"
        description = "Generates shell completion scripts for Bash and Zsh"
    }

    @TaskAction
    fun generate() {
        logger.lifecycle("Starting generation of completion scripts...")

        // STEP 1: Get the command line options from Gradle's internal API
        val allOptions = collectAllBuildOptions()
        logger.lifecycle("Successfully extracted start parameter from Gradle instance.")

        val allCliOptions = extractCliOptions(allOptions)
        logger.lifecycle("Successfully extracted ${allCliOptions.size} CLI options from Gradle API.")

        // Add help and version options that aren't exposed via the API
        allCliOptions += createHelpOption()
        allCliOptions += createVersionOption()

        val allPropertyNames = extractPropertyOptions(allOptions)

        // STEP 1.5: Extract wrapper task options via reflection
        val wrapperOptions = extractWrapperOptions()
        logger.lifecycle("Successfully extracted ${wrapperOptions.size} wrapper task options.")

        // STEP 1.6: Extract test task options via reflection
        val testOptions = extractTestOptions()
        logger.lifecycle("Successfully extracted ${testOptions.size} test task options.")

        // STEP 2: Format the extracted options into Bash and Zsh specific strings
        val bashLongOpts = generateBashLongOpts(allCliOptions)
        val bashShortOpts = getBashShortOpts(allCliOptions)
        allCliOptions += allPropertyNames
        val zshMainOpts = generateZshMainOpts(allCliOptions)
        val zshSubcommandOpts = generateZshSubcommandOpts(allCliOptions)
        val properties = generatePropertiesOpts(allPropertyNames)
        val zshWrapperOpts = generateZshWrapperOpts(wrapperOptions)
        val zshTestOpts = generateZshTestOpts(testOptions)

        // STEP 3: Read templates and substitute placeholders
        val bashCompletionScript = bashTemplate.asFile.get().readText()
            .replace("{{GENERATED_BASH_LONG_OPTIONS}}", bashLongOpts.trimEnd())
            .replace("{{GENERATED_BASH_SHORT_OPTIONS}}", bashShortOpts.trimEnd())
            .replace("{{GENERATED_GRADLE_PROPERTIES}}", properties.trimEnd())

        val zshCompletionScript = zshTemplate.asFile.get().readText()
            .replace("{{GENERATED_ZSH_MAIN_OPTIONS}}", zshMainOpts)
            .replace("{{GENERATED_ZSH_SUBCOMMAND_OPTIONS}}", zshSubcommandOpts)
            .replace("{{GENERATED_ZSH_WRAPPER_OPTIONS}}", zshWrapperOpts)
            .replace("{{GENERATED_ZSH_TEST_OPTIONS}}", zshTestOpts)

        // STEP 4: Write generated files
        bashOutputFile.asFile.get().writeText(bashCompletionScript)
        zshOutputFile.asFile.get().writeText(zshCompletionScript)

        logger.lifecycle("Generated completion scripts:")
        logger.lifecycle("  - ${bashOutputFile.asFile.get().name}")
        logger.lifecycle("  - ${zshOutputFile.asFile.get().name}")
        logger.lifecycle("\nGeneration complete!")
    }

    private fun collectAllBuildOptions(): List<BuildOption<*>> {
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

    private fun extractCliOptions(allOptions: List<BuildOption<*>>): MutableList<CliOption> {
        return allOptions.map { option ->
            val field = findDeclaredField(option, "commandLineOptionConfigurations")
            val configurations = field?.get(option) as List<*>
            val configs = configurations.map { it as CommandLineOptionConfiguration }
            option to configs
        }
            .filter { it.second.isNotEmpty() }
            .flatMap { (option, configurations) ->
                configurations.flatMap { optionDetails ->
                    val opts = mutableListOf<CliOption>()
                    if (optionDetails.longOption != null) {
                        val filePattern = getFilePattern(optionDetails.longOption)
                        val isDirectory = isDirectoryOption(optionDetails.longOption)

                        val enabledOption = CliOption(
                            twoDashOption = optionDetails.longOption,
                            oneDashOption = optionDetails.shortOption,
                            description = optionDetails.description.replace("\"", "'"),
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
    }

    private fun extractPropertyOptions(allOptions: List<BuildOption<*>>): List<CliOption> {
        return allOptions
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
    }

    private fun createHelpOption(): CliOption {
        return CliOption(
            twoDashOption = "help",
            oneDashOption = "h",
            description = "Shows a help message."
        )
    }

    private fun createVersionOption(): CliOption {
        return CliOption(
            twoDashOption = "version",
            oneDashOption = "v",
            description = "Shows the version info."
        )
    }

    private fun generateBashLongOpts(options: List<CliOption>): String = options.filter { it.twoDashOption != null }
        .sortedBy { it.twoDashOption }
        .joinToString("\n") {
            it.getSuggestionLine("--${it.twoDashOption}")
        }


    private fun getBashShortOpts(options: List<CliOption>): String = options.filter { it.oneDashOption != null }
        .sortedBy { it.oneDashOption?.lowercase(Locale.getDefault()) }
        .joinToString("\n") {
            it.getSuggestionLine("-${it.oneDashOption}")
        }

    private fun generateZshMainOpts(options: List<CliOption>): String {
        return options.sortedBy { it.twoDashOption ?: it.oneDashOption }
            .joinToString(" \\\n        ") { option ->
                option.getOptionLine(includeArgumentExpected = true)
            }
    }

    private fun generateZshSubcommandOpts(options: List<CliOption>): String {
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

    private fun generatePropertiesOpts(allPropertyNames: List<CliOption>): String {
        return allPropertyNames
            .filter { it.oneDashOption != null }
            .joinToString("\n") {
            val paddingOption = "-${it.oneDashOption!!.padEnd(40)}"
            "$paddingOption - ${it.description?.lineSequence()?.first() ?: ""}"
        }
    }

    /**
     * Extracts wrapper task options from org.gradle.api.tasks.wrapper.Wrapper class via reflection.
     */
    private fun extractWrapperOptions(): List<WrapperOption> {
        return TaskOptionExtractor.extractTaskOptions(
            listOf("org.gradle.api.tasks.wrapper.Wrapper"),
            "wrapper",
            logger
        )
    }

    /**
     * Extracts test task options from org.gradle.api.tasks.testing.Test class and its parent
     * AbstractTestTask via reflection.
     */
    private fun extractTestOptions(): List<WrapperOption> {
        return TaskOptionExtractor.extractTaskOptions(
            listOf(
                "org.gradle.api.tasks.testing.Test",
                "org.gradle.api.tasks.testing.AbstractTestTask"
            ),
            "test",
            logger
        )
    }

    /**
     * Generates Zsh completion options for the wrapper task.
     */
    private fun generateZshWrapperOpts(wrapperOptions: List<WrapperOption>): String {
        return TaskOptionExtractor.generateZshTaskOpts(wrapperOptions)
    }

    /**
     * Generates Zsh completion options for the test task.
     */
    private fun generateZshTestOpts(testOptions: List<WrapperOption>): String {
        return TaskOptionExtractor.generateZshTaskOpts(testOptions, "'(-)*:: :->task-or-option'")
    }

    // Companion object for helper functions and constants
    companion object {
        private val CACHING_VALUES = listOf("true", "false")
        private val CC_CACHE_PROBLEMS = listOf("fail", "warn")
        private val CONSOLE_TYPES = listOf("plain", "auto", "rich", "verbose")

        private val HARDCODED_POSSIBLE_VALUES = mapOf(
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

        private val DIRECTORY_OPTIONS = setOf(
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

        private val FILE_OPTIONS = mapOf(
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
                    currentClass = currentClass.superclass
                }
            }
            return null
        }

        fun getPossibleValues(option: BuildOption<*>, longOptionName: String?): List<String> {
            longOptionName?.let { optName ->
                HARDCODED_POSSIBLE_VALUES[optName]?.let { return it }
            }
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
            HARDCODED_POSSIBLE_VALUES[propertyName]?.let { return it }
            return getPossibleValuesList(option)
        }

        fun isDirectoryProperty(propertyName: String): Boolean {
            return propertyName in DIRECTORY_OPTIONS
        }

        fun getFilePatternForProperty(propertyName: String): String? {
            return FILE_OPTIONS[propertyName]
        }
    }
}
