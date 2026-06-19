package warlockfe.warlock3.compose.desktop.ui.game

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import warlockfe.warlock3.compose.util.LocalDarkTheme

/**
 * The desktop game-screen "chrome" palette: window frames, headers, the control bar, hands,
 * indicators and the compass. These are the surfaces *around* the game text (the text itself stays
 * driven by the user's style presets).
 *
 * The values are a port of the "Jewel / IntelliJ" pane from the Warlock Game Screen design. Both a
 * [dark][DarkChromeColors] and a [light][LightChromeColors] variant exist; the active one follows
 * the app's [LocalDarkTheme], read through the [WarlockGameChrome] accessor (the same pattern as
 * `MaterialTheme.colorScheme`), so existing `WarlockGameChrome.x` call sites keep working unchanged.
 *
 * A few accent colors (the violet selected-window header, and the cast/roundtime overlay colors that
 * are keyed off the entry's own background luminance) are intentionally identical in both variants.
 */
data class WarlockGameChromeColors(
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
    // Roundtime / cast-time overlay (used only over a dark entry background).
    val castBar: Color,
    val castText: Color,
    val roundtimeBar: Color,
    val roundtimeText: Color,
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

/** The dark "Jewel / IntelliJ" chrome: a cohesive dark theme with a violet accent. */
private val DarkChromeColors =
    WarlockGameChromeColors(
        appBackground = Color(0xFF14161A),
        controlBar = Color(0xFF0E1013),
        panel = Color(0xFF1B1E24),
        panelAlt = Color(0xFF16181D),
        header = Color(0xFF20242B),
        border = Color(0xFF2A2F38),
        borderStrong = Color(0xFF3A3F49),
        accent = Color(0xFF6F4FB5),
        accentText = Color(0xFFF5F0FF),
        accentSubtle = Color(0xFFCDBCF2),
        textPrimary = Color(0xFFD8D2C4),
        textMuted = Color(0xFF9AA0AA),
        textFaint = Color(0xFF7E8590),
        caption = Color(0xFF6B7280),
        entryBackground = Color(0xFF0A0C0F),
        spellText = Color(0xFFC9A8E0),
        spellIcon = Color(0xFFA98FC9),
        castBar = Color(0xFF3F7CC5),
        castText = Color(0xFF5B9BD6),
        roundtimeBar = Color(0xFFC5483F),
        roundtimeText = Color(0xFFCF6259),
        litBackground = Color(0xFF221B0C),
        litBorder = Color(0xFFC9A25E),
        litIcon = Color(0xFFD8B878),
        dangerBackground = Color(0xFF2A1513),
        dangerBorder = Color(0xFFB23B30),
        dangerIcon = Color(0xFFE06B60),
        compassDarkIcon = Color(0xFF3F444D),
    )

/** The light variant: an IntelliJ-light surface hierarchy keeping the same violet accent. */
private val LightChromeColors =
    WarlockGameChromeColors(
        appBackground = Color(0xFFF2F3F5),
        controlBar = Color(0xFFE6E8EB),
        panel = Color(0xFFFCFCFD),
        panelAlt = Color(0xFFEDEFF2),
        header = Color(0xFFE3E5E9),
        border = Color(0xFFD3D6DC),
        borderStrong = Color(0xFFB8BCC4),
        accent = Color(0xFF6F4FB5),
        accentText = Color(0xFFF5F0FF),
        accentSubtle = Color(0xFF6A4DB0),
        textPrimary = Color(0xFF1F2229),
        textMuted = Color(0xFF5A616B),
        textFaint = Color(0xFF6E757F),
        caption = Color(0xFF7C828C),
        entryBackground = Color(0xFFFFFFFF),
        spellText = Color(0xFF7A3FA8),
        spellIcon = Color(0xFF9466C4),
        castBar = Color(0xFF3F7CC5),
        castText = Color(0xFF5B9BD6),
        roundtimeBar = Color(0xFFC5483F),
        roundtimeText = Color(0xFFCF6259),
        litBackground = Color(0xFFFBF1DA),
        litBorder = Color(0xFFBE9A52),
        litIcon = Color(0xFF8A6A1F),
        dangerBackground = Color(0xFFFBE3E0),
        dangerBorder = Color(0xFFB23B30),
        dangerIcon = Color(0xFFC0392B),
        compassDarkIcon = Color(0xFFBCC0C7),
    )

/** The active chrome palette for the current light/dark mode (driven by [LocalDarkTheme]). */
val WarlockGameChrome: WarlockGameChromeColors
    @Composable
    @ReadOnlyComposable
    get() = if (LocalDarkTheme.current) DarkChromeColors else LightChromeColors
