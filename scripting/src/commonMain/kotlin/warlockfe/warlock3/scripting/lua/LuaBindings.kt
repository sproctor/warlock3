package warlockfe.warlock3.scripting.lua

import co.touchlab.kermit.Logger
import com.seanproctor.lua.LuaException
import com.seanproctor.lua.LuaState
import com.seanproctor.lua.LuaValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import warlockfe.warlock3.core.client.ClientGmcpEvent
import warlockfe.warlock3.core.client.ClientNavEvent
import warlockfe.warlock3.core.client.ClientPromptEvent
import warlockfe.warlock3.core.client.ClientTextEvent
import warlockfe.warlock3.core.client.ScriptableClient
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
 *
 * A script may also register handlers (`onGmcp`, `onLine`) to be told things as they happen;
 * [serveHandlers] then keeps the script alive after its last line, calling them on the script's
 * thread for each event, until the script is stopped or the connection closes.
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

    // Set once the script registers a handler; the script then outlives its last line.
    private var hasHandlers = false

    // The bootstrap's dispatchers, taken as handles so the globals can be removed.
    private lateinit var dispatchGmcp: LuaValue.Function
    private lateinit var dispatchLine: LuaValue.Function

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
            val seconds = args.firstOrNull()?.asNumber() ?: 1.0
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
            val level = args.firstOrNull()?.asNumber()?.toInt() ?: 0
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
        bind("setRoundTime") { args ->
            scriptable("setRoundTime").setRoundTime(endOf(args.firstOrNull()))
            emptyList()
        }
        bind("setCastTime") { args ->
            scriptable("setCastTime").setCastTime(endOf(args.firstOrNull()))
            emptyList()
        }
        bind("sendGmcp") { args ->
            val name =
                args.firstOrNull()?.asString()?.takeIf { it.isNotBlank() }
                    ?: throw IllegalArgumentException("sendGmcp needs the message name, like \"Core.Supports.Add\"")
            // A string goes as it is; a table is sent as JSON.
            val data =
                when (val value = args.getOrNull(1) ?: LuaValue.Nil) {
                    is LuaValue.Str -> value.value
                    LuaValue.Nil -> ""
                    else -> value.toJson().toString()
                }
            val target = scriptable("sendGmcp")
            blocking { target.sendGmcp(name, data) }
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
        lua.register("__keepAlive") {
            hasHandlers = true
            emptyList()
        }
        lua.eval(WARLOCK_BOOTSTRAP, "=(warlock)")
        dispatchGmcp = lua.getGlobal("__dispatchGmcp") as LuaValue.Function
        dispatchLine = lua.getGlobal("__dispatchLine") as LuaValue.Function
        lua.setGlobal("__dispatchGmcp", LuaValue.Nil)
        lua.setGlobal("__dispatchLine", LuaValue.Nil)
    }

    /**
     * Keeps the script alive to serve the handlers it registered, if it registered any: each line
     * of game text and each GMCP message is passed to the bootstrap's dispatchers on this thread.
     * Returns when the connection closes; stopping the script unwinds out of here instead.
     */
    fun serveHandlers() {
        if (!hasHandlers) return
        logger.d { "serving handlers" }
        // The script's thread may be busy in a handler while events arrive; the oldest go if it
        // falls that far behind, since a line handler is reading a live stream, not a log.
        val events = Channel<ScriptEvent>(capacity = 1024, onBufferOverflow = BufferOverflow.DROP_OLDEST)
        val feeder =
            instance.scope.launch {
                launch {
                    client.eventFlow.collect { event ->
                        when (event) {
                            is ClientTextEvent -> events.trySend(ScriptEvent.Line(event.text))
                            is ClientGmcpEvent -> events.trySend(ScriptEvent.Gmcp(event.name, event.data))
                            else -> Unit
                        }
                    }
                }
                // The script is about the connection: it ends when the connection does.
                client.disconnected.first { it }
                events.close()
            }
        try {
            while (true) {
                val event = blocking { events.receiveCatching() }.getOrNull() ?: break
                instance.checkStatus()
                dispatch(event)
            }
        } finally {
            feeder.cancel()
        }
    }

    private fun dispatch(event: ScriptEvent) {
        try {
            when (event) {
                is ScriptEvent.Line -> {
                    lua.call(dispatchLine, listOf(LuaValue.Str(event.text)))
                }

                is ScriptEvent.Gmcp -> {
                    val literal = jsonToLuaLiteral(event.data)?.let { LuaValue.Str(it) } ?: LuaValue.Nil
                    lua.call(dispatchGmcp, listOf(LuaValue.Str(event.name), literal))
                }
            }
        } catch (e: LuaException) {
            // A stop() or exit() inside a handler surfaces as a Lua error too; that one is not
            // reported, and ends the script.
            instance.checkStatus()
            blocking { client.print(StyledString("Script error: ${e.message}", style = WarlockStyle.Error)) }
        }
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

    private fun scriptable(function: String): ScriptableClient =
        client as? ScriptableClient
            ?: throw IllegalStateException("$function is only available on a telnet connection")

    /**
     * When something [seconds] long from now ends, in seconds since the epoch; null for nothing,
     * so `setRoundTime(0)` clears the bar.
     */
    private fun endOf(seconds: LuaValue?): Long? {
        val duration = seconds?.asNumber() ?: 0.0
        if (duration <= 0.0) return null
        return (client.getCurrentTime() + duration.seconds).epochSeconds
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

    private fun LuaValue.asNumber(): Double? =
        when (this) {
            is LuaValue.Integer -> value.toDouble()
            is LuaValue.Number -> value
            is LuaValue.Str -> value.toDoubleOrNull()
            else -> null
        }
}

/** What the handler-serving loop passes to the script. */
private sealed interface ScriptEvent {
    data class Line(
        val text: String,
    ) : ScriptEvent

    data class Gmcp(
        val name: String,
        val data: String,
    ) : ScriptEvent
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
 * parts of the stdlib scripts should not reach. The two `__dispatch` functions it defines are
 * taken by the host, which removes those globals itself.
 */
private val WARLOCK_BOOTSTRAP =
    """
    local getVariable = __getVariable
    local setVariable = __setVariable
    local matchWait = __matchWait
    local checkStatus = __checkStatus
    local keepAlive = __keepAlive
    __getVariable = nil
    __setVariable = nil
    __matchWait = nil
    __checkStatus = nil
    __keepAlive = nil

    -- Kept here so a script that reassigns these globals cannot break the dispatchers.
    local load, ipairs, pcall, error = load, ipairs, pcall, error
    local lower, sub, match = string.lower, string.sub, string.match
    local pack, unpack = table.pack, table.unpack

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

    -- Handlers a script registers to be told things as they happen. Registering one keeps the
    -- script running after its last line, until it is stopped or the connection closes.
    local gmcpHandlers = {}
    local lineHandlers = {}

    -- onGmcp("Char.Vitals", function(data, name) ... end): called with the message's JSON as a
    -- table (nil when it had none) for every message with that name or under that package, so a
    -- handler for "Char" hears Char.Vitals and Char.Items.List alike.
    function onGmcp(name, handler)
        gmcpHandlers[#gmcpHandlers + 1] = { name = lower(name), handler = handler }
        keepAlive()
    end

    -- onLine(function(line) ... end): called with each line of game text.
    -- onLine(pattern, function(...) ... end): called with the captures whenever a line matches
    -- the Lua pattern (with the whole match, when the pattern has no captures).
    function onLine(pattern, handler)
        if handler == nil then
            handler = pattern
            pattern = nil
        end
        lineHandlers[#lineHandlers + 1] = { pattern = pattern, handler = handler }
        keepAlive()
    end

    -- Every handler gets its turn even if an earlier one failed; the first failure is then
    -- raised so the host reports it.
    local function callAll(calls)
        local failure = nil
        for _, call in ipairs(calls) do
            local ok, err = pcall(unpack(call, 1, call.n))
            if not ok and failure == nil then
                failure = err
            end
        end
        if failure ~= nil then
            error(failure, 0)
        end
    end

    function __dispatchGmcp(name, literal)
        local data = nil
        if literal ~= nil then
            -- The literal is a table constructor of constants, so it gets no environment.
            data = load("return " .. literal, "=gmcp", "t", {})()
        end
        local lowered = lower(name)
        local calls = {}
        for _, entry in ipairs(gmcpHandlers) do
            if lowered == entry.name or sub(lowered, 1, #entry.name + 1) == entry.name .. "." then
                calls[#calls + 1] = pack(entry.handler, data, name)
            end
        end
        callAll(calls)
    end

    function __dispatchLine(line)
        local calls = {}
        for _, entry in ipairs(lineHandlers) do
            if entry.pattern == nil then
                calls[#calls + 1] = pack(entry.handler, line)
            else
                local captures = pack(match(line, entry.pattern))
                if captures[1] ~= nil then
                    calls[#calls + 1] = pack(entry.handler, unpack(captures, 1, captures.n))
                end
            end
        end
        callAll(calls)
    end

    -- Watchdog: surface stop/suspend even in scripts that never call a binding (the same job
    -- Rhino's instruction observer did). Installed via debug, which is then removed.
    debug.sethook(checkStatus, "", 10000)
    debug = nil

    -- The base library can read files; keep scripts inside the client sandbox.
    dofile = nil
    loadfile = nil
    """.trimIndent()
