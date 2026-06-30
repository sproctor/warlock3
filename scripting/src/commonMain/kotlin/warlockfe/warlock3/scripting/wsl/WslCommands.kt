package warlockfe.warlock3.scripting.wsl

import io.ktor.util.CaseInsensitiveMap
import kotlinx.coroutines.delay
import warlockfe.warlock3.core.text.StyleDefinition
import warlockfe.warlock3.core.text.WarlockColor
import warlockfe.warlock3.core.util.findArgumentBreak
import warlockfe.warlock3.core.util.firstArgument
import warlockfe.warlock3.core.util.parseArguments
import warlockfe.warlock3.core.util.splitFirstWord
import warlockfe.warlock3.core.util.toWarlockColor
import warlockfe.warlock3.scripting.util.ScriptLoggingLevel
import kotlin.random.Random
import kotlin.time.Duration.Companion.seconds

// The command table. Trivial one-liners stay inline; anything with real logic lives in a named
// `private suspend fun` below so each command is navigable and the table stays readable.
val wslCommands =
    CaseInsensitiveMap<suspend (WslContext, String) -> Unit>()
        .apply {
            putAll(
                mapOf(
                    "addtextlistener" to ::addTextListener,
                    "addtextlistenerre" to ::addTextListenerRe,
                    "addtohighlightnames" to ::addName,
                    "addtohighlightstrings" to ::addHighlight,
                    "cleartextlisteners" to { context, _ -> context.clearListeners() },
                    "counter" to ::counter,
                    "debug" to { context, args -> context.log(ScriptLoggingLevel.DEBUG, args) },
                    "debuglevel" to ::setDebugLevel,
                    "delay" to ::delayCommand,
                    "deletefromhighlightnames" to ::deleteName,
                    "deletefromhighlightstrings" to ::deleteHighlight,
                    "deletevariable" to { context, args -> context.deleteStoredVariable(args.firstArgument()) },
                    "echo" to { context, args -> context.echo(args) },
                    "error" to { context, args -> context.log(ScriptLoggingLevel.ERROR, args) },
                    "exit" to { context, _ -> context.stop() },
                    "gosub" to { context, argStr ->
                        val (label, args) = argStr.splitFirstWord()
                        if (label.isEmpty()) {
                            throw WslRuntimeException("GOSUB with no label")
                        }
                        context.gosub(label, args ?: "")
                    },
                    "goto" to { context, argStr ->
                        val (label, _) = argStr.splitFirstWord()
                        if (label.isBlank()) {
                            throw WslRuntimeException("GOTO with no label")
                        }
                        context.goto(label)
                    },
                    "info" to { context, args -> context.log(ScriptLoggingLevel.INFO, args) },
                    "local" to { context, args ->
                        val (name, value) = args.splitFirstWord()
                        if (name.isBlank()) {
                            throw WslRuntimeException("Invalid arguments to var")
                        }
                        context.setLocalVariable(name, WslString(value ?: ""))
                    },
                    "log" to ::logCommand,
                    "mapadd" to ::mapAdd,
                    "match" to { context, args ->
                        val (label, text) = args.splitFirstWord()
                        if (text?.isBlank() != false) {
                            throw WslRuntimeException("Blank text in match")
                        }
                        context.addMatch(TextMatch(label, text))
                    },
                    "matchre" to { context, args ->
                        val (label, text) = args.splitFirstWord()
                        val regex = text?.let { parseRegex(it) } ?: throw WslRuntimeException("Invalid regex in MatchRe")
                        context.addMatch(RegexMatch(label, regex))
                    },
                    "matchwait" to { context, args ->
                        val (timeout, rest) = args.splitFirstWord()
                        if (rest != null) {
                            throw WslRuntimeException("MatchWait can only take 1 argument")
                        }
                        context.matchWait(timeout.toFloatOrNull())
                    },
                    "move" to { context, args ->
                        context.putCommand(args)
                        context.waitForNav()
                    },
                    "nextroom" to { context, _ -> context.waitForNav() },
                    "pause" to ::pauseCommand,
                    "play" to { context, args -> context.playSound(args.firstArgument()) },
                    "print" to { context, args ->
                        val (stream, rest) = args.splitFirstWord()
                        rest?.let {
                            context.printToStream(stream, it)
                        }
                    },
                    "put" to { context, args -> context.putCommand(args) },
                    "random" to ::randomCommand,
                    "run" to { context, args -> context.runCommand(args) },
                    "removetextlistener" to { context, args ->
                        parseArguments(args).forEach { arg ->
                            context.removeListener(arg)
                        }
                    },
                    "return" to { context, _ -> context.gosubReturn() },
                    "save" to { context, args -> context.setScriptVariable("s", WslString(args)) },
                    "send" to { context, args -> context.sendCommand(args) },
                    "setarray" to ::setArray,
                    "setvariable" to { context, args ->
                        val (name, value) = args.splitFirstWord()
                        if (name.isBlank()) {
                            throw WslRuntimeException("Invalid arguments to SetVariable")
                        }
                        context.setStoredVariable(name, value ?: "")
                    },
                    "shift" to ::shiftCommand,
                    "timer" to ::timerCommand,
                    "typeahead" to { context, args ->
                        args.firstArgument().toIntOrNull()?.let { context.setTypeahead(it) }
                    },
                    "unsetlocal" to { context, args -> context.deleteLocalVariable(args.splitFirstWord().first) },
                    "unsetvar" to { context, args -> context.deleteScriptVariable(args.splitFirstWord().first) },
                    "var" to { context, args ->
                        val (name, value) = args.splitFirstWord()
                        if (name.isBlank()) {
                            throw WslRuntimeException("Invalid arguments to var")
                        }
                        context.setScriptVariable(name, WslString(value ?: ""))
                    },
                    "wait" to { context, _ -> context.waitForPrompt() },
                    "waitfor" to { context, args -> context.waitForText(args, ignoreCase = true) },
                    "waitforre" to { context, args ->
                        context.waitForRegex(
                            parseRegex(args) ?: throw WslRuntimeException("Invalid regex in WaitForRe"),
                        )
                    },
                ),
            )
            putAll(
                (1..9).map { "if_$it" to ifNCommand(it) },
            )
        }

