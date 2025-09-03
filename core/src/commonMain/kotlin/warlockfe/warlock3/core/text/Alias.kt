package warlockfe.warlock3.core.text

class Alias(
    pattern: String,
    private val replacement: String
) {
    val regex = Regex(pattern)

    fun replace(input: String): String {
        return try {
            regex.replace(input, replacement)
        } catch (_: RuntimeException) {
            // TODO: report invalid alias
            input
        }
    }
}