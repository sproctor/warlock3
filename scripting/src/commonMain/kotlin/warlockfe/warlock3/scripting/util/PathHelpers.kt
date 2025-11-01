package warlockfe.warlock3.scripting.util

import kotlinx.io.files.Path

val Path.extension: String
    get() = name.substringAfterLast('.')

val Path.nameWithoutExtension: String
    get() = name.substringBeforeLast('.')