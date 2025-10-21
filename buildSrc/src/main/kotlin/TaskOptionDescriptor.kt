/**
 * Represents a command-line option for the Gradle wrapper task.
 *
 * @property option The option name (without the -- prefix)
 * @property description The description of the option
 * @property possibleValues List of possible values for this option (e.g., for enum types)
 * @property completionFunction Optional Zsh completion function (e.g., ":dependency configuration:_gradle_dependency_configurations")
 */
data class TaskOptionDescriptor(
    val option: String,
    val description: String,
    val possibleValues: List<String> = emptyList(),
    val completionFunction: String? = null
)
