import kotlinx.io.files.Path
import warlockfe.warlock3.scripting.util.extension
import warlockfe.warlock3.scripting.util.nameWithoutExtension
import kotlin.test.Test
import kotlin.test.assertEquals

class FileNameTests {
    @Test
    fun normal_extension() {
        assertEquals("wsl", Path("test.wsl").extension)
    }

    @Test
    fun normal_name_without_extension() {
        assertEquals("test", Path("test.wsl").nameWithoutExtension)
    }

    @Test
    fun double_dot_extension() {
        assertEquals("wsl", Path("test.test.wsl").extension)
    }

    @Test
    fun double_dot_name_without_extension() {
        assertEquals("test.test", Path("test.test.wsl").nameWithoutExtension)
    }
}