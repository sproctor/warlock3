package warlockfe.warlock3.core.util

class AndroidSoundPlayer : SoundPlayer {
    override suspend fun playSound(filename: String): String? = "Playing sound not supported on Android."
}
