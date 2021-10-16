package cc.warlock.warlock3.core.wsl

import cc.warlock.warlock3.core.StyledString
import kotlinx.coroutines.delay
import java.math.BigDecimal
import java.util.regex.Pattern

val wslCommands = mapOf<String, suspend (WslContext, String) -> Unit>(
    "echo" to { context, args ->
        context.client.print(StyledString(args))
    },
    "exit" to { context, _ ->
        context.stop()
    },
    "goto" to { context, argStr ->
        val lines = context.lines
        val args = argStr.trim().split("[ \t]+")
        if (args.isEmpty()) {
            throw WslRuntimeException("GOTO with no label")
        }
        val label = args[0]
        var index = lines.indexOfFirst { line ->
            line.labels.any { it.equals(other = label, ignoreCase = true) }
        }
        if (index == -1) {
            index = lines.indexOfFirst { line ->
                line.labels.any { it.equals(other = "labelError", ignoreCase = true) }
            }
        }
        if (index == -1) {
            throw WslRuntimeException("Could not find label \"$label\".")
        }
        context.setNextLine(index)
    },
    "move" to { context, args ->
        context.client.print(StyledString("Sending: $args"))
        context.client.sendCommand(args)
        context.waitForNav()
    },
    "pause" to { _, args ->
        val duration = args.toBigDecimalOrNull() ?: BigDecimal.ONE
        delay((duration * BigDecimal(1000)).toLong())
    },
    "put" to { context, args ->
        context.client.print(StyledString("Sending: $args"))
        context.client.sendCommand(args)
    },
    "setvariable" to { context, args ->
        val format = Pattern.compile("^([^\\s]+)(\\s+(.+)?)?$")

        val m = format.matcher(args)
        if (!m.find()) {
            throw WslRuntimeException("Invalid arguments to setvariable")
        }
        val name = m.group(1)
        val value = m.group(3) ?: " "
        //cx.scriptDebug(1, "setVariable: $name=$value")
        context.setVariable(name, WslValue.WslString(value))
    }
)