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
