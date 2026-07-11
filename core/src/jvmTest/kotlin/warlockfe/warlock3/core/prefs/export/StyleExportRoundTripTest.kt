package warlockfe.warlock3.core.prefs.export

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import warlockfe.warlock3.core.text.WarlockColor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The export models must round-trip the newly-editable style fields and still load older exports. */
class StyleExportRoundTripTest {
    private val json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

    private val red = WarlockColor(red = 255, green = 0, blue = 0)
    private val blue = WarlockColor(red = 0, green = 0, blue = 255)

    @Test
    fun styleExportRoundTripsFontFields() {
        val style =
            StyleExport(
                textColor = red,
                backgroundColor = blue,
                entireLine = false,
                bold = false,
                italic = true,
                underline = false,
                monospace = false,
                weight = 600,
                fontFamily = "Menlo",
                fontSize = 14f,
            )
        val text = json.encodeToString(StyleExport.serializer(), style)
        assertEquals(style, json.decodeFromString(StyleExport.serializer(), text))
    }

    @Test
    fun oldStyleExportWithoutFontFieldsLoadsWithDefaults() {
        val full =
            json.encodeToJsonElement(
                StyleExport.serializer(),
                StyleExport(red, blue, false, true, false, false, false),
            ) as JsonObject
        val legacy = JsonObject(full.filterKeys { it !in setOf("weight", "fontFamily", "fontSize") })
        val parsed = json.decodeFromJsonElement(StyleExport.serializer(), legacy)
        assertEquals(true, parsed.bold)
        assertNull(parsed.weight)
        assertNull(parsed.fontFamily)
        assertNull(parsed.fontSize)
    }

    @Test
    fun oldWindowExportWithoutStyleFlagsLoadsWithDefaults() {
        val full =
            json.encodeToJsonElement(
                WindowSettingsExport.serializer(),
                WindowSettingsExport(
                    name = "main",
                    width = null,
                    height = null,
                    location = null,
                    position = null,
                    textColor = red,
                    backgroundColor = blue,
                ),
            ) as JsonObject
        val legacy = JsonObject(full.filterKeys { it !in setOf("bold", "italic", "underline") })
        val parsed = json.decodeFromJsonElement(WindowSettingsExport.serializer(), legacy)
        assertEquals(false, parsed.bold)
        assertEquals(false, parsed.italic)
        assertEquals(false, parsed.underline)
    }
}