private fun parseRegex(text: String): Regex? {
    val regex = Regex("/(.*)/(i)?")
    return regex.find(text)?.let { result ->
        try {
            Regex(
                pattern = result.groups[1]!!.value,
                options = if (result.groups[2] != null) setOf(RegexOption.IGNORE_CASE) else emptySet(),
            )
        } catch (_: Exception) {
            null
        }
    }
}

private fun ifNCommand(n: Int): suspend (WslContext, String) -> Unit =
    { context, args ->
        if (context.hasScriptVariable(n.toString())) {
            context.executeCommand(args)
        }
    }

private suspend fun addTextListener(
    context: WslContext,
    argString: String,
) {
    val (variableName, pattern) = argString.splitFirstWord()
    if (variableName.isEmpty()) {
        throw WslRuntimeException("Not enough arguments to AddTextListener")
    }
    context.addListener(variableName) {
        if (pattern == null || it.contains(other = pattern, ignoreCase = true)) {
            context.setScriptVariable(variableName, WslString(it))
        }
    }
}

private suspend fun addTextListenerRe(
    context: WslContext,
    argString: String,
) {
    val (variableName, pattern) = argString.splitFirstWord()
    if (variableName.isEmpty() || pattern == null) {
        throw WslRuntimeException("Not enough arguments to AddTextListener")
    }
    val regex =
        parseRegex(pattern) ?: throw WslRuntimeException("Invalid regex passed to AddTextListenerRe")

    context.addListener(variableName) {
        val match = regex.find(it)
        if (match != null) {
            context.setScriptVariable(variableName, WslString(match.value))
        }
    }
}

private suspend fun counter(
    context: WslContext,
    args: String,
) {
    val (operator, operandString) = args.splitFirstWord()
    val operand =
        operandString?.let {
            it.toDoubleOrNull() ?: throw WslRuntimeException("Counter operand must be a number")
        } ?: 1.0
    val current = context.lookupVariable("c")?.toNumber() ?: 0.0
    val result =
        when (operator.lowercase()) {
            "set" -> {
                operand
            }

            "add" -> {
                current + operand
            }

            "subtract" -> {
                current - operand
            }

            "multiply" -> {
                current * operand
            }

            "divide" -> {
                if (operand == 0.0) {
                    throw WslRuntimeException("Cannot divide by 0")
                }
                current / operand
            }

            else -> {
                throw WslRuntimeException("Unsupported counter operator")
            }
        }
    context.setScriptVariable("c", WslNumber(result))
}

