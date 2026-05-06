package warlockfe.warlock3.core.util

interface SoundPlayer {
    suspend fun playSound(filename: String): String?
}
