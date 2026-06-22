package warlockfe.warlock3.compose.util

import androidx.compose.ui.input.key.Key
import warlockfe.warlock3.core.macro.MacroKeyCombo
import warlockfe.warlock3.core.prefs.repositories.ClientSettingRepository
import warlockfe.warlock3.core.prefs.repositories.MacroRepository

// Bump this and tag new entries in [defaultGlobalMacros] with the new number whenever defaults are
// added, so existing users get them on upgrade (see [seedAndMigrateDefaultMacros]).
const val MACRO_DEFAULTS_VERSION = 2

// Baseline assumed for users who already have macros but predate the version marker: they were
// seeded with the version-1 set on first run, so we never re-add (or resurrect a deleted) v1 default.
const val PRE_MARKER_BASELINE_VERSION = 1

data class DefaultMacro(
    val keyCombo: MacroKeyCombo,
    val action: String,
    val sinceVersion: Int,
)

// TODO: refactor Macros to use a custom datatype and remove compose dependency
val defaultGlobalMacros: List<DefaultMacro> =
    listOf(
        DefaultMacro(MacroKeyCombo(Key.DirectionUp.keyCode), "{HistoryPrev}", 1),
        DefaultMacro(MacroKeyCombo(Key.DirectionDown.keyCode), "{HistoryNext}", 1),
        DefaultMacro(MacroKeyCombo(Key.NumPad1.keyCode), "\\xsw\\r\\?", 1),
        DefaultMacro(MacroKeyCombo(Key.NumPad2.keyCode), "\\xs\\r\\?", 1),
        DefaultMacro(MacroKeyCombo(Key.NumPad3.keyCode), "\\xse\\r\\?", 1),
        DefaultMacro(MacroKeyCombo(Key.NumPad4.keyCode), "\\xw\\r\\?", 1),
        DefaultMacro(MacroKeyCombo(Key.NumPad5.keyCode), "\\xout\\r\\?", 1),
        DefaultMacro(MacroKeyCombo(Key.NumPad6.keyCode), "\\xe\\r\\?", 1),
        DefaultMacro(MacroKeyCombo(Key.NumPad7.keyCode), "\\xnw\\r\\?", 1),
        DefaultMacro(MacroKeyCombo(Key.NumPad8.keyCode), "\\xn\\r\\?", 1),
        DefaultMacro(MacroKeyCombo(Key.NumPad9.keyCode), "\\xne\\r\\?", 1),
        DefaultMacro(MacroKeyCombo(Key.NumPad0.keyCode), "\\xdown\\r\\?", 1),
        // remove work-around when this fix lands: https://youtrack.jetbrains.com/issue/CMP-4211
        DefaultMacro(MacroKeyCombo(Key.NumPadDotFix.keyCode), "\\xup\\r\\?", 1),
        DefaultMacro(MacroKeyCombo(Key.Escape.keyCode), "{StopScript}", 1),
        DefaultMacro(MacroKeyCombo(Key.Escape.keyCode, shift = true), "{PauseScript}", 1),
        DefaultMacro(MacroKeyCombo(Key.NumPadEnter.keyCode), "{ReturnOrRepeatLast}", 1),
        DefaultMacro(MacroKeyCombo(Key.Enter.keyCode, ctrl = true), "{RepeatLast}", 1),
        DefaultMacro(MacroKeyCombo(Key.Enter.keyCode, alt = true), "{RepeatSecondToLast}", 1),
        DefaultMacro(MacroKeyCombo(Key.NumPadEnter.keyCode, ctrl = true), "{RepeatLast}", 1),
        DefaultMacro(MacroKeyCombo(Key.NumPadEnter.keyCode, alt = true), "{RepeatSecondToLast}", 1),
        DefaultMacro(MacroKeyCombo(Key.PageUp.keyCode), "{PageUp}", 1),
        DefaultMacro(MacroKeyCombo(Key.PageDown.keyCode), "{PageDown}", 1),
        DefaultMacro(MacroKeyCombo(Key.U.keyCode, ctrl = true), "{ClearToStart}", 1),
        DefaultMacro(MacroKeyCombo(Key.K.keyCode, ctrl = true), "{ClearToEnd}", 1),
        DefaultMacro(MacroKeyCombo(Key.R.keyCode, ctrl = true), "{HistorySearch}", 2),
        DefaultMacro(MacroKeyCombo(Key.R.keyCode, ctrl = true, shift = true), "{HistorySearchExit}", 2),
    )

/** Seeds the full current default set, but only when no global macros exist (used by reset-to-defaults). */
suspend fun MacroRepository.insertDefaultMacrosIfNeeded() {
    if (getGlobalCount() == 0) {
        defaultGlobalMacros.forEach { put("global", it.keyCombo, it.action) }
    }
}

/**
 * Seeds defaults on a fresh install and merges newly introduced defaults into existing installs on
 * upgrade. Only defaults newer than the recorded version whose key combo isn't already bound are
 * added, so user-rebound keys and deliberately deleted older defaults are left alone. Run once at
 * startup.
 */
suspend fun MacroRepository.seedAndMigrateDefaultMacros(clientSettings: ClientSettingRepository) {
    val baseline =
        when {
            getGlobalCount() == 0 -> 0
            else -> clientSettings.getMacroDefaultsVersion() ?: PRE_MARKER_BASELINE_VERSION
        }
    addMissingGlobalMacros(
        defaultGlobalMacros.filter { it.sinceVersion > baseline }.map { it.keyCombo to it.action },
    )
    clientSettings.putMacroDefaultsVersion(MACRO_DEFAULTS_VERSION)
}
