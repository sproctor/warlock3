package warlockfe.warlock3.core.util

fun String.splitFirstWord(): Pair<String, String?> {
    val list = trim().split(Regex("[ \t]+"), limit = 2)
    return Pair(list[0], list.getOrNull(1))
}
