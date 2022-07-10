package cc.warlock.warlock3.core.text

class Alias(
    pattern: String,
    private val replacement: String
) {
    val regex = Regex(pattern)

    fun replace(input: String): String {
        return regex.replace(input, replacement)
    }
}