package warlockfe.warlock3.scripting.util

import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.files.SystemTemporaryDirectory
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

class ScriptSoundFileTest {
    private val root = Path(SystemTemporaryDirectory, "script-sound-${Random.nextLong().toULong()}")
    private val scriptDir = Path(root, "scripts")

    private fun touch(path: Path) {
        path.parent?.let { SystemFileSystem.createDirectories(it) }
        SystemFileSystem.sink(path).close()
    }

    @Test
    fun aRelativeNameIsFoundBesideTheScript() {
        val beside = Path(scriptDir, "ping.wav")
        touch(beside)
        assertEquals(beside.toString(), scriptSoundFile(SystemFileSystem, scriptDir, "ping.wav"))
    }

    @Test
    fun aRelativeNameNotBesideTheScriptIsPassedOn() {
        SystemFileSystem.createDirectories(scriptDir)
        assertEquals("ping.wav", scriptSoundFile(SystemFileSystem, scriptDir, "ping.wav"))
    }

    @Test
    fun anAbsoluteNameIsNeverJoinedOntoTheScriptDirectory() {
        val absolute = Path(root, "sounds", "ping.wav")
        touch(absolute)
        // The same path again, under the script's directory: what joining would wrongly find.
        touch(Path(scriptDir, absolute.toString()))
        assertEquals(absolute.toString(), scriptSoundFile(SystemFileSystem, scriptDir, absolute.toString()))
    }
}
