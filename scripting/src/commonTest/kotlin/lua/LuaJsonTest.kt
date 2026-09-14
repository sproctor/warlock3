package warlockfe.warlock3.scripting.lua

import com.seanproctor.lua.LuaState
import com.seanproctor.lua.LuaValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LuaJsonTest {
    @Test
    fun jsonBecomesATableConstructor() {
        assertEquals(
            """{["a"]="x\"y\n",["b"]={1,2.5,true,nil,},["c"]={},["d"]=false,}""",
            jsonToLuaLiteral("""{"a":"x\"y\n","b":[1,2.5,true,null],"c":{},"d":false}"""),
        )
        assertEquals("\"just text\"", jsonToLuaLiteral("\"just text\""))
        assertEquals("42", jsonToLuaLiteral("42"))
    }

    @Test
    fun nullKeysAreLeftOutAndControlCharactersEscaped() {
        assertEquals("""{["b"]="\001\127",}""", jsonToLuaLiteral("""{"a":null,"b":"\u0001\u007f"}"""))
    }

    @Test
    fun whatIsNotJsonIsNil() {
        assertNull(jsonToLuaLiteral(""))
        assertNull(jsonToLuaLiteral("   "))
        assertNull(jsonToLuaLiteral("39\nhttps://example.com"))
    }

    @Test
    fun theLiteralLoadsInLua() {
        val literal = jsonToLuaLiteral("""{"name":"a \"quoted\" name","exits":{"n":1,"s":2},"list":["x",{"deep":true}],"n":-1.5e3}""")
        LuaState().use { lua ->
            lua.setGlobal("literal", LuaValue.Str(literal!!))
            val results =
                lua.eval(
                    """
                    local t = load("return " .. literal, "=t", "t", {})()
                    return t.name, t.exits.s, #t.list, t.list[2].deep, t.n
                    """.trimIndent(),
                )
            assertEquals(
                listOf(
                    LuaValue.Str("a \"quoted\" name"),
                    LuaValue.Integer(2),
                    LuaValue.Integer(2),
                    LuaValue.Bool(true),
                    LuaValue.Number(-1500.0),
                ),
                results,
            )
        }
    }
}
