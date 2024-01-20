package warlockfe.warlock3.compose.macros

import warlockfe.warlock3.core.macro.MacroToken
import warlockfe.warlock3.macro.parseMacro

actual fun parseMacroCommand(macroString: String): Result<List<MacroToken>> =
    parseMacro(macroString)