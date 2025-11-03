package warlockfe.warlock3.scripting.util

import kotlinx.io.files.FileSystem
import kotlinx.io.files.Path
import org.antlr.v4.kotlinruntime.CharStream
import org.antlr.v4.kotlinruntime.CharStreams

internal actual fun Path.toCharStream(fileSystem: FileSystem): CharStream {
    return CharStreams.fromFileName(toString())
}