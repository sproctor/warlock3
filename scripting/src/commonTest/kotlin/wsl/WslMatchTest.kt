package warlockfe.warlock3.scripting.wsl

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WslMatchTest {
    @Test
    fun textMatch_returnsTextWhenPresent() {
        val match = TextMatch(label = "found", text = "dragon")
        assertEquals("dragon", match.match("a fierce dragon appears", WslFrame(0)))
    }

    @Test
    fun textMatch_caseInsensitive() {
        val match = TextMatch(label = "found", text = "Dragon")
        assertEquals("Dragon", match.match("a fierce DRAGON appears", WslFrame(0)))
    }

    @Test
    fun textMatch_returnsNullWhenAbsent() {
        val match = TextMatch(label = "found", text = "dragon")
        assertNull(match.match("an empty room", WslFrame(0)))
    }

    @Test
    fun regexMatch_returnsMatchedValue() {
        val match = RegexMatch(label = "found", regex = Regex("[0-9]+"))
        assertEquals("42", match.match("you have 42 silver", WslFrame(0)))
    }

    @Test
    fun regexMatch_returnsNullWhenNoMatch() {
        val match = RegexMatch(label = "found", regex = Regex("[0-9]+"))
        assertNull(match.match("no digits here", WslFrame(0)))
    }

    @Test
    fun regexMatch_populatesMatchMapWithGroups() =
        runTest {
            val match = RegexMatch(label = "found", regex = Regex("([0-9]+) (\\w+)"))
            val frame = WslFrame(0)
            match.match("you have 42 silver", frame)
            val matchMap = frame.lookupVariable("match")
            assertEquals("42 silver", matchMap?.getProperty("0")?.toString())
            assertEquals("42", matchMap?.getProperty("1")?.toString())
            assertEquals("silver", matchMap?.getProperty("2")?.toString())
        }

    @Test
    fun regexMatch_doesNotSetMatchMapWhenNoMatch() {
        val match = RegexMatch(label = "found", regex = Regex("[0-9]+"))
        val frame = WslFrame(0)
        match.match("no digits here", frame)
        assertNull(frame.lookupVariable("match"))
    }
}
