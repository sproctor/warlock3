import kotlinx.serialization.json.Json
import warlockfe.warlock3.compose.model.SkinColor
import warlockfe.warlock3.compose.model.SkinObject
import warlockfe.warlock3.compose.model.forMode
import warlockfe.warlock3.compose.util.toPresets
import warlockfe.warlock3.core.text.WarlockColor
import kotlin.test.Test
import kotlin.test.assertEquals

class SkinJsonTest {
    val json =
        Json {
            ignoreUnknownKeys = true
        }

    @Test
    fun skin_object_parses() {
        val skinJson =
            """
            {
                "top": 10,
                "left": 100,
                "children": {
                    "child": {
                        "top": 10
                    }
                }
            }
            """.trimIndent()

        val skin = json.decodeFromString<SkinObject>(skinJson)

        assertEquals(
            expected =
                SkinObject(
                    top = 10,
                    left = 100,
                    children =
                        mapOf(
                            "child" to SkinObject(top = 10),
                        ),
                ),
            actual = skin,
        )
    }

    @Test
    fun skin_color_string_applies_to_both_modes() {
        val skin = json.decodeFromString<SkinObject>("""{ "background": "#222222" }""")

        assertEquals(SkinColor(light = "#222222", dark = "#222222"), skin.background)
        assertEquals("#222222", skin.background.forMode(isDark = false))
        assertEquals("#222222", skin.background.forMode(isDark = true))
    }

    @Test
    fun skin_color_object_resolves_per_mode() {
        val skin =
            json.decodeFromString<SkinObject>(
                """{ "background": { "light": "#EEEEEE", "dark": "#222222" } }""",
            )

        assertEquals("#EEEEEE", skin.background.forMode(isDark = false))
        assertEquals("#222222", skin.background.forMode(isDark = true))
    }

    @Test
    fun skin_color_object_falls_back_when_a_mode_is_missing() {
        val skin = json.decodeFromString<SkinObject>("""{ "background": { "dark": "#222222" } }""")

        // light is absent, so it falls back to the dark value.
        assertEquals("#222222", skin.background.forMode(isDark = false))
        assertEquals("#222222", skin.background.forMode(isDark = true))
    }

    @Test
    fun presets_section_converts_to_style_definitions() {
        val skinJson =
            """
            {
                "presets": {
                    "children": {
                        "default": { "color": "#F0F0FF", "background": { "light": "#EEEEEE", "dark": "#1E1F22" } },
                        "link": { "color": "#ADD8E6", "underline": true },
                        "roomName": { "color": "#FFFFFF", "background": "#0000FF", "entireLine": true },
                        "mono": { "monospace": true }
                    }
                }
            }
            """.trimIndent()
        val skin = json.decodeFromString<Map<String, SkinObject>>(skinJson)

        val light = skin.toPresets(isDark = false)
        val dark = skin.toPresets(isDark = true)

        assertEquals(WarlockColor("#F0F0FF"), light["default"]?.textColor)
        assertEquals(WarlockColor("#EEEEEE"), light["default"]?.backgroundColor)
        assertEquals(WarlockColor("#1E1F22"), dark["default"]?.backgroundColor)
        assertEquals(true, light["link"]?.underline)
        assertEquals(true, light["roomName"]?.entireLine)
        assertEquals(true, light["mono"]?.monospace)
    }

    @Test
    fun compass_direction_parses_sprite_and_rect() {
        val skinJson =
            """
            {
                "compass": {
                    "children": {
                        "cardinal": { "image": { "file": "images/compassCardinal.png" } },
                        "north": { "sprite": "cardinal", "left": 34, "top": 1, "width": 14, "height": 15 }
                    }
                }
            }
            """.trimIndent()

        val skin = json.decodeFromString<Map<String, SkinObject>>(skinJson)
        val north = skin["compass"]?.children?.get("north")

        assertEquals("cardinal", north?.sprite)
        assertEquals(34, north?.left)
        assertEquals(1, north?.top)
        assertEquals(14, north?.width)
        assertEquals(15, north?.height)
        assertEquals(
            "images/compassCardinal.png",
            skin["compass"]
                ?.children
                ?.get("cardinal")
                ?.image
                ?.file,
        )
    }

    @Test
    fun skin_map_parses() {
        val skinJson =
            """
            {
                "skin": {
                    "top": 10,
                    "left": 100,
                    "children": {
                        "child": {
                            "top": 10
                        }
                    }
                },
                "skin2": {
                    "top": 100,
                    "left": 200,
                    "children": {
                        "child": {
                            "top": 20
                        }
                    }
                }
            }
            """.trimIndent()

        val skin = json.decodeFromString<Map<String, SkinObject>>(skinJson)

        assertEquals(
            expected =
                mapOf(
                    "skin" to
                        SkinObject(
                            top = 10,
                            left = 100,
                            children =
                                mapOf(
                                    "child" to SkinObject(top = 10),
                                ),
                        ),
                    "skin2" to
                        SkinObject(
                            top = 100,
                            left = 200,
                            children =
                                mapOf(
                                    "child" to SkinObject(top = 20),
                                ),
                        ),
                ),
            actual = skin,
        )
    }

    // FIXME: Doesn't work on Android
//    @Test
//    fun default_skin_file_parses(): Unit = runBlocking {
//        try {
//            val skinJson = Res.readBytes("files/skin.json")
//
//            val skin = json.decodeFromString<Map<String, SkinObject>>(skinJson.decodeToString())
//
//            val injury1 = skin.getIgnoringCase("injury1")
//            assertNotNull(injury1?.image)
//        } catch (e: Exception) {
//            println(e.message)
//            throw e
//        }
//    }
}
