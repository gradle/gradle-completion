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
    ): List<WrapperOption> {
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
                    
                    WrapperOption(
                        option = optionName,
                        description = description,
                        possibleValues = possibleValues
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
        
//        if (optionValuesMethod != null) {
//            // Try to invoke the method to get the list of values
//            // Note: This won't work for non-static methods that require instance state
//            // For now, we'll just note that values exist but can't be extracted
//            return emptyList() // We can't safely invoke instance methods without an instance
//        }
        
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
     * Generates Zsh completion options for a task.
     * 
     * @param options List of task options to format
     * @param additionalLine Optional additional line to append (e.g., '(-)*:: :->task-or-option')
     * @return Formatted Zsh completion string
     */
    fun generateZshTaskOpts(
        options: List<WrapperOption>,
        additionalLine: String? = null
    ): String {
        val optionsString = options.joinToString(" \\\n                ") { option ->
            val optionName = "--${option.option}"
            val escapedDescription = option.description.replace("[", "\\[").replace("]", "\\]")
            
            if (option.possibleValues.isNotEmpty()) {
                val values = option.possibleValues.joinToString(" ")
                "'$optionName=[$escapedDescription]:*:distribution type:($values)'"
            } else {
                "'$optionName=[$escapedDescription]'"
            }
        }
        
        return if (additionalLine != null) {
            "$optionsString \\\n                $additionalLine"
        } else {
            optionsString
        }
    }
}
