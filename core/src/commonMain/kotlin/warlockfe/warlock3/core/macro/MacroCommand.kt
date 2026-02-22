package warlockfe.warlock3.core.macro

data class MacroCommand(
    val name: String,
    val aliases: List<String> = emptyList(),
    val execute: suspend (MacroHandler) -> Unit
)
