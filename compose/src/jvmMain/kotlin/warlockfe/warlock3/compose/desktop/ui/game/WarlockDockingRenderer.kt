package warlockfe.warlock3.compose.desktop.ui.game

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.seanproctor.docking.jewel.JewelDockingRenderer
import com.seanproctor.docking.spi.DockingRenderer
import com.seanproctor.docking.spi.PaneFrameModel

/**
 * The Jewel docking renderer with the game's own window frame around each pane.
 *
 * The frame belongs here rather than on the window body because a dock pane is a header stacked on
 * a body, and the body is composed several layers below the header: a border drawn there starts
 * under the title bar, leaving the title floating above the box it names. `paneFrame` is the slot
 * that wraps both, so the border encloses the header with the window it belongs to.
 *
 * [selectedWindow] rather than the pane's own `isActive` because the two disagree at startup: the
 * dock has no active dockable until something is clicked, while the game always has a selected
 * window (the main one, to begin with). Following the game's is what keeps the main window looking
 * selected on a screen nobody has touched yet, and the two agree from the first click on.
 */
internal class WarlockDockingRenderer(
    private val selectedWindow: State<String>,
) : DockingRenderer by JewelDockingRenderer {
    @Composable
    override fun paneFrame(model: PaneFrameModel): Modifier {
        val shape = RoundedCornerShape(4.dp)
        val isSelected = selectedWindow.value == model.dockableId.value
        return Modifier
            // Clip so a body that paints its own background (panels, user styles) does not
            // overdraw the rounded corners.
            .clip(shape)
            .background(gameChrome.panel, shape)
            .border(
                Dp.Hairline,
                if (isSelected) gameChrome.borderStrong else gameChrome.border,
                shape,
            )
    }
}
