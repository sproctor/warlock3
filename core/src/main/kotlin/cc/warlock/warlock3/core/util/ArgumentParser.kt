package cc.warlock.warlock3.core.util

/**
 * Parses argumentString into a list of strings
 *   an argument can be in quotes to have spaces in the argument
 */
fun parseArguments(input: String): List<String> {
    val result = mutableListOf<String>()
    var inQuotes = false
    var inEscape = false
    val current = StringBuilder()
    for (i in input.indices) {
        // If this character is escaped, add it without looking
        if (inEscape) {
            inEscape = false
            current.append(input[i])
            continue
        }
        when (val c = input[i]) {
            '\\' -> inEscape = true // escape the next character, \ isn't added to result
            ' ' -> if (inQuotes) {
                current.append(c)
            } else {
                result += current.toString()
                current.clear()
            }
            '"' -> inQuotes = !inQuotes
            else -> current.append(c)
        }
    }
    if (current.isNotEmpty()) {
        result += current.toString()
    }
    return result
}