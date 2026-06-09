package warlockfe.warlock3.scripting.wsl

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WslFrameTest {
    @Test
    fun startsAtGivenLineAndAdvances() {
        val frame = WslFrame(startLine = 0)
        assertEquals(0, frame.nextLine())
        assertEquals(1, frame.nextLine())
        assertEquals(2, frame.nextLine())
    }

    @Test
    fun nonZeroStartLine() {
        val frame = WslFrame(startLine = 5)
        assertEquals(5, frame.nextLine())
        assertEquals(6, frame.nextLine())
    }

    @Test
    fun lineNumberReflectsCurrentLine() {
        val frame = WslFrame(startLine = 0)
        // lineNumber is 1-based: -1 + 1 = 0 before execution begins
        assertEquals(0, frame.lineNumber)
        frame.nextLine()
        assertEquals(1, frame.lineNumber)
        frame.nextLine()
        assertEquals(2, frame.lineNumber)
    }

    @Test
    fun gotoChangesNextLine() {
        val frame = WslFrame(startLine = 0)
        frame.nextLine() // current 0
        frame.goto(10)
        assertEquals(10, frame.nextLine())
        assertEquals(11, frame.nextLine())
    }

    @Test
    fun gotoCanJumpBackward() {
        val frame = WslFrame(startLine = 0)
        frame.nextLine()
        frame.nextLine()
        frame.goto(0)
        assertEquals(0, frame.nextLine())
    }

    @Test
    fun variableSetAndLookup() {
        val frame = WslFrame(startLine = 0)
        assertNull(frame.lookupVariable("x"))
        frame.setVariable("x", WslString("hello"))
        assertEquals("hello", frame.lookupVariable("x")?.toString())
    }

    @Test
    fun variableOverwrite() {
        val frame = WslFrame(startLine = 0)
        frame.setVariable("x", WslString("first"))
        frame.setVariable("x", WslString("second"))
        assertEquals("second", frame.lookupVariable("x")?.toString())
    }

    @Test
    fun variableDelete() {
        val frame = WslFrame(startLine = 0)
        frame.setVariable("x", WslString("value"))
        frame.deleteVariable("x")
        assertNull(frame.lookupVariable("x"))
    }

    @Test
    fun deleteMissingVariableIsNoOp() {
        val frame = WslFrame(startLine = 0)
        frame.deleteVariable("missing")
        assertNull(frame.lookupVariable("missing"))
    }
}
