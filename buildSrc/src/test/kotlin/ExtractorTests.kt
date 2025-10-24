import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ExtractorTests {
    @Test
    fun `extracts wrapper options`() {
        val options = TaskOptionExtractor.extractTaskOptions("org.gradle.api.tasks.wrapper.Wrapper")

        Assertions.assertTrue(options.any { it.optionName == "gradle-version" })
        Assertions.assertTrue(options.any { it.optionName == "distribution-type" })
        val distType = options.find { it.optionName == "distribution-type" }
        Assertions.assertEquals(listOf("bin", "all"), distType?.possibleValues)
    }

    @Test
    fun `extracts from multiple classes and deduplicates`() {
        val options = TaskOptionExtractor.extractTaskOptions(
            "org.gradle.api.tasks.testing.Test",
            "org.gradle.api.tasks.testing.AbstractTestTask"
        )

        val names = options.map { it.optionName }
        Assertions.assertEquals(options.size, names.distinct().size)
        Assertions.assertTrue(options.any { it.optionName == "debug-jvm" })
        Assertions.assertTrue(options.any { it.optionName == "tests" })
    }

    @Test
    fun `throws on non-existent classes`() {
        assertThrows<ClassNotFoundException> {
            TaskOptionExtractor.extractTaskOptions("com.fake.Task")
        }
    }

    @Test
    fun `sorts options alphabetically`() {
        val options = TaskOptionExtractor.extractTaskOptions("org.gradle.api.tasks.wrapper.Wrapper")

        val names = options.map { it.optionName }
        Assertions.assertEquals(names.sorted(), names)
    }
}