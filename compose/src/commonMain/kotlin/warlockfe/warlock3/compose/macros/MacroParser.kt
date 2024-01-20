package warlockfe.warlock3.compose.macros

import warlockfe.warlock3.core.macro.MacroToken

expect fun parseMacroCommand(macroString: String): Result<List<MacroToken>>