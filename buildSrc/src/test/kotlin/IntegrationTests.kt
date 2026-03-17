import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class IntegrationTests {
    @Test
    fun `all supported tasks extract options`() {
        val tasks = mapOf(
            "wrapper" to "org.gradle.api.tasks.wrapper.Wrapper",
            "test" to "org.gradle.api.tasks.testing.Test",
            "init" to "org.gradle.buildinit.tasks.InitBuild",
            "help" to "org.gradle.configuration.Help"
        )

        tasks.forEach { (name, className) ->
            val options = TaskOptionExtractor.extractTaskOptions(className)
            Assertions.assertTrue(options.isNotEmpty(), "$name should have options")
        }
    }

    @Test
    fun `end to end wrapper generation`() {
        val options = TaskOptionExtractor.extractTaskOptions("org.gradle.api.tasks.wrapper.Wrapper")
        val formatted = TaskOptionExtractor.generateZshTaskOpts(options)

        Assertions.assertTrue(formatted.contains("--gradle-version="))
        Assertions.assertTrue(formatted.contains("(bin all)"))
    }
}
