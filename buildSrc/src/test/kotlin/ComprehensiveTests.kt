import io.mockk.*
import org.gradle.api.logging.Logger
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested

/**
 * Comprehensive test suite for utility classes.
 */
class ComprehensiveTests {

    private lateinit var mockLogger: Logger

    @BeforeEach
    fun setup() {
        mockLogger = mockk(relaxed = true)
    }

    @Nested
    @DisplayName("TaskOptionDescriptor Tests")
    inner class TaskOptionDescriptorTests {

        @Test
        fun `creates option with all fields`() {
            val option = TaskOptionDescriptor(
                option = "test",
                description = "Test",
                possibleValues = listOf("a", "b"),
                completionFunction = ":func"
            )

            assertEquals("test", option.option)
            assertEquals(listOf("a", "b"), option.possibleValues)
            assertEquals(":func", option.completionFunction)
        }

        @Test
        fun `supports copy modification`() {
            val original = TaskOptionDescriptor("test", "Original")
            val modified = original.copy(completionFunction = ":new")

            assertEquals(":new", modified.completionFunction)
            assertNull(original.completionFunction)
        }
    }

    @Nested
    @DisplayName("CliOption Tests")
    inner class CliOptionTests {

        @Test
        fun `formats options correctly`() {
            assertEquals("-h", CliOption(oneDashOption = "h", description = "H").formatOptionStr())
            assertEquals("--help", CliOption(twoDashOption = "help", description = "Help").formatOptionStr())
            assertEquals("{-h,--help}", CliOption(twoDashOption = "help", oneDashOption = "h", description = "Help").formatOptionStr())
        }

        @Test
        fun `escapes brackets`() {
            val option = CliOption(twoDashOption = "test", description = "Test [with] brackets")
            assertEquals("Test (with) brackets", option.getZshCompatibleDescription())
        }
    }

    @Nested
    @DisplayName("TaskOptionExtractor Tests")
    inner class ExtractorTests {

        @Test
        fun `extracts wrapper options`() {
            val options = TaskOptionExtractor.extractTaskOptions(
                listOf("org.gradle.api.tasks.wrapper.Wrapper"),
                "wrapper",
                mockLogger
            )

            assertTrue(options.any { it.option == "gradle-version" })
            assertTrue(options.any { it.option == "distribution-type" })
            val distType = options.find { it.option == "distribution-type" }
            assertEquals(listOf("bin", "all"), distType?.possibleValues)
        }

        @Test
        fun `extracts from multiple classes and deduplicates`() {
            val options = TaskOptionExtractor.extractTaskOptions(
                listOf(
                    "org.gradle.api.tasks.testing.Test",
                    "org.gradle.api.tasks.testing.AbstractTestTask"
                ),
                "test",
                mockLogger
            )

            val names = options.map { it.option }
            assertEquals(names.size, names.distinct().size)
            assertTrue(options.any { it.option == "debug-jvm" })
            assertTrue(options.any { it.option == "tests" })
        }

        @Test
        fun `handles non-existent classes`() {
            val options = TaskOptionExtractor.extractTaskOptions(
                listOf("com.fake.Task"),
                "fake",
                mockLogger
            )

            assertTrue(options.isEmpty())
        }

        @Test
        fun `sorts options alphabetically`() {
            val options = TaskOptionExtractor.extractTaskOptions(
                listOf("org.gradle.api.tasks.wrapper.Wrapper"),
                "wrapper",
                mockLogger
            )

            val names = options.map { it.option }
            assertEquals(names.sorted(), names)
        }
    }

    @Nested
    @DisplayName("Formatting Tests")
    inner class FormattingTests {

        @Test
        fun `generates simple option`() {
            val result = TaskOptionExtractor.generateZshTaskOpts(
                listOf(TaskOptionDescriptor("test", "Test"))
            )
            assertEquals("'--test=[Test]'", result)
        }

        @Test
        fun `generates option with values`() {
            val result = TaskOptionExtractor.generateZshTaskOpts(
                listOf(TaskOptionDescriptor("type", "Type", possibleValues = listOf("a", "b")))
            )
            assertTrue(result.contains("(a b)"))
        }

        @Test
        fun `generates option with completion function`() {
            val result = TaskOptionExtractor.generateZshTaskOpts(
                listOf(TaskOptionDescriptor("cfg", "Config", completionFunction = ":dep:_func"))
            )
            assertTrue(result.contains(":dep:_func"))
        }

        @Test
        fun `prefers completion function over values`() {
            val result = TaskOptionExtractor.generateZshTaskOpts(
                listOf(TaskOptionDescriptor("test", "Test", listOf("a"), ":func"))
            )
            assertTrue(result.contains(":func"))
            assertFalse(result.contains("(a)"))
        }

        @Test
        fun `handles multiple options`() {
            val result = TaskOptionExtractor.generateZshTaskOpts(
                listOf(
                    TaskOptionDescriptor("opt1", "First"),
                    TaskOptionDescriptor("opt2", "Second")
                )
            )
            assertTrue(result.contains("--opt1="))
            assertTrue(result.contains("--opt2="))
            assertTrue(result.contains(" \\\n                "))
        }

        @Test
        fun `appends additional line`() {
            val result = TaskOptionExtractor.generateZshTaskOpts(
                listOf(TaskOptionDescriptor("test", "Test")),
                "'(-)*:: :->task-or-option'"
            )
            assertTrue(result.endsWith("'(-)*:: :->task-or-option'"))
        }
    }

    @Nested
    @DisplayName("Integration Tests")
    inner class IntegrationTests {

        @Test
        fun `all supported tasks extract options`() {
            val tasks = mapOf(
                "wrapper" to "org.gradle.api.tasks.wrapper.Wrapper",
                "test" to "org.gradle.api.tasks.testing.Test",
                "init" to "org.gradle.buildinit.tasks.InitBuild",
                "help" to "org.gradle.configuration.Help"
            )

            tasks.forEach { (name, className) ->
                val options = TaskOptionExtractor.extractTaskOptions(
                    listOf(className),
                    name,
                    mockLogger
                )
                assertTrue(options.isNotEmpty(), "$name should have options")
            }
        }

        @Test
        fun `end to end wrapper generation`() {
            val options = TaskOptionExtractor.extractTaskOptions(
                listOf("org.gradle.api.tasks.wrapper.Wrapper"),
                "wrapper",
                mockLogger
            )
            val formatted = TaskOptionExtractor.generateZshTaskOpts(options)

            assertTrue(formatted.contains("--gradle-version="))
            assertTrue(formatted.contains("(bin all)"))
        }
    }
}
