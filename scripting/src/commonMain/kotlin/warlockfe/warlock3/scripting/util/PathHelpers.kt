package warlockfe.warlock3.scripting.util

import kotlinx.io.buffered
import kotlinx.io.files.FileSystem
import kotlinx.io.files.Path
import kotlinx.io.readByteArray
import org.antlr.v4.kotlinruntime.CharStream
import org.antlr.v4.kotlinruntime.CharStreams

val Path.extension: String
    get() = name.substringAfterLast('.')

val Path.nameWithoutExtension: String
    get() = name.substringBeforeLast('.')

internal fun Path.toCharStream(fileSystem: FileSystem): CharStream =
    fileSystem
        .source(this)
        .buffered()
        .use {
            val text = it.readByteArray().decodeToString()
            CharStreams.fromString(text)
        }

/**
 * The file a script means by the sound [name]: the one beside the script, in [scriptDir], when
 * there is one; otherwise [name] as given, for the sound player to look for. [scriptDir] is null
 * for a script run from a string.
 */
internal fun scriptSoundFile(
    fileSystem: FileSystem,
    scriptDir: Path?,
    name: String,
): String {
    val beside = scriptDir?.let { Path(it, name) }
    return if (beside != null && fileSystem.exists(beside)) beside.toString() else name
}
