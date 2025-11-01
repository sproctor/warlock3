package warlockfe.warlock3.scripting.util

import kotlinx.io.files.FileSystem
import kotlinx.io.files.Path
import org.antlr.v4.kotlinruntime.CharStream

val Path.extension: String
    get() = name.substringAfterLast('.')

val Path.nameWithoutExtension: String
    get() = name.substringBeforeLast('.')

internal expect fun Path.toCharStream(fileSystem: FileSystem): CharStream