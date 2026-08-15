package warlockfe.warlock3.compose.ui.window

import kotlinx.coroutines.test.runTest
import warlockfe.warlock3.core.client.PanelObject
import kotlin.test.Test
import kotlin.test.assertEquals

class ComposePanelStateTest {
    private fun label(
        id: String,
        value: String,
    ): PanelObject =
        PanelObject.Label(
            id = id,
            value = value,
            justify = warlockfe.warlock3.core.client.PanelJustify.Left,
            left = null,
            top = null,
            width = null,
            height = null,
            align = null,
            topAnchor = null,
            leftAnchor = null,
            tooltip = null,
        )

    private suspend fun ComposePanelState.ids(): List<String> {
        updateState()
        return objects.value.map { it.id }
    }

    @Test
    fun anUpdatedWidgetKeepsItsPlace() =
        runTest {
            val state = ComposePanelState("combat")
            listOf("m1", "m2", "m3").forEach { state.setObject(label(it, "none")) }
            assertEquals(listOf("m1", "m2", "m3"), state.ids())

            // The server acknowledges a change by resending just that widget. Moving it to the end
            // would reorder the panel and shift every widget after it into a new slot.
            state.setObject(label("m1", "feint"))
            assertEquals(listOf("m1", "m2", "m3"), state.ids())
            assertEquals(
                listOf("feint", "none", "none"),
                state.objects.value.map { (it as PanelObject.Label).value },
            )
        }

    @Test
    fun anUnknownWidgetIsAppended() =
        runTest {
            val state = ComposePanelState("combat")
            state.setObject(label("m1", "none"))
            state.setObject(label("m2", "none"))
            assertEquals(listOf("m1", "m2"), state.ids())
        }

    @Test
    fun clearEmptiesThePanel() =
        runTest {
            val state = ComposePanelState("combat")
            state.setObject(label("m1", "none"))
            state.clear()
            assertEquals(emptyList(), state.ids())
        }
}
