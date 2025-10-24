import org.gradle.api.tasks.options.Option
import java.lang.reflect.Method

/**
 * Utility class for extracting task options from Gradle task classes via reflection.
 */
object TaskOptionExtractor {

    /**
     * Extracts task options from one or more Gradle task classes via reflection.
     * Looks for methods annotated with @Option and extracts option names, descriptions, and possible values.
     *
     * @param classNames List of fully qualified class names to extract options from (e.g., task class and its parents)
     * @return List of extracted options
     */
    fun extractTaskOptions(vararg classNames: String): List<TaskOptionDescriptor> {
        if (classNames.isEmpty()) {
            throw RuntimeException("no expected class names provided")
        }

        val classes = classNames.map { Class.forName(it) }

        val allMethods = classes.flatMap { it.declaredMethods.toList() }

        return allMethods.mapNotNull { method ->
            val optionAnnotation = method.getAnnotation(Option::class.java)
            if (optionAnnotation != null) {
                TaskOptionDescriptor(
                    optionName = optionAnnotation.option,
                    description = optionAnnotation.description,
                    possibleValues = extractPossibleValuesFromEnumParameters(method),
                    requiresArgument = !isBooleanOption(method)
                )
            } else {
                null
            }
        }.distinctBy { it.optionName }.sortedBy { it.optionName }
    }

    private fun extractPossibleValuesFromEnumParameters(method: Method): List<String> {
        // Check if the parameter is an enum
        val paramTypes = method.parameterTypes
        if (paramTypes.isNotEmpty()) {
            val paramType = paramTypes[0]
            if (paramType.isEnum) {
                @Suppress("UNCHECKED_CAST")
                return (paramType.enumConstants as Array<Enum<*>>).map { it.name.lowercase() }
            }
        }

        return emptyList()
    }

    private fun isBooleanOption(method: Method): Boolean {
        // Check for setter-style: method with boolean parameter
        val paramTypes = method.parameterTypes
        if (paramTypes.isNotEmpty()) {
            val firstParam = paramTypes[0]
            if (firstParam == Boolean::class.javaPrimitiveType ||
                firstParam == Boolean::class.javaObjectType
            ) {
                return true
            }
        }

        // Check for getter-style: method that returns Property<Boolean>
        val returnType = method.returnType
        if (returnType.name == "org.gradle.api.provider.Property") {
            // Check if it's Property<Boolean> by examining the generic type
            val genericReturnType = method.genericReturnType
            if (genericReturnType != null && genericReturnType.toString().contains("Boolean")) {
                return true
            }
        }

        return false
    }

    /**
     * Generates Zsh completion options for a task.
     *
     * @param options List of task options to format
     * @return Formatted Zsh completion string
     */
    fun generateZshTaskOpts(options: List<TaskOptionDescriptor>) =
        options.joinToString(" \\\n                ") {
            it.getTaskOptionCompletionLine()
        }

}


