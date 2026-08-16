import androidx.compose.ui.unit.Density
import warlockfe.warlock3.compose.ui.window.toPx
import warlockfe.warlock3.core.client.DataDistance
import warlockfe.warlock3.core.client.Percentage
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The panel scale exists so a user can buy room inside boxes the game sized for Wrayth's compact grid.
 * It is deliberately narrow: it multiplies the pixel distances the game sends and nothing else, so a
 * percentage-sized widget keeps tracking the panel and the text keeps following the panel font.
 */
class PanelScaleTest {
    private val density = Density(1f)

    @Test
    fun scaleMultipliesPixelDistances() {
        val pixels = DataDistance.Pixels(100)
        assertEquals(100, pixels.toPx(basis = 500, density = density, scale = 1f))
        assertEquals(150, pixels.toPx(basis = 500, density = density, scale = 1.5f))
        assertEquals(50, pixels.toPx(basis = 500, density = density, scale = 0.5f))
    }

    @Test
    fun scaleLeavesPercentDistancesAlone() {
        // A percentage is a fraction of the panel, so it already grows with the panel; scaling it too
        // would push a half-width widget past the edge.
        val percent = DataDistance.Percent(Percentage(50))
        assertEquals(250, percent.toPx(basis = 500, density = density, scale = 1f))
        assertEquals(250, percent.toPx(basis = 500, density = density, scale = 2f))
    }

    @Test
    fun pixelDistancesIgnoreTheBasis() {
        val pixels = DataDistance.Pixels(40)
        assertEquals(
            pixels.toPx(basis = 100, density = density, scale = 1.2f),
            pixels.toPx(basis = 900, density = density, scale = 1.2f),
        )
    }
}
