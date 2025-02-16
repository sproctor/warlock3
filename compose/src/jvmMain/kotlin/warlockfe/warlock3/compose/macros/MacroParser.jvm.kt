package warlockfe.warlock3.compose.macros

import warlockfe.warlock3.core.macro.MacroToken
import warlockfe.warlock3.macro.parseMacro

actual fun parseMacroCommand(macroString: String): List<MacroToken>? =
    parseMacro(macroString)