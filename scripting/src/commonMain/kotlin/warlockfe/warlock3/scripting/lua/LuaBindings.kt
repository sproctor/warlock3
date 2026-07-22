package warlockfe.warlock3.scripting.lua

import co.touchlab.kermit.Logger
import com.seanproctor.lua.LuaState
import com.seanproctor.lua.LuaValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import warlockfe.warlock3.core.client.ClientNavEvent
import warlockfe.warlock3.core.client.ClientPromptEvent
import warlockfe.warlock3.core.client.ClientTextEvent
import warlockfe.warlock3.core.client.WarlockClient
import warlockfe.warlock3.core.prefs.repositories.VariableRepository
import warlockfe.warlock3.core.script.ScriptStatus
import warlockfe.warlock3.core.text.StyledString
import warlockfe.warlock3.core.text.WarlockStyle
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * Installs the Warlock script API into a [LuaState]: globals implemented as host functions
 * plus a small Lua bootstrap ([WARLOCK_BOOTSTRAP]) built on top of them.
 *
 * Host functions run synchronously on the script's thread; anything that needs to wait is
 * bridged to the suspend world with [blocking], whose waits are parented to the instance's
 * scope so that stopping the script aborts them.
 */
internal class LuaBindings(
    private val lua: LuaState,
    private val client: WarlockClient,
    private val instance: LuaScriptInstance,
    private val variableRepository: VariableRepository,
) {
    private val logger = Logger.withTag("LuaScript")

    // Same threshold the JS engine used: only log() calls at this level or above reach the client.
    private val loggingLevel = 30

    fun install() {
        bind("echo") { args ->
            val text = args.firstOrNull()?.asString() ?: ""
            blocking { client.print(StyledString(text, style = WarlockStyle.Echo)) }
            emptyList()
        }
        bind("put") { args ->
            val command = args.firstOrNull()?.asString()
            if (command != null) {
                blocking { putCommand(command) }
            }
            emptyList()
        }
        bind("move") { args ->
            val command = args.firstOrNull()?.asString()
            if (command != null) {
                blocking {
                    putCommand(command)
                    doWaitForNav()
                }
            }
            emptyList()
        }
        bind("pause") { args ->
            val seconds =
                when (val value = args.firstOrNull()) {
                    is LuaValue.Integer -> value.value.toDouble()
                    is LuaValue.Number -> value.value
                    else -> 1.0
                }
            blocking {
                withTimeoutOrNull(seconds.seconds) {
                    // Waking on any status change lets stop() and suspend() cut the pause short.
                    instance.statusFlow.first { it != ScriptStatus.Running }
                }
            }
            emptyList()
        }
        lua.register("exit") {
            // Marking the script stopped before unwinding keeps the runner from reporting the
            // unwind as a script error.
            instance.setStatus(ScriptStatus.Stopped)
            throw StopException()
        }
        bind("log") { args ->
            val level =
                when (val value = args.firstOrNull()) {
                    is LuaValue.Integer -> value.value.toInt()
                    is LuaValue.Number -> value.value.toInt()
                    else -> 0
                }
            val message = args.getOrNull(1)?.asString() ?: ""
            if (level >= loggingLevel) {
                blocking { client.debug(message) }
            }
            emptyList()
        }
        bind("waitForNav") {
            blocking { doWaitForNav() }
            emptyList()
        }
        bind("waitForPrompt") {
            blocking {
                logger.d { "waiting for next prompt" }
                client.eventFlow.first { it is ClientPromptEvent }
            }
            emptyList()
        }
        bind("waitForRoundTime") {
            blocking { doWaitForRoundTime() }
            emptyList()
        }
        bind("__getVariable") { args ->
            val name = args.firstOrNull()?.asString()
            val characterId = client.characterId.value?.lowercase()
            val value =
                if (name != null && characterId != null) {
                    variableRepository.getVariable(characterId, name)
                } else {
                    null
                }
            listOf(value?.let { LuaValue.Str(it) } ?: LuaValue.Nil)
        }
        bind("__setVariable") { args ->
            val name = args.firstOrNull()?.asString()
            val characterId = client.characterId.value?.lowercase()
            if (name != null && characterId != null) {
                when (val value = args.getOrNull(1) ?: LuaValue.Nil) {
                    is LuaValue.Nil -> {
                        blocking { variableRepository.delete(characterId, name) }
                    }

                    else -> {
                        value.asString()?.let { text ->
                            blocking { variableRepository.put(characterId, name, text) }
                        }
                    }
                }
            }
            emptyList()
        }
        bind("__matchWait") { args ->
            // Extract everything out of the tables before waiting: table handles are confined to
            // this thread, while the wait body may run on another.
            val patterns = args.firstOrNull() as? LuaValue.Table
            val regexFlags = args.getOrNull(1) as? LuaValue.Table
            val matchers =
                if (patterns == null) {
                    emptyList()
                } else {
                    (1..patterns.size).mapNotNull { index ->
                        val key = LuaValue.Integer(index.toLong())
                        val pattern = (patterns[key] as? LuaValue.Str)?.value ?: return@mapNotNull null
                        val regex = (regexFlags?.get(key) as? LuaValue.Bool)?.value == true
                        if (regex) {
                            RegexMatcher(index, Regex(pattern))
                        } else {
                            TextMatcher(index, pattern)
                        }
                    }
                }
            if (matchers.isEmpty()) {
                listOf(LuaValue.Nil)
            } else {
                var matched: Int? = null
                blocking {
                    client.eventFlow.first { event ->
                        if (instance.status == ScriptStatus.Running && event is ClientTextEvent) {
                            val match = matchers.firstOrNull { it.matches(event.text) }
                            if (match != null) {
                                matched = match.index
                            }
                            match != null
                        } else {
                            false
                        }
                    }
                }
                listOf(matched?.let { LuaValue.Integer(it.toLong()) } ?: LuaValue.Nil)
            }
        }
        lua.register("__checkStatus") {
            instance.checkStatus()
            emptyList()
        }
        lua.eval(WARLOCK_BOOTSTRAP, "=(warlock)")
    }

    /** Registers a host function that first surfaces any pending stop/suspend. */
    private fun bind(
        name: String,
        function: (List<LuaValue>) -> List<LuaValue>,
    ) {
        lua.register(name) { args ->
            instance.checkStatus()
            function(args)
        }
    }

    /**
     * Runs [block] on the instance scope, blocking the script thread until it finishes. Stopping
     * the script cancels the scope, which aborts the block and unwinds the script as a Lua error.
     */
    private fun <T> blocking(block: suspend CoroutineScope.() -> T): T {
        val result = runBlocking(instance.scope.coroutineContext) { block() }
        instance.checkStatus()
        return result
    }

    private suspend fun putCommand(command: String) {
        doWaitForRoundTime()
        logger.d { "sending command: $command" }
        client.sendCommand(command)
    }

    private suspend fun doWaitForNav() {
        logger.d { "waiting for next room" }
        client.eventFlow.first { it is ClientNavEvent }
    }

    private suspend fun doWaitForRoundTime() {
        logger.d { "waiting for round time" }
        while (true) {
            instance.awaitRunning()
            val roundEnd = client.roundTimeEnd.value?.let { Instant.fromEpochSeconds(it) + 1.seconds } ?: break
            val currentTime = client.getCurrentTime()
            if (roundEnd <= currentTime) {
                break
            }
            delay(roundEnd - currentTime)
        }
        logger.d { "done waiting for round time" }
    }

    private fun LuaValue.asString(): String? =
        when (this) {
            is LuaValue.Str -> value
            is LuaValue.Integer -> value.toString()
            is LuaValue.Number -> value.toString()
            is LuaValue.Bool -> value.toString()
            else -> null
        }
}

