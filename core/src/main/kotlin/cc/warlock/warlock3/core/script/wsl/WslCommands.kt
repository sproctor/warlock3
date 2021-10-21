package cc.warlock.warlock3.core.script.wsl

import cc.warlock.warlock3.core.util.findArgumentBreak
import kotlinx.coroutines.delay
import java.math.BigDecimal
import kotlin.random.Random

// stolen from python
val loggingLevels = mapOf(
    "debug" to 10,
    "info" to 20,
    "warning" to 30,
    "error" to 40,
    "critical" to 50
)

val wslCommands = mapOf<String, suspend (WslContext, String) -> Unit>(
    "counter" to { context, args ->
        val (operator, operandString) = args.splitFirstWord()
        val operand = operandString?.let {
            it.toBigDecimalOrNull() ?: throw WslRuntimeException("Counter operand must be a number")
        } ?: BigDecimal.ONE
        val current = context.lookupVariable("c")?.toNumber() ?: BigDecimal.ZERO
        val result = when (operator.lowercase()) {
            "set" -> operand
            "add" -> current + operand
            "subtract" -> current - operand
            "multiply" -> current * operand
            "divide" -> current / operand
            else -> throw WslRuntimeException("Unsupported counter operator")
        }
        context.setScriptVariable("c", WslNumber(result))
    },
    "debug" to { context, args ->
        context.log(10, args)
    },
    "deletevariable" to { context, args ->
        val (name, _) = args.splitFirstWord()
        context.deleteStoredVariable(name)
    },
    "echo" to { context, args ->
        context.echo(args)
    },
    "error" to { context, args ->
        context.log(40, args)
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
    "info" to { context, args ->
        context.log(20, args)
    },
    "level" to { context, args ->
        val (level, _) = args.splitFirstWord()
        level.toIntOrNull()?.let {
            if (it > 50) {
                throw WslRuntimeException("maximum logging level is 50")
            }
            context.setLoggingLevel(it)
        } ?:
        loggingLevels[level]?.let {
            context.setLoggingLevel(it)
        } ?:
        throw WslRuntimeException("Invalid logging level")
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
        context.sendCommand(args)
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
        context.sendCommand(args)
    },
    "random" to { context, args ->
        val argList = args.split(Regex("[ \t]+"))
        val min = argList[0].toIntOrNull() ?: throw WslRuntimeException("Invalid arguments to random")
        val max = argList.getOrNull(1)?.toIntOrNull() ?: throw WslRuntimeException("Invalid arguments to random")
        context.setScriptVariable("r", WslNumber(Random.nextInt(min, max).toBigDecimal()))
    },
    "save" to { context, args ->
        context.setScriptVariable("s", WslString(args))
    },
    "setvariable" to { context, args ->
        val (name, value) = args.splitFirstWord()

        if (name.isBlank()) {
            throw WslRuntimeException("Invalid arguments to setvariable")
        }
        //cx.scriptDebug(1, "setVariable: $name=$value")
        context.setStoredVariable(name, value ?: "")
    },
    "shift" to { context, _ ->
        var i = 1
        while (true) {
            val nextName = (i + 1).toString()
            val nextVar = context.lookupVariable(nextName)
            if (nextVar != null) {
                context.setScriptVariable(i.toString(), nextVar)
            } else {
                context.deleteScriptVariable(i.toString())
                break
            }
            i++
        }
        val allArgs = context.lookupVariable("0").toString()
        val breakIndex = findArgumentBreak(allArgs)
        context.setScriptVariable(
            name = "0",
            value = WslString(
                if (breakIndex >= 0) {
                    allArgs.substring(breakIndex + 1)
                } else {
                    ""
                }
            ),
        )
    },
    "timer" to { context, args ->
        val (command, _) = args.splitFirstWord()
        when (command) {
            "start" -> {
                context.setScriptVariable("t", WslTimer())
            }
            "stop" -> {
                val timer = context.lookupVariable("t")
                if (timer is WslTimer) {
                    context.setScriptVariable("t", WslNumber(timer.toNumber()))
                } else {
                    // TODO warn that timer isn't running
                }
            }
            "clear" -> {
                context.deleteScriptVariable("t")
            }
        }
    },
    "wait" to { context, _ ->
        context.waitForPrompt()
    },
    "waitfor" to { context, args ->
        context.waitForText(args, ignoreCase = true)
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

class WslTimer : WslValue {
    private val startTime = System.currentTimeMillis()
    override fun toBoolean(): Boolean {
        throw WslRuntimeException("Cannot convert timer to boolean")
    }

    override fun toNumber(): BigDecimal {
        return ((System.currentTimeMillis() - startTime) / 1000L).toBigDecimal()
    }

    override fun isNumeric(): Boolean {
        return true
    }

    override fun toString(): String {
        return toNumber().toString()
    }
}