import org.gradle.api.logging.Logger
import org.gradle.api.tasks.options.Option
import org.gradle.api.tasks.options.OptionValues
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
     * @param taskName Name of the task for logging purposes
     * @param logger Logger for warnings and errors
     * @return List of extracted options
     */
    fun extractTaskOptions(
        classNames: List<String>,
        taskName: String,
        logger: Logger
    ): List<TaskOptionDescriptor> {
        return try {
            val classes = classNames.mapNotNull { className ->
                try {
                    Class.forName(className)
                } catch (e: ClassNotFoundException) {
                    logger.warn("Could not find class: $className")
                    null
                }
            }
            
            if (classes.isEmpty()) {
                logger.warn("Could not find any classes for $taskName task, using empty options list")
                return emptyList()
            }
            
            // Combine methods from all classes
            val allMethods = classes.flatMap { it.declaredMethods.toList() }
            
            allMethods.mapNotNull { method ->
                val optionAnnotation = method.getAnnotation(Option::class.java)
                if (optionAnnotation != null) {
                    val optionName = optionAnnotation.option
                    val description = optionAnnotation.description
                    
                    // Extract possible values from enum types, @OptionValues methods, or method parameters
                    val possibleValues = extractPossibleValues(method, allMethods)
                    
                    // Determine if this option requires an argument by checking the parameter type
                    val requiresArgument = !isBooleanOption(method)
                    
                    TaskOptionDescriptor(
                        option = optionName,
                        description = description,
                        possibleValues = possibleValues,
                        requiresArgument = requiresArgument
                    )
                } else {
                    null
                }
            }.distinctBy { it.option }.sortedBy { it.option }
        } catch (e: Exception) {
            logger.warn("Error extracting $taskName options: ${e.message}")
            emptyList()
        }
    }

    /**
     * Extracts possible values for a task option.
     * First checks for @OptionValues annotation on a corresponding getter method,
     * then checks if the parameter is an enum type.
     */
    private fun extractPossibleValues(method: Method, allMethods: List<Method>): List<String> {
        val optionAnnotation = method.getAnnotation(Option::class.java)
        val optionName = optionAnnotation?.option ?: return emptyList()
        
        // Look for a method with @OptionValues annotation for this option
        val optionValuesMethod = allMethods.find { m ->
            val optionValues = m.getAnnotation(OptionValues::class.java)
            // The value property is an array of strings, get the first one
            optionValues != null && optionValues.value.isNotEmpty() && optionValues.value[0] == optionName
        }

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

    /**
     * Determines if an option is a boolean flag (doesn't require an argument).
     * Boolean options are detected by checking if:
     * 1. The method parameter is a boolean type (for setter-style options), OR
     * 2. The method return type is Property<Boolean> (for property-style options)
     */
    private fun isBooleanOption(method: Method): Boolean {
        // Check for setter-style: method with boolean parameter
        val paramTypes = method.parameterTypes
        if (paramTypes.isNotEmpty()) {
            val firstParam = paramTypes[0]
            if (firstParam == Boolean::class.javaPrimitiveType || 
                firstParam == Boolean::class.javaObjectType) {
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
     * @param additionalLine Optional additional line to append (e.g., '(-)*:: :->task-or-option')
     * @return Formatted Zsh completion string
     */
    fun generateZshTaskOpts(
        options: List<TaskOptionDescriptor>,
        additionalLine: String? = null
    ): String {
        val optionsString = options.joinToString(" \\\n                ") { option ->
            val optionName = "--${option.option}"
            val escapedDescription = option.description.replace("[", "\\[").replace("]", "\\]")
            
            // Determine if we need the '=' suffix based on whether the option requires an argument
            val equalsSuffix = if (option.requiresArgument) "=" else ""
            
            // Use custom completion function if provided, otherwise use possible values or simple completion
            when {
                option.completionFunction != null -> {
                    "'$optionName$equalsSuffix[$escapedDescription]${option.completionFunction}'"
                }
                option.possibleValues.isNotEmpty() -> {
                    val values = option.possibleValues.joinToString(" ")
                    "'$optionName$equalsSuffix[$escapedDescription]:*:distribution type:($values)'"
                }
                option.requiresArgument -> {
                    "'$optionName$equalsSuffix[$escapedDescription]'"
                }
                else -> {
                    // Boolean flag - no equals sign and no argument completion
                    "'$optionName[$escapedDescription]'"
                }
            }
        }
        
        return if (additionalLine != null) {
            "$optionsString \\\n                $additionalLine"
        } else {
            optionsString
        }
    }
}
