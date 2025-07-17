package warlockfe.warlock3.core.util

class AndroidSoundPlayer : SoundPlayer {
    override fun playSound(filename: String): String? {
        return "Playing sound not supported on Android."
    }
}
