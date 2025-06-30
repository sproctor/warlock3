package warlockfe.warlock3.core.macro

sealed interface MacroToken {
    data class Entity(val char: Char) : MacroToken
    data object At : MacroToken
    data class Text(val text: String) : MacroToken
    data class Variable(val name: String) : MacroToken
    data class Command(val name: String) : MacroToken
}