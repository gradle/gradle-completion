import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class CliOptionTests {

    @Test
    fun `formats options correctly`() {
        Assertions.assertEquals("-h", CliOption(oneDashOption = "h", description = "H").formatOptionStr())
        Assertions.assertEquals("--help", CliOption(twoDashOption = "help", description = "Help").formatOptionStr())
        Assertions.assertEquals(
            "{-h,--help}",
            CliOption(twoDashOption = "help", oneDashOption = "h", description = "Help").formatOptionStr()
        )
    }

    @Test
    fun `escapes brackets`() {
        val option = CliOption(twoDashOption = "test", description = "Test [with] brackets")
        Assertions.assertEquals("Test (with) brackets", option.getZshCompatibleDescription())
    }
}
