import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import warlockfe.warlock3.compose.generated.resources.Res
import warlockfe.warlock3.compose.util.SkinObject
import warlockfe.warlock3.core.util.getIgnoringCase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class SkinJsonTest {
    val json = Json {
        ignoreUnknownKeys = true
    }

    @Test
    fun skin_object_parses() {
        val skinJson = """
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
            expected = SkinObject(
                top = 10,
                left = 100,
                children = mapOf(
                    "child" to SkinObject(top = 10)
                )
            ),
            actual = skin,
        )
    }

    @Test
    fun skin_map_parses() {
        val skinJson = """
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
            expected = mapOf(
                "skin" to SkinObject(
                    top = 10,
                    left = 100,
                    children = mapOf(
                        "child" to SkinObject(top = 10)
                    ),
                ),
                "skin2" to SkinObject(
                    top = 100,
                    left = 200,
                    children = mapOf(
                        "child" to SkinObject(top = 20)
                    )
                )
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