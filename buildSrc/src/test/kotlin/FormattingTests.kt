import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class FormattingTests {

    @Test
    fun `generates simple option`() {
        val result = TaskOptionExtractor.generateZshTaskOpts(
            listOf(TaskOptionDescriptor("test", "Test", emptyList(), null, true))
        )
        Assertions.assertEquals("'--test=[Test]'", result)
    }

    @Test
    fun `generates option with values`() {
        val result = TaskOptionExtractor.generateZshTaskOpts(
            listOf(TaskOptionDescriptor("type", "Type", possibleValues = listOf("a", "b"), null, true))
        )
        Assertions.assertTrue(result.contains("(a b)"))
    }

    @Test
    fun `generates option with completion function`() {
        val result = TaskOptionExtractor.generateZshTaskOpts(
            listOf(TaskOptionDescriptor("cfg", "Config", emptyList(), completionFunction = ":dep:_func", true))
        )
        Assertions.assertTrue(result.contains(":dep:_func"))
    }

    @Test
    fun `prefers completion function over values`() {
        val result = TaskOptionExtractor.generateZshTaskOpts(
            listOf(TaskOptionDescriptor("test", "Test", listOf("a"), ":func", true))
        )
        Assertions.assertTrue(result.contains(":func"))
        Assertions.assertFalse(result.contains("(a)"))
    }

    @Test
    fun `handles multiple options`() {
        val result = TaskOptionExtractor.generateZshTaskOpts(
            listOf(
                TaskOptionDescriptor("opt1", "First", emptyList(), null, true),
                TaskOptionDescriptor("opt2", "Second", emptyList(), null, true)
            )
        )
        Assertions.assertTrue(result.contains("--opt1="))
        Assertions.assertTrue(result.contains("--opt2="))
        Assertions.assertTrue(result.contains(" \\\n                "))
    }
}
