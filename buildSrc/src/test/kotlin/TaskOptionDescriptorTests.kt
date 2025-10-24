import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class TaskOptionDescriptorTests {

    @Test
    fun `creates option with all fields`() {
        val option = TaskOptionDescriptor(
            optionName = "test",
            description = "Test",
            possibleValues = listOf("a", "b"),
            completionFunction = ":func"
        )

        Assertions.assertEquals("test", option.optionName)
        Assertions.assertEquals(listOf("a", "b"), option.possibleValues)
        Assertions.assertEquals(":func", option.completionFunction)
    }

    @Test
    fun `supports copy modification`() {
        val original = TaskOptionDescriptor("test", "Original")
        val modified = original.copy(completionFunction = ":new")

        Assertions.assertEquals(":new", modified.completionFunction)
        Assertions.assertNull(original.completionFunction)
    }
}