private sealed class Matcher(
    val index: Int,
) {
    abstract fun matches(line: String): Boolean
}

private class TextMatcher(
    index: Int,
    private val text: String,
) : Matcher(index) {
    override fun matches(line: String): Boolean = line.contains(text, ignoreCase = true)
}

private class RegexMatcher(
    index: Int,
    private val regex: Regex,
) : Matcher(index) {
    override fun matches(line: String): Boolean = regex.find(line) != null
}

/**
 * Lua-side layer of the script API. Runs after the host functions are registered and before the
 * script itself; it wraps the internal `__`-prefixed hooks and then removes them, along with the
 * parts of the stdlib scripts should not reach.
 */
private val WARLOCK_BOOTSTRAP =
    """
    local getVariable = __getVariable
    local setVariable = __setVariable
    local matchWait = __matchWait
    local checkStatus = __checkStatus
    __getVariable = nil
    __setVariable = nil
    __matchWait = nil
    __checkStatus = nil

    -- Reads and writes go straight to the character's stored variables.
    variables = setmetatable({}, {
        __index = function(_, key)
            return getVariable(key)
        end,
        __newindex = function(_, key, value)
            setVariable(key, value)
        end,
    })

    -- Send print() to the game window instead of stdout.
    function print(...)
        local parts = {}
        for i = 1, select("#", ...) do
            parts[i] = tostring((select(i, ...)))
        end
        echo(table.concat(parts, "\t"))
    end

    -- Mirrors the old JS MatchList: collect matches, then wait for the first line that hits one.
    -- addMatch does case-insensitive substring matching; addMatchRe takes a regular expression.
    function MatchList()
        local matches = {}
        local list = {}
        function list:addMatch(text, obj)
            matches[#matches + 1] = { pattern = text, regex = false, obj = obj }
        end
        function list:addMatchRe(pattern, obj)
            matches[#matches + 1] = { pattern = pattern, regex = true, obj = obj }
        end
        function list:wait()
            local patterns = {}
            local regexes = {}
            for i, match in ipairs(matches) do
                patterns[i] = match.pattern
                regexes[i] = match.regex
            end
            local index = matchWait(patterns, regexes)
            if index then
                return matches[index].obj
            end
        end
        return list
    end

    -- Watchdog: surface stop/suspend even in scripts that never call a binding (the same job
    -- Rhino's instruction observer did). Installed via debug, which is then removed.
    debug.sethook(checkStatus, "", 10000)
    debug = nil

    -- The base library can read files; keep scripts inside the client sandbox.
    dofile = nil
    loadfile = nil
    """.trimIndent()
