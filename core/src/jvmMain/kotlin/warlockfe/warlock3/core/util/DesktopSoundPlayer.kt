package warlockfe.warlock3.core.util

import java.io.File
import javax.sound.sampled.AudioSystem

class DesktopSoundPlayer(
    private val warlockDirs: WarlockDirs
) : SoundPlayer {
    override fun playSound(filename: String): Boolean {
        val dirs = listOf(
            warlockDirs.dataDir,
            warlockDirs.configDir,
            warlockDirs.homeDir,
            "",
        )
        val file = dirs.map { File(it, filename) }.firstOrNull { it.exists() } ?: return false
        try {
            val clip = AudioSystem.getClip()
            val inputStream = AudioSystem.getAudioInputStream(file)
            clip.open(inputStream)
            clip.start()
            return true
        } catch (_: Exception) {
            return false
        }
    }
}