private suspend fun setDebugLevel(
    context: WslContext,
    args: String,
) {
    val level = args.firstArgument()
    level.toIntOrNull()?.let {
        if (it !in 0..50) {
            throw WslRuntimeException("debug level must be between 0 and 50")
        }
        context.setLoggingLevel(it)
    } ?: ScriptLoggingLevel.fromString(level)?.let {
        context.setLoggingLevel(it.level)
    } ?: throw WslRuntimeException("Invalid logging level")
}

private suspend fun delayCommand(
    context: WslContext,
    args: String,
) {
    val duration = args.firstArgument().toDoubleOrNull() ?: 1.0
    delay(duration.seconds)
    context.scriptInstance.waitWhenSuspended()
}

private suspend fun pauseCommand(
    context: WslContext,
    args: String,
) {
    val duration = args.firstArgument().toDoubleOrNull() ?: 1.0
    context.waitForRoundTime()
    delay(duration.seconds)
    context.scriptInstance.waitWhenSuspended()
}

private suspend fun logCommand(
    context: WslContext,
    args: String,
) {
    val (levelStr, rest) = args.splitFirstWord()
    val level =
        levelStr.toIntOrNull()
            ?: ScriptLoggingLevel.fromString(levelStr)?.level
            ?: throw WslRuntimeException("Invalid logging level")
    context.log(level, rest ?: "")
}

private suspend fun mapAdd(
    context: WslContext,
    argString: String,
) {
    val (name, rest) = argString.splitFirstWord()
    val (key, value) =
        rest?.splitFirstWord()
            ?: throw WslRuntimeException("bad arguments passed to MapAdd")
    val variable =
        context.lookupVariable(name) ?: WslMap(emptyMap()).also { context.setScriptVariable(name, it) }
    if (variable.isMap()) {
        variable.setProperty(key, WslString(value ?: ""))
    } else {
        throw WslRuntimeException("Trying to set a property on a non-map variable")
    }
}

private suspend fun randomCommand(
    context: WslContext,
    args: String,
) {
    val argList = args.split(Regex("[ \t]+"))
    val min = argList[0].toIntOrNull() ?: throw WslRuntimeException("Invalid arguments to random")
    val max =
        argList.getOrNull(1)?.toIntOrNull()
            ?: throw WslRuntimeException("Invalid arguments to random")
    if (min >= max) {
        throw WslRuntimeException("Invalid arguments to random: min must be less than max")
    }
    context.setScriptVariable(
        name = "r",
        value = WslNumber(Random.nextInt(min, max).toDouble()),
    )
}

private suspend fun setArray(
    context: WslContext,
    args: String,
) {
    val (name, rest) = args.splitFirstWord()
    val variable =
        context.lookupVariable(name) ?: WslMap(emptyMap()).also { context.setScriptVariable(name, it) }
    if (!variable.isMap()) {
        throw WslRuntimeException("Trying to set a property on a non-map variable")
    }
    variable.setProperty("0", WslString(rest ?: ""))
    rest?.let { parseArguments(it) }?.forEachIndexed { index, arg ->
        variable.setProperty((index + 1).toString(), WslString(arg))
    }
}

private suspend fun shiftCommand(
    context: WslContext,
    args: String,
) {
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
    val allArgs = context.lookupVariable("0")?.toText() ?: ""
    val breakIndex = findArgumentBreak(allArgs)
    context.setScriptVariable(
        name = "0",
        value =
            WslString(
                if (breakIndex >= 0) {
                    allArgs.substring(breakIndex + 1)
                } else {
                    ""
                },
            ),
    )
}

