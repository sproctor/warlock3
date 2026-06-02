import kotlinx.serialization.json.Json
import warlockfe.warlock3.compose.model.SkinObject
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
            skin["compass"]?.children?.get("cardinal")?.image?.file,
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
