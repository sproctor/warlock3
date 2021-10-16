package cc.warlock.warlock3.core.wsl

import cc.warlock.warlock3.core.StyledString
import cc.warlock.warlock3.core.util.findArgumentBreak
import kotlinx.coroutines.delay
import java.math.BigDecimal

val wslCommands = mapOf<String, suspend (WslContext, String) -> Unit>(
    "echo" to { context, args ->
        context.client.print(StyledString(args))
    },
    "exit" to { context, _ ->
        context.stop()
    },
    "goto" to { context, argStr ->
        val (label, _) = argStr.splitFirstWord()
        if (label.isBlank()) {
            throw WslRuntimeException("GOTO with no label")
        }
        context.goto(label)
    },
    "match" to { context, args ->
        val (label, text) = args.splitFirstWord()
        if (text?.isBlank() != false) {
            throw WslRuntimeException("Blank text in match")
        }
        context.addMatch(TextMatch(label, text))
    },
    "matchre" to { context, args ->
        val (label, text) = args.splitFirstWord()
        val regex = text?.let { parseRegex(it) } ?: throw WslRuntimeException("Invalid regex in matchRe")
        context.addMatch(RegexMatch(label, regex))
    },
    "matchwait" to { context, _ ->
        // TODO timeout after $args seconds
        context.matchWait()
    },
    "move" to { context, args ->
        context.client.print(StyledString("Sending: $args"))
        context.client.sendCommand(args)
        context.waitForNav()
    },
    "nextroom" to { context, _ ->
        context.waitForNav()
    },
    "pause" to { _, args ->
        val (arg, _) = args.splitFirstWord()
        val duration = arg.toBigDecimalOrNull() ?: BigDecimal.ONE
        delay((duration * BigDecimal(1000)).toLong())
    },
    "put" to { context, args ->
        context.client.print(StyledString("Sending: $args"))
        context.client.sendCommand(args)
    },
    "setvariable" to { context, args ->
        val (name, value) = args.splitFirstWord()

        if (name.isBlank()) {
            throw WslRuntimeException("Invalid arguments to setvariable")
        }
        //cx.scriptDebug(1, "setVariable: $name=$value")
        context.setVariable(name, WslValue.WslString(value ?: ""))
    },
    "shift" to { context, _ ->
        var i = 1
        while (true) {
            val nextName = (i + 1).toString()
            if (context.hasVariable(nextName)) {
                context.setVariable(i.toString(), context.lookupVariable(nextName))
            } else {
                context.deleteVariable(i.toString())
                break
            }
            i++
        }
        val allArgs = context.lookupVariable("0").toString()
        val breakIndex = findArgumentBreak(allArgs)
        if (breakIndex >= 0) {
            context.setVariable("0", WslValue.WslString(allArgs.substring(breakIndex + 1)))
        } else {
            context.setVariable("0", WslValue.WslString(""))
        }
    },
    "wait" to { context, _ ->
        context.waitForPrompt()
    },
    "waitfor" to { context, args ->
        context.waitForText(args)
    },
    "waitforre" to { context, args ->
        context.waitForRegex(
            parseRegex(args) ?: throw WslRuntimeException("Invalid regex in waitForRe")
        )
    },
) + (1..9).map { "if_$it" to ifNCommand(it) }

fun parseRegex(text: String): Regex? {
    val regex = Regex("/(.*)/(i)?")
    return regex.find(text)?.let { result ->
        Regex(
            pattern = result.groups[1]!!.value,
            options = if (result.groups[2] != null) setOf(RegexOption.IGNORE_CASE) else emptySet(),
        )
    }
}

fun ifNCommand(n: Int): suspend (WslContext, String) -> Unit {
    return { context, args ->
        if (context.hasVariable(n.toString())) {
            context.executeCommand(args)
        }
    }
}

sealed class ScriptMatch(val label: String) {
    abstract fun match(line: String): String?
}
class TextMatch(label: String, val text: String) : ScriptMatch(label) {
    override fun match(line: String): String? {
        if (line.contains(text)) {
            return text
        }
        return null
    }
}
class RegexMatch(label: String, val regex: Regex) : ScriptMatch(label) {
    override fun match(line: String): String? {
        return regex.find(line)?.value
    }
}

fun String.splitFirstWord(): Pair<String, String?> {
    val list = trim().split(Regex("[ \t]+"), limit = 2)
    return Pair(list[0], list.getOrNull(1))
}