package warlockfe.warlock3.wrayth.settings

import kotlinx.serialization.decodeFromString
import nl.adaptivity.xmlutil.serialization.XML
import java.io.File

class WraythImporter {
    fun importFile(file: File) {
        val contents = file.readText(Charsets.ISO_8859_1)
        val settings = XML.decodeFromString<WraythSettings>(contents)
        println(settings)
    }
}