import TaskOptionExtractor.generateZshTaskOpts
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
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
import org.gradle.internal.extensions.stdlib.uncheckedCast
import org.gradle.internal.logging.LoggingConfigurationBuildOptions
import org.gradle.launcher.cli.converter.WelcomeMessageBuildOptions
import org.gradle.launcher.daemon.configuration.DaemonBuildOptions
import org.gradle.launcher.daemon.toolchain.ToolchainBuildOptions
import java.lang.reflect.Field
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

        val allOptions = collectAllBuildOptions()
        logger.lifecycle("Successfully extracted start parameter from Gradle instance.")

        val allCliOptions = extractCliOptions(allOptions)
        logger.lifecycle("Successfully extracted ${allCliOptions.size} CLI options from Gradle API.")

        allCliOptions += listOf(
            createHelpOption(),
            createVersionOption(),
            createShowVersionOption(),
            createRerunOption()
        )

        val allPropertyNames = extractPropertyOptions(allOptions)

        val wrapperOptions = extractWrapperOptions()
        logger.lifecycle("Successfully extracted ${wrapperOptions.size} wrapper task options.")

        val testOptions = extractTestOptions()
        logger.lifecycle("Successfully extracted ${testOptions.size} test task options.")

        val initOptions = extractInitOptions()
        logger.lifecycle("Successfully extracted ${initOptions.size} init task options.")

        val dependenciesOptions = extractDependenciesOptions()
        logger.lifecycle("Successfully extracted ${dependenciesOptions.size} dependencies task options.")

        val dependencyInsightOptions = extractDependencyInsightOptions()
        logger.lifecycle("Successfully extracted ${dependencyInsightOptions.size} dependencyInsight task options.")

        val tasksOptions = extractTasksOptions()
        logger.lifecycle("Successfully extracted ${tasksOptions.size} tasks task options.")

        val helpOptions = extractHelpOptions()
        logger.lifecycle("Successfully extracted ${helpOptions.size} help task options.")

        val bashLongOpts = generateBashLongOpts(allCliOptions)
        val bashShortOpts = getBashShortOpts(allCliOptions)
        allCliOptions += allPropertyNames
        val zshMainOpts = generateZshMainOpts(allCliOptions)
        val zshSubcommandOpts = generateZshSubcommandOpts(allCliOptions)
        val properties = generatePropertiesOpts(allPropertyNames)
        val zshWrapperOpts = generateZshTaskOpts(wrapperOptions)
        val zshTestOpts = generateZshTaskOpts(testOptions) + " \\\n                '(-)*:: :->task-or-option'"
        val zshInitOpts = generateZshTaskOpts(initOptions)
        val zshDependenciesOpts = generateZshTaskOpts(dependenciesOptions)
        val zshDependencyInsightOpts = generateZshTaskOpts(dependencyInsightOptions)
        val zshTasksOpts = generateZshTaskOpts(tasksOptions)
        val zshHelpOpts = generateZshTaskOpts(helpOptions)

        val bashCompletionScript = bashTemplate.asFile.get().readText()
            .replace("{{GENERATED_BASH_LONG_OPTIONS}}", bashLongOpts.trimEnd())
            .replace("{{GENERATED_BASH_SHORT_OPTIONS}}", bashShortOpts.trimEnd())
            .replace("{{GENERATED_GRADLE_PROPERTIES}}", properties.trimEnd())

        val zshCompletionScript = zshTemplate.asFile.get().readText()
            .replace("{{GENERATED_ZSH_MAIN_OPTIONS}}", zshMainOpts)
            .replace("{{GENERATED_ZSH_SUBCOMMAND_OPTIONS}}", zshSubcommandOpts)
            .replace("{{GENERATED_ZSH_WRAPPER_OPTIONS}}", zshWrapperOpts)
            .replace("{{GENERATED_ZSH_TEST_OPTIONS}}", zshTestOpts)
            .replace("{{GENERATED_ZSH_INIT_OPTIONS}}", zshInitOpts)
            .replace("{{GENERATED_ZSH_DEPENDENCIES_OPTIONS}}", zshDependenciesOpts)
            .replace("{{GENERATED_ZSH_DEPENDENCY_INSIGHT_OPTIONS}}", zshDependencyInsightOpts)
            .replace("{{GENERATED_ZSH_TASKS_OPTIONS}}", zshTasksOpts)
            .replace("{{GENERATED_ZSH_HELP_OPTIONS}}", zshHelpOpts)

        bashOutputFile.asFile.get().writeText(bashCompletionScript)
        zshOutputFile.asFile.get().writeText(zshCompletionScript)

        logger.lifecycle("Generated completion scripts:")
        logger.lifecycle("  - ${bashOutputFile.asFile.get().name}")
        logger.lifecycle("  - ${zshOutputFile.asFile.get().name}")
        logger.lifecycle("\nGeneration complete!")
    }

    private fun collectAllBuildOptions() =
        listOf(
            BuildLayoutParametersBuildOptions(),
            StartParameterBuildOptions(),
            LoggingConfigurationBuildOptions(),
            WelcomeMessageBuildOptions(),
            DaemonBuildOptions(),
            ParallelismBuildOptions(),
            ToolchainBuildOptions.forToolChainConfiguration()
        ).flatMap { it.allOptions }

    private fun extractCliOptions(allOptions: List<BuildOption<*>>): MutableList<CliOption> {
        return allOptions.map { option ->
            option to getCliConfigsFrom(option)
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
                            argumentExpected = isArgumentExpected(option, isDirectory, filePattern),
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

    private fun isArgumentExpected(
        option: BuildOption<*>,
        isDirectory: Boolean,
        filePattern: String?
    ): Boolean = option is StringBuildOption ||
            option is IntegerBuildOption ||
            option is EnumBuildOption<*, *> ||
            isDirectory ||
            filePattern != null

    private fun extractPropertyOptions(allOptions: List<BuildOption<*>>): List<CliOption> {
        return allOptions
            .filter { it.property != null && !it.property?.contains(".internal.")!! }
            .mapNotNull { option ->
                val cliConfigs = getCliConfigsFrom(option)

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

    private fun getCliConfigsFrom(option: BuildOption<*>) =
        (getDeclaredField(option, "commandLineOptionConfigurations")
            .get(option) as List<*>)
            .map { it as CommandLineOptionConfiguration }

    private fun createHelpOption() =
        CliOption(
            twoDashOption = "help",
            oneDashOption = "h",
            description = "Shows a help message."
        )

    private fun createVersionOption() =
        CliOption(
            twoDashOption = "version",
            oneDashOption = "v",
            description = "Print version info and exit."
        )

    private fun createShowVersionOption() =
        CliOption(
            twoDashOption = "show-version",
            oneDashOption = "V",
            description = "Print version info and continue."
        )

    private fun createRerunOption() =
        CliOption(
            twoDashOption = "rerun",
            description = "Causes the task to be re-run even if up-to-date."
        )

    private fun generateBashLongOpts(options: List<CliOption>) =
        options.filter { it.twoDashOption != null }
            .sortedBy { it.twoDashOption }
            .joinToString("\n") {
                it.getSuggestionLine("--${it.twoDashOption}")
            }


    private fun getBashShortOpts(options: List<CliOption>) =
        options.filter { it.oneDashOption != null }
            .sortedBy { it.oneDashOption?.lowercase(Locale.getDefault()) }
            .joinToString("\n") {
                it.getSuggestionLine("-${it.oneDashOption}")
            }

    private fun generateZshMainOpts(options: List<CliOption>) =
        options.sortedBy { it.twoDashOption ?: it.oneDashOption }
            .joinToString(" \\\n        ") { option ->
                option.getOptionLine(includeArgumentExpected = true)
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

    private fun generatePropertiesOpts(allPropertyNames: List<CliOption>) =
        allPropertyNames
            .filter { it.oneDashOption != null }
            .joinToString("\n") {
                val paddingOption = "-${it.oneDashOption!!.padEnd(40)}"
                "$paddingOption - ${it.description?.lineSequence()?.first() ?: ""}"
            }

    private fun extractWrapperOptions(): List<TaskOptionDescriptor> {
        return TaskOptionExtractor.extractTaskOptions("org.gradle.api.tasks.wrapper.Wrapper")
    }

    private fun extractTestOptions() =
        TaskOptionExtractor.extractTaskOptions(
            "org.gradle.api.tasks.testing.Test",
            "org.gradle.api.tasks.testing.AbstractTestTask"
        )

    private fun extractInitOptions() =
        TaskOptionExtractor.extractTaskOptions("org.gradle.buildinit.tasks.InitBuild")

    private fun extractDependenciesOptions(): List<TaskOptionDescriptor> {
        val options = TaskOptionExtractor.extractTaskOptions(
            "org.gradle.api.tasks.diagnostics.DependencyReportTask",
            "org.gradle.api.tasks.diagnostics.AbstractDependencyReportTask"
        )

        return addCustomCompletionFunctionForConfiguration(options)
    }

    private fun addCustomCompletionFunctionForConfiguration(options: List<TaskOptionDescriptor>) =
        options.map { option ->
            when (option.optionName) {
                "configuration" -> option.copy(completionFunction = ":dependency configuration:_gradle_dependency_configurations")
                else -> option
            }
        }

    /**
     * Extracts dependencyInsight task options from DependencyInsightReportTask via reflection.
     */
    private fun extractDependencyInsightOptions(): List<TaskOptionDescriptor> {
        val options =
            TaskOptionExtractor.extractTaskOptions("org.gradle.api.tasks.diagnostics.DependencyInsightReportTask")

        return addCustomCompletionFunctionForConfiguration(options)
    }

    private fun extractTasksOptions() =
        TaskOptionExtractor.extractTaskOptions("org.gradle.api.tasks.diagnostics.TaskReportTask")

    private fun extractHelpOptions() =
        TaskOptionExtractor.extractTaskOptions("org.gradle.configuration.Help")

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

        fun getDeclaredField(obj: Any, fieldName: String): Field {
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
            throw RuntimeException("Could not find field $fieldName on object $obj")
        }

        fun getPossibleValues(option: BuildOption<*>, longOptionName: String?) =
            longOptionName?.let { HARDCODED_POSSIBLE_VALUES[it] } ?: getPossibleValuesList(option)

        fun isDirectoryOption(longOptionName: String?) =
            longOptionName?.let { it in DIRECTORY_OPTIONS } ?: false

        fun getFilePattern(longOptionName: String?) =
            longOptionName?.let { FILE_OPTIONS[it] }

        fun getPossibleValuesList(option: BuildOption<*>) =
            when (option) {
                is EnumBuildOption<*, *> -> getDeclaredField(option, "possibleValues")
                    .get(option)
                    ?.uncheckedCast<List<Any>>()
                    ?.map { it.toString().lowercase() }
                    ?: emptyList()

                else -> emptyList()
            }

        fun getPossibleValuesForProperty(option: BuildOption<*>, propertyName: String) =
            HARDCODED_POSSIBLE_VALUES[propertyName] ?: getPossibleValuesList(option)

        fun isDirectoryProperty(propertyName: String) =
            propertyName in DIRECTORY_OPTIONS

        fun getFilePatternForProperty(propertyName: String) =
            FILE_OPTIONS[propertyName]
    }
}
