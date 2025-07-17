package warlockfe.warlock3.core.util

import java.io.File
import javax.sound.sampled.AudioSystem

class DesktopSoundPlayer(
    private val warlockDirs: WarlockDirs
) : SoundPlayer {
    override fun playSound(filename: String): String? {
        val dirs = listOf(
            warlockDirs.dataDir,
            warlockDirs.configDir,
            warlockDirs.homeDir,
            "",
        )
        val file = dirs.map { File(it, filename) }.firstOrNull { it.exists() } ?: return "File not found"
        try {
            val clip = AudioSystem.getClip()
            val inputStream = AudioSystem.getAudioInputStream(file)
            clip.open(inputStream)
            clip.start()
            return null
        } catch (e: Exception) {
            return e.message
        }
    }
}