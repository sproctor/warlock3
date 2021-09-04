package cc.warlock.warlock3.core.wsl

import cc.warlock.warlock3.core.StyledString
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
        val lines = context.lines
        val args = argStr.trim().split("[ \t]+")
        if (args.isEmpty()) {
            throw WslRuntimeException("GOTO with no label")
        }
        val label = args[0]
        lines.forEachIndexed { index, line ->
            if (line.label == label) {
                context.setNextLine(index)
            }
        }
    },
    "pause" to { _, args ->
        val duration = args.toBigDecimalOrNull() ?: BigDecimal.ONE
        delay((duration * BigDecimal(1000)).toLong())
    },
    "put" to { context, args ->
        context.client.print(StyledString("Sending: $args"))
        context.client.sendCommand(args)
    },
)