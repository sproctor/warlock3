package warlockfe.warlock3.compose.desktop.ui.game

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import warlockfe.warlock3.compose.model.forMode
import warlockfe.warlock3.compose.util.LocalDarkTheme
import warlockfe.warlock3.compose.util.LocalSkin
import warlockfe.warlock3.compose.util.toColor
import warlockfe.warlock3.core.util.getIgnoringCase
import warlockfe.warlock3.core.util.toWarlockColor

/**
 * Resolved desktop game-screen "chrome" colors: the surfaces *around* the game text (window frames,
 * headers, the control bar, hands, indicators and the compass). The text itself stays driven by the
 * user's style presets.
 *
 * The values live in the active skin's `gameChrome` section and are resolved for the current
 * light/dark mode by the [gameChrome] accessor; the bundled default skin ports the "Jewel / IntelliJ"
 * palette (a violet accent over a neutral surface hierarchy, with light and dark variants). A skin
 * that omits a key leaves that color [unspecified][Color.Unspecified].
 */
data class GameChrome(
    /** App background behind the whole work area. */
    val appBackground: Color,
    /** The control bar pinned to the bottom. */
    val controlBar: Color,
    /** Window/dialog body background. */
    val panel: Color,
    /** Secondary panel surface: sidebar, hand boxes, unlit compass cells. */
    val panelAlt: Color,
    /** Unselected window header. */
    val header: Color,
    /** Hairline borders between surfaces. */
    val border: Color,
    /** A brighter border used to emphasise the selected window and the command entry. */
    val borderStrong: Color,
    /** Violet accent: the selected window header. */
    val accent: Color,
    /** Title text on the accent header. */
    val accentText: Color,
    /** Subtle accent for icons/handles (selected drag handle, compass-style check). */
    val accentSubtle: Color,
    /** Primary chrome text (titles, hand values). */
    val textPrimary: Color,
    /** Muted chrome text (unselected window titles). */
    val textMuted: Color,
    /** Faint chrome text/icons (drag handles, hand icons, prompt glyphs). */
    val textFaint: Color,
    /** Tiny caption labels (LEFT / RIGHT / SPELL). */
    val caption: Color,
    /** Default command-entry background when the user hasn't set an "entry" style. */
    val entryBackground: Color,
    /** Prepared-spell value accent. */
    val spellText: Color,
    /** Prepared-spell icon accent. */
    val spellIcon: Color,
    // "Lit" amber accent: active posture/concealment indicators and available compass directions.
    val litBackground: Color,
    val litBorder: Color,
    val litIcon: Color,
    // Danger red accent: bleeding / dead.
    val dangerBackground: Color,
    val dangerBorder: Color,
    val dangerIcon: Color,
    /** Icon color for an unavailable (dim) compass direction. */
    val compassDarkIcon: Color,
)

/**
 * The chrome palette from the active skin's `gameChrome` section, resolved for the current
 * [light/dark mode][LocalDarkTheme]. Mirrors the `MaterialTheme.colorScheme` access pattern.
 */
val gameChrome: GameChrome
    @Composable
    @ReadOnlyComposable
    get() {
        val children = LocalSkin.current.getIgnoringCase("gameChrome")?.children
        val isDark = LocalDarkTheme.current

        fun color(name: String): Color =
            children
                ?.getIgnoringCase(name)
                ?.color
                .forMode(isDark)
                ?.toWarlockColor()
                .toColor()
        return GameChrome(
            appBackground = color("appBackground"),
            controlBar = color("controlBar"),
            panel = color("panel"),
            panelAlt = color("panelAlt"),
            header = color("header"),
            border = color("border"),
            borderStrong = color("borderStrong"),
            accent = color("accent"),
            accentText = color("accentText"),
            accentSubtle = color("accentSubtle"),
            textPrimary = color("textPrimary"),
            textMuted = color("textMuted"),
            textFaint = color("textFaint"),
            caption = color("caption"),
            entryBackground = color("entryBackground"),
            spellText = color("spellText"),
            spellIcon = color("spellIcon"),
            litBackground = color("litBackground"),
            litBorder = color("litBorder"),
            litIcon = color("litIcon"),
            dangerBackground = color("dangerBackground"),
            dangerBorder = color("dangerBorder"),
            dangerIcon = color("dangerIcon"),
            compassDarkIcon = color("compassDarkIcon"),
        )
    }
