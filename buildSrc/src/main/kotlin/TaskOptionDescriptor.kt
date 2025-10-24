/**
 * Represents a command-line option for a Gradle task.
 *
 * @property optionName The option name (without the -- prefix)
 * @property description The description of the option
 * @property possibleValues List of possible values for this option (e.g., for enum types)
 * @property completionFunction Optional Zsh completion function (e.g., ":dependency configuration:_gradle_dependency_configurations")
 * @property requiresArgument Whether this option requires a value (true) or is a boolean flag (false)
 */
data class TaskOptionDescriptor(
    val optionName: String,
    val description: String,
    val possibleValues: List<String> = emptyList(),
    val completionFunction: String? = null,
    val requiresArgument: Boolean = true
) {
    fun getTaskOptionCompletionLine(): CharSequence {
        val optionName = "--${optionName}"
        val escapedDescription = description.replace("[", "\\[").replace("]", "\\]")

        val equalsSuffix = if (requiresArgument) "=" else ""

        return "'$optionName$equalsSuffix[$escapedDescription]${getPostfix()}'"
    }

    private fun getPostfix() = when {
        completionFunction != null -> {
            completionFunction
        }
        possibleValues.isNotEmpty() -> {
            val values = possibleValues.joinToString(" ")
            ":*:distribution type:($values)"
        }
        else -> {
            ""
        }
    }
}
