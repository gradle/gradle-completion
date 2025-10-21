/**
 * Represents a command-line option for the Gradle wrapper task.
 *
 * @property option The option name (without the -- prefix)
 * @property description The description of the option
 * @property possibleValues List of possible values for this option (e.g., for enum types)
 */
data class WrapperOption(
    val option: String,
    val description: String,
    val possibleValues: List<String> = emptyList()
)
