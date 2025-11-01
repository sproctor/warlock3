package warlockfe.warlock3.scripting.util

import kotlinx.io.buffered
import kotlinx.io.files.FileSystem
import kotlinx.io.files.Path
import kotlinx.io.readByteArray
import org.antlr.v4.kotlinruntime.CharStream
import org.antlr.v4.kotlinruntime.CharStreams

internal actual fun Path.toCharStream(fileSystem: FileSystem): CharStream {
    val text = fileSystem.source(this).buffered().readByteArray().decodeToString()
    return CharStreams.fromString(text)
}