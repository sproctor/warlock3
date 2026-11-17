package warlockfe.warlock3.compose.shim

import kotlin.test.Test
import kotlin.test.assertEquals

class DropdownSelectionTest {
    @Test
    fun anItemInTheListIsShown() {
        assertEquals(1, dropdownSelectedIndex(listOf("a", "b", "c"), "b"))
    }

    @Test
    fun nullNotInTheListIsNothingSelected() {
        assertEquals(-1, dropdownSelectedIndex(listOf("a", "b"), null))
    }

    @Test
    fun nullIsARealItemWhereTheListHasIt() {
        assertEquals(0, dropdownSelectedIndex(listOf(null, "a", "b"), null))
    }

    @Test
    fun aValueNoLongerInTheListShowsTheFirstItem() {
        assertEquals(0, dropdownSelectedIndex(listOf("a", "b"), "gone"))
    }

    @Test
    fun anEmptyListShowsNothing() {
        assertEquals(-1, dropdownSelectedIndex(emptyList(), "a"))
    }
}
