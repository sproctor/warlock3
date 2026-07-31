package warlockfe.warlock3.core.util

import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.sound.sampled.AudioFileFormat
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioInputStream
import javax.sound.sampled.AudioSystem
import kotlin.io.path.createTempDirectory
import kotlin.math.PI
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DesktopSoundPlayerTest {
    private val dir = createTempDirectory("sound-test").toFile()

    private val dirs =
        WarlockDirs(
            homeDir = dir.absolutePath,
            dataDir = dir.absolutePath,
            configDir = dir.absolutePath,
            logDir = dir.absolutePath,
        )

    /** A short sine tone, so the test needs no audio fixture on disk. */
    private fun writeTone(
        name: String,
        channels: Int = 1,
        sampleRate: Float = 44100f,
    ): File {
        val frames = (sampleRate / 5).toInt() // 200ms
        val format = AudioFormat(sampleRate, 16, channels, true, false)
        val bytes = ByteBuffer.allocate(frames * channels * 2).order(ByteOrder.LITTLE_ENDIAN)
        repeat(frames) { frame ->
            val sample = (sin(2.0 * PI * 440.0 * frame / sampleRate) * Short.MAX_VALUE * 0.25).toInt().toShort()
            repeat(channels) { bytes.putShort(sample) }
        }
        val file = File(dir, name)
        AudioInputStream(bytes.array().inputStream(), format, frames.toLong()).use {
            AudioSystem.write(it, AudioFileFormat.Type.WAVE, file)
        }
        return file
    }

    /**
     * A machine with no sound card (CI, containers) can't open a device, and that is reported rather
     * than thrown. Anything else - a missing native, a bad buffer format, an OpenAL error - fails.
     */
    private fun assertPlayed(result: String?) {
        assertTrue(
            result == null || result == "No audio device available" || result.startsWith("Could not create an audio context"),
            "unexpected playback failure: $result",
        )
    }

    @Test
    fun playsAMonoWavFile() =
        runBlocking {
            val file = writeTone("mono.wav")
            assertPlayed(DesktopSoundPlayer(dirs).playSound(file.absolutePath))
        }

    @Test
    fun playsAStereoWavFile() =
        runBlocking {
            val file = writeTone("stereo.wav", channels = 2, sampleRate = 22050f)
            assertPlayed(DesktopSoundPlayer(dirs).playSound(file.absolutePath))
        }

    @Test
    fun findsSoundsRelativeToTheWarlockDirectories() =
        runBlocking {
            writeTone("relative.wav")
            assertPlayed(DesktopSoundPlayer(dirs).playSound("relative.wav"))
        }

    @Test
    fun reportsAMissingFile() =
        runBlocking {
            assertEquals("File not found", DesktopSoundPlayer(dirs).playSound("nope.wav"))
        }

    @Test
    fun reportsAFileThatIsNotAudio() =
        runBlocking {
            File(dir, "notaudio.wav").writeText("this is not a wav file")
            val result = DesktopSoundPlayer(dirs).playSound("notaudio.wav")
            assertTrue(result != null, "expected an error message for a non-audio file")
        }
}