private suspend fun timerCommand(
    context: WslContext,
    args: String,
) {
    when (args.firstArgument()) {
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
}

private suspend fun addHighlight(
    context: WslContext,
    argString: String,
) {
    val args = parseArguments(argString)
    var pattern: String? = null
    var textColor: WarlockColor? = null
    var backgroundColor: WarlockColor? = null
    var entireLine = false
    var matchPartialWord = true
    var ignoreCase = true
    var isRegex = false
    var global = false
    var sound: String? = null
    args.forEach { pair ->
        val parts = pair.split("=", limit = 2)
        if (parts.size != 2) {
            throw WslRuntimeException("Malformed arguments to AddToHighlightStrings")
        }
        val arg = parts[1]
        when (val name = parts[0].lowercase()) {
            "string" -> pattern = arg
            "forecolor" -> textColor = arg.toWarlockColor()
            "backcolor" -> backgroundColor = arg.toWarlockColor()
            "highlightentireline" -> entireLine = arg.toBoolean()
            "notonwordboundary", "matchpartialword" -> matchPartialWord = arg.toBoolean()
            "caseinsensitive", "ignorecase" -> ignoreCase = arg.toBoolean()
            "isregex" -> isRegex = arg.toBoolean()
            "global" -> global = arg.toBoolean()
            "sound" -> sound = arg
            else -> throw WslRuntimeException("Invalid argument \"$name\" to AddToHighlightStrings")
        }
    }
    if (pattern == null) {
        throw WslRuntimeException("\"string\" must be specified for AddToHighlightStrings")
    }
    context.addHighlight(
        pattern = pattern,
        style =
            StyleDefinition(
                textColor = textColor ?: WarlockColor.Unspecified,
                backgroundColor = backgroundColor ?: WarlockColor.Unspecified,
                entireLine = entireLine,
            ),
        ignoreCase = ignoreCase,
        matchPartialWord = matchPartialWord,
        isRegex = isRegex,
        global = global,
        sound = sound,
    )
}

private suspend fun addName(
    context: WslContext,
    argString: String,
) {
    val args = parseArguments(argString)
    var pattern: String? = null
    var textColor: WarlockColor? = null
    var backgroundColor: WarlockColor? = null
    var global = false
    var sound: String? = null
    args.forEach { pair ->
        val parts = pair.split("=", limit = 2)
        if (parts.size != 2) {
            throw WslRuntimeException("Malformed arguments to AddToHighlightNames")
        }
        val arg = parts[1]
        when (val name = parts[0].lowercase()) {
            "string" -> {
                pattern = arg
            }

            "forecolor" -> {
                textColor = arg.toWarlockColor()
            }

            "backcolor" -> {
                backgroundColor = arg.toWarlockColor()
            }

            "highlightentireline" -> {}

            "notonwordboundary", "matchpartialword" -> {}

            "caseinsensitive", "ignorecase" -> {}

            "isregex" -> {}

            "global" -> {
                global = arg.toBoolean()
            }

            "sound" -> {
                sound = arg
            }

            else -> {
                throw WslRuntimeException("Invalid argument \"$name\" to AddToHighlightNames")
            }
        }
    }
    if (pattern == null) {
        throw WslRuntimeException("\"string\" must be specified for AddToHighlightNames")
    }
    context.addName(
        pattern = pattern,
        textColor = textColor ?: WarlockColor.Unspecified,
        backgroundColor = backgroundColor ?: WarlockColor.Unspecified,
        global = global,
        sound = sound,
    )
}

private suspend fun deleteHighlight(
    context: WslContext,
    argString: String,
) {
    val args = parseArguments(argString)
    var pattern: String? = null
    args.forEach { pair ->
        val parts = pair.split("=", limit = 2)
        if (parts.size != 2) {
            throw WslRuntimeException("Malformed arguments to DeleteFromHighlightStrings")
        }
        val arg = parts[1]
        when (val name = parts[0].lowercase()) {
            "string" -> pattern = arg
            else -> throw WslRuntimeException("Invalid argument \"$name\" to DeleteFromHighlightStrings")
        }
    }
    if (pattern == null) {
        throw WslRuntimeException("\"string\" must be specified for DeleteFromHighlightStrings")
    }
    context.deleteHighlight(pattern = pattern)
}

private suspend fun deleteName(
    context: WslContext,
    argString: String,
) {
    val args = parseArguments(argString)
    var pattern: String? = null
    args.forEach { pair ->
        val parts = pair.split("=", limit = 2)
        if (parts.size != 2) {
            throw WslRuntimeException("Malformed arguments to DeleteFromHighlightNames")
        }
        val arg = parts[1]
        when (val name = parts[0].lowercase()) {
            "string" -> pattern = arg
            else -> throw WslRuntimeException("Invalid argument \"$name\" to DeleteFromHighlightNames")
        }
    }
    if (pattern == null) {
        throw WslRuntimeException("\"string\" must be specified for DeleteFromHighlightNames")
    }
    context.deleteName(pattern = pattern)
}
