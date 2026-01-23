package warlockfe.warlock3.core.util

fun <T> List<T>.replaceOrAdd(element: T, predicate: (T) -> Boolean): List<T> =
    toMutableList().apply {
        val index = indexOfFirst(predicate)
        if (index != -1) set(index, element)
        else add(element)
    }
