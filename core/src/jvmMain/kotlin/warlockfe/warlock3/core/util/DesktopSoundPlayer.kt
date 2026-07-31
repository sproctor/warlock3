package warlockfe.warlock3.core.util

import co.touchlab.kermit.Logger
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import org.lwjgl.openal.AL
import org.lwjgl.openal.AL10
import org.lwjgl.openal.ALC
import org.lwjgl.openal.ALC10
import org.lwjgl.system.MemoryUtil
import java.io.File
import java.nio.ByteBuffer
import java.util.concurrent.Executors
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioInputStream
import javax.sound.sampled.AudioSystem

/**
 * Plays sounds through OpenAL (LWJGL). Java Sound is still used to *decode* a file into PCM, so the
 * formats we read are unchanged (WAV, AIFF, AU, plus whatever an SPI on the classpath adds), but the
 * output path is OpenAL: mixing is the driver's job there, so there is no output line to be
 * unavailable and no need to hunt through mixers for one that will accept the clip.
 *
 * OpenAL calls are not thread safe, so all of them run on [audioThread], a single thread that also
 * owns the device and context for the life of the process.
 */
class DesktopSoundPlayer(
    warlockDirs: WarlockDirs,
) : SoundPlayer {
    private val logger = Logger.withTag("DesktopSoundPlayer")

    private val dirs =
        listOf(
            warlockDirs.dataDir,
            warlockDirs.configDir,
            warlockDirs.homeDir,
        )

    private val audioThread =
        Executors
            .newSingleThreadExecutor { runnable ->
                Thread(runnable, "openal-audio").apply { isDaemon = true }
            }.asCoroutineDispatcher()

    private var device = 0L
    private var context = 0L
    private var initError: String? = null
    private var initialized = false

    // Sources still playing, with the buffer each one owns. Swept on every play so a long session
    // does not exhaust OpenAL's finite source pool.
    private val playing = mutableListOf<Pair<Int, Int>>()

    override suspend fun playSound(filename: String): String? =
        withContext(audioThread) {
            val file =
                File(filename).takeIf { it.exists() }
                    ?: dirs.map { File(it, filename) }.firstOrNull { it.exists() }
                    ?: return@withContext "File not found"

            openDevice()?.let { return@withContext it }

            releaseFinished()

            val sound =
                try {
                    decode(file)
                } catch (e: Exception) {
                    logger.d(e) { "Could not decode $filename" }
                    return@withContext e.message ?: "Could not read $filename"
                }

            try {
                play(sound)
                null
            } catch (e: Exception) {
                logger.d(e) { "Could not play $filename" }
                e.message ?: "Error playing sound: $filename"
            } finally {
                MemoryUtil.memFree(sound.pcm)
            }
        }

    /** Opens the device and context on first use. Returns an error message when audio is unavailable. */
    private fun openDevice(): String? {
        if (initialized) return initError
        initialized = true
        initError =
            try {
                device = ALC10.alcOpenDevice(null as CharSequence?)
                if (device == 0L) {
                    "No audio device available"
                } else {
                    val capabilities = ALC.createCapabilities(device)
                    context = ALC10.alcCreateContext(device, null as IntArray?)
                    if (context == 0L) {
                        "Could not create an audio context"
                    } else {
                        ALC10.alcMakeContextCurrent(context)
                        AL.createCapabilities(capabilities)
                        logger.d { "OpenAL ready: ${AL10.alGetString(AL10.AL_RENDERER)}" }
                        null
                    }
                }
            } catch (e: Throwable) {
                // Typically a missing or mismatched native library, which would otherwise take the
                // whole app down the first time something tried to play a sound.
                logger.e(e) { "Could not initialize OpenAL" }
                "Could not initialize audio: ${e.message}"
            }
        return initError
    }

    private class Sound(
        val pcm: ByteBuffer,
        val format: Int,
        val sampleRate: Int,
    )

    /**
     * Decodes [file] into the signed 16-bit PCM OpenAL takes, converting sample size, endianness and
     * channel count as needed. Anything with more than two channels is asked for as stereo, since
     * OpenAL's basic formats are mono and stereo only.
     */
    private fun decode(file: File): Sound =
        AudioSystem.getAudioInputStream(file).use { encoded ->
            val source = encoded.format
            val channels = if (source.channels in 1..2) source.channels else 2
            val sampleRate = if (source.sampleRate > 0) source.sampleRate else 44100f
            val target =
                AudioFormat(
                    AudioFormat.Encoding.PCM_SIGNED,
                    sampleRate,
                    16,
                    channels,
                    channels * 2,
                    sampleRate,
                    false, // little endian, which is what alBufferData expects
                )
            val decoded: AudioInputStream =
                when {
                    AudioSystem.isConversionSupported(target, source) -> AudioSystem.getAudioInputStream(target, encoded)
                    source.matches(target) -> encoded
                    else -> throw UnsupportedOperationException("Unsupported audio format: $source")
                }
            decoded.use { stream ->
                val bytes = stream.readBytes()
                if (bytes.isEmpty()) throw UnsupportedOperationException("No audio data in ${file.name}")
                val pcm = MemoryUtil.memAlloc(bytes.size)
                pcm.put(bytes)
                pcm.flip()
                Sound(
                    pcm = pcm,
                    format = if (channels == 1) AL10.AL_FORMAT_MONO16 else AL10.AL_FORMAT_STEREO16,
                    sampleRate = sampleRate.toInt(),
                )
            }
        }

    private fun play(sound: Sound) {
        AL10.alGetError()
        val buffer = AL10.alGenBuffers()
        AL10.alBufferData(buffer, sound.format, sound.pcm, sound.sampleRate)
        val source = AL10.alGenSources()
        AL10.alSourcei(source, AL10.AL_BUFFER, buffer)
        AL10.alSourcePlay(source)
        val error = AL10.alGetError()
        if (error != AL10.AL_NO_ERROR) {
            AL10.alDeleteSources(source)
            AL10.alDeleteBuffers(buffer)
            throw IllegalStateException("OpenAL error: ${AL10.alGetString(error) ?: error}")
        }
        playing += source to buffer
    }

    /** Deletes the sources that have finished, freeing them and the buffer each one holds. */
    private fun releaseFinished() {
        playing.removeAll { (source, buffer) ->
            val done = AL10.alGetSourcei(source, AL10.AL_SOURCE_STATE) != AL10.AL_PLAYING
            if (done) {
                AL10.alDeleteSources(source)
                AL10.alDeleteBuffers(buffer)
            }
            done
        }
    }
}
