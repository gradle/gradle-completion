import org.gradle.internal.buildoption.CommandLineOptionConfiguration

/**
 * Data class to hold structured CLI option data for shell completion generation.
 */
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
    /**
     * Adds another CliOption to the list of mutually exclusive options.
     */
    fun addMutexOption(other: CliOption) {
        other.twoDashOption?.let { mutuallyExclusiveWith += "--${it}" }
        other.oneDashOption?.let { mutuallyExclusiveWith += "-${it}" }
    }

    /**
     * Adds a CommandLineOptionConfiguration to the list of mutually exclusive options.
     */
    fun addMutexOption(other: CommandLineOptionConfiguration) {
        mutuallyExclusiveWith += "--${other.longOption}"
        other.shortOption?.let { mutuallyExclusiveWith += "-${it}" }
    }

    fun getMultipleOccurrencePrefix() = if (multipleOccurrencePossible) "\\*" else ""

    /**
     * Formats the option string for Zsh completion.
     * Returns something like "-h", "--help", or "{-h,--help}"
     */
    fun formatOptionStr(): String {
        val optBuilder = mutableListOf<String>()
        oneDashOption?.let { optBuilder.add("-$it") }
        twoDashOption?.let { optBuilder.add("--$it") }
        return if (optBuilder.size > 1) {
            // Multiple options: use braces {-a,--long}
            optBuilder.joinToString(",", prefix = "{", postfix = "}")
        } else {
            // Single option: no braces needed
            optBuilder.firstOrNull() ?: ""
        }
    }

    /**
     * Returns a human-readable name for this option (used in argument labels).
     */
    fun getOptionName() =
        (twoDashOption ?: oneDashOption?.removePrefix("D")?.removeSuffix("="))
            ?.replace("-", " ")

    /**
     * Generates the argument completion part for Zsh.
     * This handles file patterns, directories, and value lists.
     */
    fun generateArgumentPart(includeArgumentExpected: Boolean): String {
        if (!argumentExpected) return ""

        val argumentExpectedQualifier = if (includeArgumentExpected) ":->argument-expected" else ""

        if (filePattern != null) {
            val label = getOptionName() ?: "file"
            return ":$label:_files -g \\${filePattern}$argumentExpectedQualifier"
        }

        if (isDirectory) {
            val label = getOptionName() ?: "directory"
            return ":$label:_directories$argumentExpectedQualifier"
        }

        if (possibleValues.isEmpty()) {
            return argumentExpectedQualifier
        }

        val label = getOptionName() ?: "value"
        return ":$label:(${possibleValues.joinToString(" ")})$argumentExpectedQualifier"
    }

    /**
     * Generates a complete option line for Zsh completion.
     */
    fun getOptionLine(includeArgumentExpected: Boolean): String {
        val multiplePrefix = getMultipleOccurrencePrefix()
        val mutex = getMutexOptions()
        val incubatingText = if (incubating) " (incubating)" else ""
        val description = getZshCompatibleDescription()
        val optStr = formatOptionStr()
        val argumentPart = generateArgumentPart(includeArgumentExpected = includeArgumentExpected)

        val commonPostfix = "[${description}${incubatingText}]${argumentPart}"
        val commonPrefix = "${multiplePrefix}${mutex}"

        return when {
            optStr.contains("{") ->
                "${commonPrefix}${optStr}'$commonPostfix'"
            else ->
                "${commonPrefix}'${optStr}$commonPostfix'"
        }
    }

    /**
     * Returns the description with brackets replaced (for Zsh compatibility).
     */
    fun getZshCompatibleDescription() =
        description?.lineSequence()?.first()?.replace("[", "(")?.replace("]", ")") ?: ""

    /**
     * Returns the formatted string of mutually exclusive options.
     */
    fun getMutexOptions() = if (mutuallyExclusiveWith.isNotEmpty()) {
        "(${mutuallyExclusiveWith.joinToString(",")})"
    } else {
        ""
    }

    fun getSuggestionLine(optionString: String): String {
        val incubatingText = if (incubating) " [incubating]" else ""
        return "${optionString.padEnd(30)} - ${(description ?: "").lineSequence().firstOrNull() ?: ""} $incubatingText"
    }

}
