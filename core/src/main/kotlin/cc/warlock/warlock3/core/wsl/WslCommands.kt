package cc.warlock.warlock3.core.wsl

import cc.warlock.warlock3.core.StyledString
import kotlinx.coroutines.delay
import java.math.BigDecimal

val wslCommands = mapOf<String, suspend (WslContext, String) -> Unit>(
    "pause" to { _, args ->
        val duration = args.toBigDecimalOrNull() ?: BigDecimal.ONE
        delay((duration * BigDecimal(1000)).toLong())
    },
    "print" to { context, args ->
        context.client.print(StyledString(args))
    },
    "put" to { context, args ->
        context.client.print(StyledString("Sending: $args"))
        context.client.sendCommand(args)
    },
)