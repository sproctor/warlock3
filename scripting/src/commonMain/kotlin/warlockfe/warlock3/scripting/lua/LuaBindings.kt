package warlockfe.warlock3.scripting.lua

import co.touchlab.kermit.Logger
import com.seanproctor.lua.LuaException
import com.seanproctor.lua.LuaState
import com.seanproctor.lua.LuaValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.readString
import kotlinx.io.writeString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import warlockfe.warlock3.core.client.ClientGmcpEvent
import warlockfe.warlock3.core.client.ClientNavEvent
import warlockfe.warlock3.core.client.ClientPromptEvent
import warlockfe.warlock3.core.client.ClientTextEvent
import warlockfe.warlock3.core.client.HandBlock
import warlockfe.warlock3.core.client.ScriptableClient
import warlockfe.warlock3.core.client.WarlockAction
import warlockfe.warlock3.core.client.WarlockClient
import warlockfe.warlock3.core.prefs.repositories.VariableRepository
import warlockfe.warlock3.core.script.ScriptInstance
import warlockfe.warlock3.core.script.ScriptStatus
import warlockfe.warlock3.core.text.StyledString
import warlockfe.warlock3.core.text.WarlockStyle
import warlockfe.warlock3.core.text.isSpecified
import warlockfe.warlock3.core.util.toWarlockColor
import kotlin.concurrent.Volatile
import kotlin.math.roundToInt
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlin.time.TimeSource

/**
 * Installs the Warlock script API into a [LuaState]: globals implemented as host functions
 * plus a small Lua bootstrap ([WARLOCK_BOOTSTRAP]) built on top of them. `docs/lua-api.md`
 * describes the API from the script's side.
 *
 * Host functions run synchronously on the script's thread; anything that needs to wait is
 * bridged to the suspend world with [blocking], whose waits are parented to the instance's
 * scope so that stopping the script aborts them.
 *
 * From the moment the bindings are installed, every line of game text is kept for the script
 * ([lines]), so that `waitFor` after a `put` cannot miss a reply that came back at once. A script
 * may also register handlers (`onGmcp`, `onLine`) to be told things as they happen; they are
 * called whenever the script is waiting in one of the API's waits, and [serveHandlers] keeps the
 * script alive after its last line to go on calling them, until the script is stopped or the
 * connection closes.
 */
internal class LuaBindings(
    private val lua: LuaState,
    private val client: WarlockClient,
    private val instance: LuaScriptInstance,
    private val variableRepository: VariableRepository,
    private val host: LuaHost,
) {
    private val logger = Logger.withTag("LuaScript")

    // Same threshold the JS engine used: only log() calls at this level or above reach the client.
    private val loggingLevel = 30

    private val started = TimeSource.Monotonic.markNow()

    private val regex = LuaRegex()

    // Every line of game text since the script started, for waitFor and nextLine; the oldest go
    // when a script leaves it unread for long.
    private val lines = Channel<ScriptEvent.Line>(capacity = 1024, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    // Made when the script registers its first handler, from which moment events are gathered
    // for it, so that nothing that arrives while the rest of the script runs is lost. The script's
    // thread may be busy while events arrive; the oldest go if it falls that far behind, since a
    // line handler is reading a live stream, not a log.
    @Volatile
    private var handlerEvents: Channel<ScriptEvent>? = null
    private var handlersClosed = false
    private var feeder: Job? = null

    // The protocol lines for protocolLines(), tapped on its first call. Lines that do not fit are
    // lost, and the next call says so.
    private var protocol: Channel<String>? = null
    private var removeTap: (() -> Unit)? = null

    @Volatile
    private var protocolLost = false

    // Set while a handler runs from inside one of the script's waits. A handler that waits then
    // would have the script waiting in two places at once, so it is refused.
    private var dispatchingInWait = false

    // The bootstrap's dispatchers, taken as handles so the globals can be removed.
    private lateinit var dispatchGmcp: LuaValue.Function
    private lateinit var dispatchLine: LuaValue.Function
    private lateinit var dispatchInput: LuaValue.Function

    // Where a line from /put goes: to waitForInput while the script is in it, and to the onInput
    // handlers once there are any. Called on whatever thread ran the /put.
    @Volatile
    private var inputWaiter: Channel<String>? = null

    @Volatile
    private var inputHandled = false

    fun install() {
        startFeeding()
        // __echo(text1, input1, text2, input2, ...): one line, each part plain where its input is
        // false, or a link that sends the input to this script with /put.
        bind("__echo") { args ->
            val parts =
                args.chunked(2).map { part ->
                    val text = part[0].asString() ?: ""
                    val input = (part.getOrNull(1) as? LuaValue.Str)?.value
                    if (input == null) {
                        StyledString(text, style = WarlockStyle.Echo)
                    } else {
                        val action = WarlockAction.SendCommand("/put ${instance.id} $input")
                        StyledString(text, listOf(WarlockStyle.Echo, WarlockStyle.Link(action)))
                    }
                }
            val line = parts.reduceOrNull { line, part -> line + part } ?: StyledString("", style = WarlockStyle.Echo)
            blocking { client.print(line) }
            emptyList()
        }
        bind("waitForInput") { args ->
            val waiter = Channel<String>(Channel.UNLIMITED)
            inputWaiter = waiter
            val text =
                try {
                    waitServing(timeoutArg(args.firstOrNull())) { waiter.receive() }
                } finally {
                    inputWaiter = null
                }
            listOf(text?.let { LuaValue.Str(it) } ?: LuaValue.Nil)
        }
        bind("put") { args ->
            val command = args.firstOrNull()?.asString()
            if (command != null) {
                waitServing(null) { doWaitForRoundTime() }
                blocking { sendCommand(command) }
            }
            emptyList()
        }
        bind("move") { args ->
            val command = args.firstOrNull()?.asString()
            if (command != null) {
                waitServing(null) { doWaitForRoundTime() }
                // Listening for the move before sending, so a fast one is not missed.
                val nav = instance.scope.async(start = CoroutineStart.UNDISPATCHED) { client.eventFlow.first { it is ClientNavEvent } }
                blocking { sendCommand(command) }
                waitServing(timeoutArg(args.getOrNull(1))) { nav.await() }.also { nav.cancel() }
                    ?: return@bind listOf(LuaValue.Nil)
            }
            listOf(LuaValue.Bool(true))
        }
        bind("pause") { args ->
            val seconds = args.firstOrNull()?.asNumber() ?: 1.0
            // Waking on any status change lets stop() and suspend() cut the pause short.
            waitServing(seconds.seconds) { instance.statusFlow.first { it != ScriptStatus.Running } }
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
        bind("waitForNav") { args ->
            logger.d { "waiting for next room" }
            found(waitServing(timeoutArg(args.firstOrNull())) { client.eventFlow.first { it is ClientNavEvent } })
        }
        bind("waitForPrompt") { args ->
            logger.d { "waiting for next prompt" }
            found(waitServing(timeoutArg(args.firstOrNull())) { client.eventFlow.first { it is ClientPromptEvent } })
        }
        bind("waitForRoundTime") {
            waitServing(null) { doWaitForRoundTime() }
            emptyList()
        }
        bind("waitForCastTime") {
            waitServing(null) { doWaitForCastTime() }
            emptyList()
        }
        bind("roundtime") { listOf(LuaValue.Number(secondsUntil(client.roundTimeEnd.value))) }
        bind("casttime") { listOf(LuaValue.Number(secondsUntil(client.castTimeEnd.value))) }
        bind("now") { listOf(LuaValue.Number(started.elapsedNow().inWholeMilliseconds / 1000.0)) }
        bind("gameTime") { listOf(LuaValue.Number(client.getCurrentTime().toEpochMilliseconds() / 1000.0)) }
        bind("setRoundTime") { args ->
            scriptable("setRoundTime").setRoundTime(endOf(args.firstOrNull()))
            emptyList()
        }
        bind("setCastTime") { args ->
            scriptable("setCastTime").setCastTime(endOf(args.firstOrNull()))
            emptyList()
        }
        bind("setLeftHand") { args ->
            scriptable("setLeftHand").setLeftHand(args.firstOrNull()?.asString()?.takeIf { it.isNotEmpty() })
            emptyList()
        }
        bind("setRightHand") { args ->
            scriptable("setRightHand").setRightHand(args.firstOrNull()?.asString()?.takeIf { it.isNotEmpty() })
            emptyList()
        }
        bind("setSpellHand") { args ->
            scriptable("setSpellHand").setSpellHand(args.firstOrNull()?.asString()?.takeIf { it.isNotEmpty() })
            emptyList()
        }
        bind("getHands") {
            listOf(client.leftHand.value, client.rightHand.value, client.spellHand.value).map { it?.let(LuaValue::Str) ?: LuaValue.Nil }
        }
        bind("showHands") { args ->
            scriptable("showHands").showHands((args.firstOrNull() as? LuaValue.Bool)?.value ?: true)
            emptyList()
        }
        bind("showBlock") { args ->
            val id =
                args.firstOrNull()?.asString()?.takeIf { it.isNotBlank() }
                    ?: throw IllegalArgumentException("showBlock needs the block's id")
            scriptable("showBlock").showBlock(id, (args.getOrNull(1) as? LuaValue.Bool)?.value ?: true)
            emptyList()
        }
        bind("setBlock") { args ->
            val id =
                args.firstOrNull()?.asString()?.takeIf { it.isNotBlank() }
                    ?: throw IllegalArgumentException("setBlock needs the block's id")
            if (id in
                HandBlock.HAND_IDS
            ) {
                throw IllegalArgumentException("setBlock: $id is a hand; set it with setLeftHand, setRightHand or setSpellHand")
            }
            val label = args.getOrNull(1)?.asString()
            val value = args.getOrNull(2)?.asString()?.takeIf { it.isNotEmpty() }
            scriptable("setBlock").setBlock(id, label, value)
            emptyList()
        }
        bind("removeBlock") { args ->
            val id =
                args.firstOrNull()?.asString()?.takeIf { it.isNotBlank() }
                    ?: throw IllegalArgumentException("removeBlock needs the block's id")
            scriptable("removeBlock").removeBlock(id)
            emptyList()
        }
        bind("arrangeBlocks") { args ->
            scriptable("arrangeBlocks").arrangeBlocks(args.mapNotNull { it.asString() })
            emptyList()
        }
        bind("setVital") { args ->
            val id =
                args.firstOrNull()?.asString()?.takeIf { it.isNotBlank() }
                    ?: throw IllegalArgumentException("setVital needs the bar's id, like \"health\"")
            val percent = args.getOrNull(1)?.asNumber() ?: throw IllegalArgumentException("setVital needs how full the bar is, 0 to 100")
            val text = args.getOrNull(2)?.asString()
            val target = scriptable("setVital")
            blocking { target.setVital(id, percent.roundToInt(), text) }
            emptyList()
        }
        bind("clearVitals") {
            val target = scriptable("clearVitals")
            blocking { target.clearVitals() }
            emptyList()
        }
        bind("flashBackground") { args ->
            val colorText =
                args.firstOrNull()?.asString() ?: throw IllegalArgumentException("flashBackground needs a colour, like \"#400000\"")
            val color =
                colorText.toWarlockColor()?.takeIf { it.isSpecified() }
                    ?: throw IllegalArgumentException("flashBackground: \"$colorText\" is not a colour; use \"#rrggbb\"")
            val total =
                args.getOrNull(1)?.asNumber()
                    ?: throw IllegalArgumentException("flashBackground needs the time to show the colour for, in seconds")
            val fadeIn = args.getOrNull(2)?.asNumber() ?: 0.0
            val fadeOut = args.getOrNull(3)?.asNumber() ?: 0.0
            val window = args.getOrNull(4)?.asString() ?: "main"
            val target = scriptable("flashBackground")
            when {
                total <= 0.0 || fadeIn < 0.0 || fadeOut < 0.0 -> {
                    Unit
                }

                fadeIn + fadeOut > total -> {
                    // Reported, but not fatal: the script goes on without the flash.
                    blocking {
                        client.print(
                            StyledString(
                                "Script error: flashBackground: the fades ($fadeIn + $fadeOut seconds) are longer than the whole flash ($total seconds)",
                                style = WarlockStyle.Error,
                            ),
                        )
                    }
                }

                else -> {
                    target.flashBackground(window, color, total.seconds, fadeIn.seconds, fadeOut.seconds)
                }
            }
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
                waitServing(null) {
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
        installLines()
        installProtocol()
        installRegex()
        installScripts()
        installStorage()
        bind("__readModule") { args -> readModule(args.firstOrNull()?.asString()) }
        lua.register("__checkStatus") {
            instance.checkStatus()
            emptyList()
        }
        lua.register("__keepAlive") {
            startGathering()
            emptyList()
        }
        lua.register("__listenForInput") {
            startGathering()
            inputHandled = true
            emptyList()
        }
        instance.inputListener = ::takeInput
        lua.eval(WARLOCK_BOOTSTRAP, "=(warlock)")
        dispatchGmcp = lua.getGlobal("__dispatchGmcp") as LuaValue.Function
        dispatchLine = lua.getGlobal("__dispatchLine") as LuaValue.Function
        dispatchInput = lua.getGlobal("__dispatchInput") as LuaValue.Function
        lua.setGlobal("__dispatchGmcp", LuaValue.Nil)
        lua.setGlobal("__dispatchLine", LuaValue.Nil)
        lua.setGlobal("__dispatchInput", LuaValue.Nil)
    }

    /**
     * Starts listening to the connection before the script's first line runs: the subscription is
     * made on this thread, undispatched, so it is in place by the time this returns.
     */
    private fun startFeeding() {
        feeder =
            instance.scope.launch(start = CoroutineStart.UNDISPATCHED) {
                launch(start = CoroutineStart.UNDISPATCHED) {
                    client.eventFlow.collect { event ->
                        when (event) {
                            is ClientTextEvent -> {
                                val line = ScriptEvent.Line(event.text)
                                lines.trySend(line)
                                handlerEvents?.trySend(line)
                            }

                            is ClientGmcpEvent -> {
                                handlerEvents?.trySend(ScriptEvent.Gmcp(event.name, event.data))
                            }

                            else -> {
                                Unit
                            }
                        }
                    }
                }
                // The script is about the connection: it ends when the connection does.
                client.disconnected.first { it }
                lines.close()
                handlerEvents?.close()
            }
    }

    private fun startGathering() {
        if (handlerEvents != null) return
        val events = Channel<ScriptEvent>(capacity = 1024, onBufferOverflow = BufferOverflow.DROP_OLDEST)
        handlerEvents = events
        if (client.disconnected.value) events.close()
    }

    /**
     * Keeps the script alive to serve the handlers it registered, if it registered any: each line
     * of game text and each GMCP message gathered since the first was registered is passed to the
     * bootstrap's dispatchers on this thread. Returns when the connection closes; stopping the
     * script unwinds out of here instead.
     */
    fun serveHandlers() {
        val events = handlerEvents
        try {
            if (events == null || handlersClosed) return
            logger.d { "serving handlers" }
            while (true) {
                val event = blocking { events.receiveCatching() }.getOrNull() ?: break
                instance.checkStatus()
                dispatch(event)
            }
        } finally {
            feeder?.cancel()
        }
    }

    private fun takeInput(text: String): Boolean {
        var taken = inputWaiter?.trySend(text)?.isSuccess == true
        if (inputHandled) {
            taken = handlerEvents?.trySend(ScriptEvent.Input(text))?.isSuccess == true || taken
        }
        return taken
    }

    /** Lets go of the connection when the script ends. */
    fun close() {
        instance.inputListener = null
        removeTap?.invoke()
        removeTap = null
        feeder?.cancel()
    }

    private fun dispatch(event: ScriptEvent) {
        try {
            when (event) {
                is ScriptEvent.Line -> {
                    lua.call(dispatchLine, listOf(LuaValue.Str(event.text)))
                }

                is ScriptEvent.Input -> {
                    lua.call(dispatchInput, listOf(LuaValue.Str(event.text)))
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

    /**
     * Waits on the script's thread for [body], at most [timeout] (no limit when null), and returns
     * what it produced, or null when the time ran out. Meanwhile the script's handlers are served
     * here, on this thread, as their events arrive; [body] itself runs on the instance's scope.
     */
    private fun <T : Any> waitServing(
        timeout: Duration?,
        body: suspend () -> T,
    ): T? {
        if (dispatchingInWait) {
            throw IllegalStateException("a handler cannot wait while the script is already waiting; do the waiting in the script")
        }
        val deadline = timeout?.let { TimeSource.Monotonic.markNow() + it }
        val result = instance.scope.async { body() }
        try {
            while (true) {
                val events = handlerEvents?.takeUnless { handlersClosed }
                val outcome =
                    blocking {
                        val remaining = deadline?.let { -it.elapsedNow() }
                        val choose: suspend () -> WaitOutcome<T> = {
                            select {
                                result.onAwait { WaitOutcome.Done(it) }
                                events?.onReceiveCatching { received ->
                                    received.getOrNull()?.let { WaitOutcome.Event(it) } ?: WaitOutcome.Closed
                                }
                            }
                        }
                        when {
                            remaining == null -> choose()
                            remaining <= Duration.ZERO -> WaitOutcome.TimedOut
                            else -> withTimeoutOrNull(remaining) { choose() } ?: WaitOutcome.TimedOut
                        }
                    }
                when (outcome) {
                    is WaitOutcome.Done -> {
                        return outcome.value
                    }

                    WaitOutcome.TimedOut -> {
                        return null
                    }

                    WaitOutcome.Closed -> {
                        handlersClosed = true
                    }

                    is WaitOutcome.Event -> {
                        dispatchingInWait = true
                        try {
                            dispatch(outcome.event)
                        } finally {
                            dispatchingInWait = false
                        }
                    }
                }
            }
        } finally {
            result.cancel()
        }
    }

    private fun installLines() {
        // __waitLines(timeout, pattern1, flags1, pattern2, flags2, ...): the first buffered line
        // matching any of the regexes (any line at all, given none) -> line, index, captures.
        bind("__waitLines") { args ->
            val timeout = timeoutArg(args.firstOrNull())
            val matchers =
                args.drop(1).chunked(2).map { pair ->
                    regex.compile(pair[0].asString() ?: "", pair.getOrNull(1)?.asString())
                }

            suspend fun nextMatch(): LineMatch {
                while (true) {
                    instance.awaitRunning()
                    val line = lines.receiveCatching().getOrNull() ?: return LineMatch.Closed
                    if (matchers.isEmpty()) return LineMatch.Found(line, 0, null)
                    matchers.forEachIndexed { index, matcher ->
                        matcher.find(line.text)?.let { return LineMatch.Found(line, index + 1, it) }
                    }
                }
            }
            val found = waitServing(timeout) { nextMatch() }
            when (found) {
                is LineMatch.Found -> {
                    listOf(
                        LuaValue.Str(found.line.text),
                        if (found.index > 0) LuaValue.Integer(found.index.toLong()) else LuaValue.Nil,
                        found.match?.let { LuaValue.Str(luaLiteral(regex.captures(it))) } ?: LuaValue.Nil,
                    )
                }

                else -> {
                    listOf(LuaValue.Nil)
                }
            }
        }
        bind("__clearLines") {
            while (lines.tryReceive().isSuccess) {
                // Draining.
            }
            emptyList()
        }
        // __capture(command, timeout, startPattern, startFlags, endPattern, endFlags) -> lines, complete
        bind("__capture") { args ->
            val command = args.firstOrNull()?.asString() ?: throw IllegalArgumentException("capture needs a command")
            val timeout = timeoutArg(args.getOrNull(1))
            val start = args.getOrNull(2)?.asString()?.let { regex.compile(it, args.getOrNull(3)?.asString()) }
            val end = args.getOrNull(4)?.asString()?.let { regex.compile(it, args.getOrNull(5)?.asString()) }
            waitServing(null) { doWaitForRoundTime() }
            val capture = CaptureInterceptor(start, end)
            val remove = client.addLineInterceptor(capture)
            val complete =
                try {
                    blocking { client.sendQuietCommand(command) }
                    waitServing(timeout) { capture.done.await() } != null
                } finally {
                    remove()
                }
            listOf(LuaValue.Str(luaLiteral(capture.lines)), LuaValue.Bool(complete))
        }
    }

    private fun installProtocol() {
        bind("status") {
            listOf(LuaValue.Str(luaLiteral(client.indicators.value.associateWith { true })))
        }
        // __protocolLines() -> the protocol lines that arrived since the last call (the first call
        // starts with the connection's recent history), and whether any were lost meanwhile.
        bind("__protocolLines") {
            var history = emptyList<String>()
            val buffer =
                protocol ?: Channel<String>(capacity = PROTOCOL_BUFFER).also { channel ->
                    protocol = channel
                    val tap = client.tapProtocol { line -> if (channel.trySend(line).isFailure) protocolLost = true }
                    history = tap.history
                    removeTap = tap.remove
                }
            val lines = history + generateSequence { buffer.tryReceive().getOrNull() }
            val lost = protocolLost
            protocolLost = false
            listOf(LuaValue.Str(luaLiteral(lines)), LuaValue.Bool(lost))
        }
    }

    // __re(operation, pattern, flags, text, extra)
    private fun installRegex() {
        bind("__re") { args ->
            val operation = args.firstOrNull()?.asString()
            val pattern = args.getOrNull(1)?.asString() ?: ""
            val flags = args.getOrNull(2)?.asString()
            val text = args.getOrNull(3)?.asString() ?: ""
            if (operation == "escape") return@bind listOf(LuaValue.Str(Regex.escape(pattern)))
            val compiled = regex.compile(pattern, flags)
            when (operation) {
                "check" -> {
                    emptyList()
                }

                "test" -> {
                    listOf(LuaValue.Bool(compiled.containsMatchIn(text)))
                }

                "match" -> {
                    val match = compiled.find(text) ?: return@bind listOf(LuaValue.Nil)
                    val captures = regex.captures(match)
                    listOf(LuaValue.Str(luaLiteral(captures)), LuaValue.Integer(captures.size.toLong()))
                }

                "find" -> {
                    val init = args.getOrNull(4)?.asNumber()?.toInt() ?: 1
                    val from = regex.charIndex(text, init.coerceAtLeast(1))
                    if (from > text.length) return@bind listOf(LuaValue.Nil)
                    val match = compiled.find(text, from) ?: return@bind listOf(LuaValue.Nil)
                    listOf(LuaValue.Str(luaLiteral(regex.describe(match, text))))
                }

                "all" -> {
                    listOf(LuaValue.Str(luaLiteral(compiled.findAll(text).map { regex.describe(it, text) }.toList())))
                }

                "replace" -> {
                    val replacement = args.getOrNull(4)?.asString() ?: ""
                    val count = compiled.findAll(text).count()
                    val replaced =
                        try {
                            compiled.replace(text, replacement)
                        } catch (e: Exception) {
                            throw IllegalArgumentException("bad replacement \"$replacement\": ${e.message}")
                        }
                    listOf(LuaValue.Str(replaced), LuaValue.Integer(count.toLong()))
                }

                "split" -> {
                    listOf(LuaValue.Str(luaLiteral(compiled.split(text))))
                }

                else -> {
                    throw IllegalArgumentException("no regex operation called $operation")
                }
            }
        }
    }

    // __scripts(operation, argument): the other scripts on this connection.
    private fun installScripts() {
        bind("__scripts") { args ->
            val operation = args.firstOrNull()?.asString()
            val argument = args.getOrNull(1)?.asString()?.trim()

            fun named(): ScriptInstance? {
                val name = argument?.takeIf { it.isNotEmpty() } ?: throw IllegalArgumentException("scripts.$operation needs a script name")
                return host.scriptManager.runningScripts.value.values
                    .map { it.instance }
                    .firstOrNull { it.name.equals(name, ignoreCase = true) && it.id != instance.id }
            }
            when (operation) {
                "start", "run" -> {
                    val command =
                        argument?.takeIf { it.isNotEmpty() } ?: throw IllegalArgumentException("scripts.$operation needs a script to run")
                    val started = blocking { host.scriptManager.startScript(client, command, host.commandHandler) }
                    if (started == null) {
                        listOf(LuaValue.Bool(false))
                    } else {
                        if (operation == "run") {
                            waitServing(null) { host.scriptManager.runningScripts.first { started.id !in it } }
                        }
                        listOf(LuaValue.Bool(true))
                    }
                }

                "running" -> {
                    listOf(LuaValue.Bool(named() != null))
                }

                "stop" -> {
                    val script = named()
                    script?.stop()
                    listOf(LuaValue.Bool(script != null))
                }

                "pause" -> {
                    val script = named()
                    script?.suspend()
                    listOf(LuaValue.Bool(script != null))
                }

                "resume" -> {
                    val script = named()
                    script?.resume()
                    listOf(LuaValue.Bool(script != null))
                }

                "list" -> {
                    val names =
                        host.scriptManager.runningScripts.value.values
                            .map { it.instance.name }
                    listOf(LuaValue.Str(luaLiteral(names)))
                }

                else -> {
                    throw IllegalArgumentException("no scripts operation called $operation")
                }
            }
        }
    }

    // __storage(operation, value): the script's own data, kept per character as JSON.
    private fun installStorage() {
        bind("__storage") { args ->
            val file =
                storageFile()
                    ?: throw IllegalStateException("storage is not available here")
            val fileSystem = host.fileSystem
            when (val operation = args.firstOrNull()?.asString()) {
                "load" -> {
                    if (!fileSystem.exists(file)) return@bind listOf(LuaValue.Nil)
                    val json = fileSystem.source(file).buffered().use { it.readString() }
                    listOf(jsonToLuaLiteral(json)?.let { LuaValue.Str(it) } ?: LuaValue.Nil)
                }

                "save" -> {
                    val value = args.getOrNull(1) ?: LuaValue.Nil
                    val json = prettyJson.encodeToString(JsonElement.serializer(), value.toJson())
                    file.parent?.let { fileSystem.createDirectories(it) }
                    fileSystem.sink(file).buffered().use { it.writeString(json) }
                    emptyList()
                }

                else -> {
                    throw IllegalArgumentException("no storage operation called $operation")
                }
            }
        }
    }

    // <storage dir>/<character>/<script>.json
    private fun storageFile(): Path? {
        val dir = host.storageDir ?: return null
        val character = fileNamePart(client.characterId.value ?: "global")
        val script = fileNamePart(instance.name.substringBeforeLast(".lua"))
        return Path(dir, character, "$script.json")
    }

    private fun fileNamePart(text: String): String = text.lowercase().replace(Regex("[^a-z0-9_-]"), "_")

    /**
     * The source of the library [name] names, for `require`: `lib/<name>.lua` in the first script
     * folder that has it, dots in the name standing for folders. The name is checked first, so a
     * library cannot be read from outside a `lib` folder.
     */
    private fun readModule(name: String?): List<LuaValue> {
        if (name == null || !moduleName.matches(name)) {
            throw IllegalArgumentException(
                "require: \"$name\" is not a library name; use letters, digits, _ and -, with dots between folders",
            )
        }
        val relative = name.replace('.', '/') + ".lua"
        val characterId = client.characterId.value ?: ""
        val dirs = blocking { host.scriptDirs(characterId) }
        for (dir in dirs) {
            val file = Path(dir, "lib", relative)
            if (host.fileSystem.exists(file)) {
                val source =
                    host.fileSystem
                        .source(file)
                        .buffered()
                        .use { it.readString() }
                return listOf(LuaValue.Str(source), LuaValue.Str("@lib/$relative"))
            }
        }
        return listOf(LuaValue.Nil)
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

    /** A wait's timeout argument, in seconds; none (wait for ever) when absent or not positive. */
    private fun timeoutArg(value: LuaValue?): Duration? = value?.asNumber()?.takeIf { it > 0.0 }?.seconds

    private fun found(value: Any?): List<LuaValue> = listOf(if (value != null) LuaValue.Bool(true) else LuaValue.Nil)

    /**
     * When something [seconds] long from now ends, in seconds since the epoch; null for nothing,
     * so `setRoundTime(0)` clears the bar.
     */
    private fun endOf(seconds: LuaValue?): Long? {
        val duration = seconds?.asNumber() ?: 0.0
        if (duration <= 0.0) return null
        return (client.getCurrentTime() + duration.seconds).epochSeconds
    }

    /** Seconds from now until [endSeconds] (since the epoch), and none when that has passed. */
    private fun secondsUntil(endSeconds: Long?): Double {
        if (endSeconds == null) return 0.0
        val left = Instant.fromEpochSeconds(endSeconds) - client.getCurrentTime()
        return (left.inWholeMilliseconds / 1000.0).coerceAtLeast(0.0)
    }

    private suspend fun sendCommand(command: String) {
        logger.d { "sending command: $command" }
        client.sendCommand(command)
    }

    private suspend fun doWaitForRoundTime() = waitUntilPassed { client.roundTimeEnd.value }

    private suspend fun doWaitForCastTime() = waitUntilPassed { client.castTimeEnd.value }

    // Waits out a timer given in seconds since the epoch, with a second's margin, as the game's
    // clock only counts whole seconds.
    private suspend fun waitUntilPassed(end: () -> Long?) {
        while (true) {
            instance.awaitRunning()
            val timerEnd = end()?.let { Instant.fromEpochSeconds(it) + 1.seconds } ?: break
            val currentTime = client.getCurrentTime()
            if (timerEnd <= currentTime) {
                break
            }
            delay(timerEnd - currentTime)
        }
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

// Lines of protocol kept for a script between its calls to protocolLines().
private const val PROTOCOL_BUFFER = 20_000

private val moduleName = Regex("""^[A-Za-z0-9_-]+(\.[A-Za-z0-9_-]+)*$""")

private val prettyJson = Json { prettyPrint = true }

/** What the handler-serving loop passes to the script. */
private sealed interface ScriptEvent {
    data class Line(
        val text: String,
    ) : ScriptEvent

    data class Gmcp(
        val name: String,
        val data: String,
    ) : ScriptEvent

    data class Input(
        val text: String,
    ) : ScriptEvent
}

private sealed interface WaitOutcome<out T> {
    data class Done<T>(
        val value: T,
    ) : WaitOutcome<T>

    data class Event(
        val event: ScriptEvent,
    ) : WaitOutcome<Nothing>

    data object TimedOut : WaitOutcome<Nothing>

    data object Closed : WaitOutcome<Nothing>
}

private sealed interface LineMatch {
    class Found(
        val line: ScriptEvent.Line,
        val index: Int,
        val match: MatchResult?,
    ) : LineMatch

    data object Closed : LineMatch
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
    local listenForInput = __listenForInput
    local echoHost = __echo
    local protocolHost = __protocolLines
    local statusHost = status
    local waitLines = __waitLines
    local clearLinesHost = __clearLines
    local captureHost = __capture
    local reHost = __re
    local readModule = __readModule
    local scriptsHost = __scripts
    local storageHost = __storage
    __getVariable = nil
    __setVariable = nil
    __matchWait = nil
    __checkStatus = nil
    __keepAlive = nil
    __listenForInput = nil
    __echo = nil
    __protocolLines = nil
    __waitLines = nil
    __clearLines = nil
    __capture = nil
    __re = nil
    __readModule = nil
    __scripts = nil
    __storage = nil

    -- Kept here so a script that reassigns these globals cannot break the API.
    local load, ipairs, pcall, error, type, tostring, setmetatable, getmetatable =
        load, ipairs, pcall, error, type, tostring, setmetatable, getmetatable
    local lower, sub, match = string.lower, string.sub, string.match
    local pack, unpack, concat = table.pack, table.unpack, table.concat
    local globals = _ENV

    -- The host hands tables over as Lua constructors; this makes them tables again.
    local function literal(text)
        if text == nil then return nil end
        return load("return " .. text, "=(warlock)", "t", {})()
    end

    -- link(label [, input]): a part of an echoed line that, clicked, sends input (the label, if
    -- none is given) to this script, for waitForInput or onInput.
    local linkKind = {}
    function link(label, input)
        label = tostring(label)
        if input == nil then input = label end
        return setmetatable({ label = label, input = tostring(input) }, linkKind)
    end

    -- echo(...): writes one line made of the arguments, text and links alike.
    function echo(...)
        local args = pack(...)
        local parts = {}
        for i = 1, args.n do
            local part = args[i]
            if getmetatable(part) == linkKind then
                parts[#parts + 1] = part.label
                parts[#parts + 1] = part.input
            else
                parts[#parts + 1] = part == nil and "" or tostring(part)
                parts[#parts + 1] = false
            end
        end
        echoHost(unpack(parts))
    end

    -- echoLink(label [, input]): a line that is just the one link.
    function echoLink(label, input) echo(link(label, input)) end

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
        echo(concat(parts, "\t"))
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

    -- The protocol lines since the last call, markup and all, for libraries that keep track of
    -- the game themselves; the first call starts with the connection's recent history. The second
    -- value is true when lines were lost because the script left them unread too long.
    function protocolLines()
        local lines, lost = protocolHost()
        return literal(lines), lost
    end

    -- The status icons lit, each true: stunned, kneeling, hidden, webbed...
    function status() return literal(statusHost()) end

    -- Patterns for the waits: a regex string, a compiled re object, or a list of either.
    local function patternArgs(patterns)
        if type(patterns) ~= "table" or patterns.__regex then patterns = { patterns } end
        local args = {}
        for _, p in ipairs(patterns) do
            if type(p) == "table" then
                args[#args + 1] = p.pattern
                args[#args + 1] = p.flags
            else
                args[#args + 1] = tostring(p)
                args[#args + 1] = ""
            end
        end
        return args
    end

    -- waitFor(patterns [, timeout]) -> line, index, captures; nil when the time runs out.
    -- Reads the lines the game has sent since the script last read one (see clearLines).
    function waitFor(patterns, timeout)
        local line, index, captures = waitLines(timeout or 0, unpack(patternArgs(patterns)))
        if line == nil then return nil end
        return line, index, literal(captures)
    end

    -- nextLine([timeout]) -> the next line; nil when the time runs out.
    function nextLine(timeout)
        return (waitLines(timeout or 0))
    end

    -- Forgets the lines read so far, so the next wait only sees what comes after.
    function clearLines() clearLinesHost() end

    -- Sends a command and waits for the game's answer to it.
    function sendAndWait(command, patterns, timeout)
        waitForRoundTime()
        clearLines()
        put(command)
        return waitFor(patterns, timeout)
    end

    -- capture(command [, startPattern [, endPattern [, timeout]]]) -> lines, complete
    -- Sends the command without echoing it and takes the game's answer off the screen: from the
    -- line matching startPattern (the first line, without one) to the one matching endPattern (the
    -- next prompt, without one). complete is false when the time ran out first.
    function capture(command, startPattern, endPattern, timeout)
        local s = startPattern and patternArgs(startPattern) or {}
        local e = endPattern and patternArgs(endPattern) or {}
        local lines, complete = captureHost(command, timeout or 0, s[1], s[2], e[1], e[2])
        return literal(lines), complete
    end

    -- Waits until condition() returns something other than nil or false, checking every interval
    -- seconds (0.1 by default), and returns it; nil when the time runs out.
    function waitUntil(condition, timeout, interval)
        interval = interval or 0.1
        local deadline = timeout and (now() + timeout)
        while true do
            local result = condition()
            if result then return result end
            local left = deadline and (deadline - now())
            if left and left <= 0 then return nil end
            pause(left and math.min(interval, left) or interval)
        end
    end

    -- Regular expressions (Kotlin's flavour; flags "i" ignore case, "m" multiline). Positions are
    -- Lua's, as string.find gives them.
    re = {}
    local compiled = {}
    compiled.__index = compiled

    function re.compile(pattern, flags)
        reHost("check", pattern, flags or "")
        return setmetatable({ pattern = pattern, flags = flags or "", __regex = true }, compiled)
    end

    function re.escape(text) return reHost("escape", text) end

    function re.test(pattern, text, flags) return reHost("test", pattern, flags or "", text) end

    -- The captures, or the whole match when the pattern has none; nil when it does not match.
    function re.match(pattern, text, flags)
        local captures, count = reHost("match", pattern, flags or "", text)
        if captures == nil then return nil end
        return unpack(literal(captures), 1, count)
    end

    -- start, finish, captures...; nil when it does not match.
    function re.find(pattern, text, init, flags)
        local found = reHost("find", pattern, flags or "", text, init or 1)
        if found == nil then return nil end
        local m = literal(found)
        return m.start, m.finish, unpack(m.captures, 1, m.count)
    end

    function re.gmatch(pattern, text, flags)
        local all = literal(reHost("all", pattern, flags or "", text))
        local i = 0
        return function()
            i = i + 1
            local m = all[i]
            if m == nil then return nil end
            return unpack(m.captures, 1, m.count)
        end
    end

    -- The replacement is a string ($1 for a group), a function given the captures, or a table
    -- keyed by the first capture. Returns the new text and how many matches there were.
    function re.gsub(pattern, text, replacement, flags)
        if type(replacement) == "string" then
            return reHost("replace", pattern, flags or "", text, replacement)
        end
        local all = literal(reHost("all", pattern, flags or "", text))
        local parts, last = {}, 1
        for _, m in ipairs(all) do
            parts[#parts + 1] = sub(text, last, m.start - 1)
            local value
            if type(replacement) == "table" then
                value = replacement[m.captures[1]]
            else
                value = replacement(unpack(m.captures, 1, m.count))
            end
            if value == nil or value == false then value = m.match end
            parts[#parts + 1] = tostring(value)
            last = m.finish + 1
        end
        parts[#parts + 1] = sub(text, last)
        return concat(parts), #all
    end

    function re.split(pattern, text, flags) return literal(reHost("split", pattern, flags or "", text)) end

    function compiled:test(text) return re.test(self.pattern, text, self.flags) end
    function compiled:match(text) return re.match(self.pattern, text, self.flags) end
    function compiled:find(text, init) return re.find(self.pattern, text, init, self.flags) end
    function compiled:gmatch(text) return re.gmatch(self.pattern, text, self.flags) end
    function compiled:gsub(text, replacement) return re.gsub(self.pattern, text, replacement, self.flags) end
    function compiled:split(text) return re.split(self.pattern, text, self.flags) end

    -- require("name") loads lib/name.lua from the script folders (lib/a/b.lua for "a.b"), once.
    local loaded, loading = {}, {}
    function require(name)
        if loaded[name] ~= nil then return loaded[name] end
        if loading[name] then error("require: \"" .. tostring(name) .. "\" requires itself", 2) end
        local source, chunkName = readModule(name)
        if source == nil then
            error("require: no library \"" .. tostring(name) .. "\" in the lib folder of any script folder", 2)
        end
        local chunk, err = load(source, chunkName, "t", globals)
        if chunk == nil then error(err, 2) end
        loading[name] = true
        local ok, result = pcall(chunk, name)
        loading[name] = nil
        if not ok then error(result, 0) end
        if result == nil then result = true end
        loaded[name] = result
        return result
    end

    -- The other scripts on this connection.
    scripts = {}
    function scripts.start(command) return scriptsHost("start", command) end
    function scripts.run(command) return scriptsHost("run", command) end
    function scripts.isRunning(name) return scriptsHost("running", name) end
    function scripts.stop(name) return scriptsHost("stop", name) end
    function scripts.pause(name) return scriptsHost("pause", name) end
    function scripts.resume(name) return scriptsHost("resume", name) end
    function scripts.list() return literal(scriptsHost("list")) end

    -- The script's own data, kept per character between runs.
    storage = {}
    function storage.load() return literal(storageHost("load")) or {} end
    function storage.save(data) storageHost("save", data) end

    -- Handlers a script registers to be told things as they happen. They are called while the
    -- script waits, and registering one keeps the script running after its last line, until it is
    -- stopped or the connection closes.
    local gmcpHandlers = {}
    local lineHandlers = {}
    local inputHandlers = {}

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

    -- onInput(function(text) ... end): called with each line the user sends the script with
    -- /put, as the links from echoLink do.
    function onInput(handler)
        inputHandlers[#inputHandlers + 1] = handler
        listenForInput()
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

    function __dispatchGmcp(name, data)
        if data ~= nil then
            -- The literal is a table constructor of constants, so it gets no environment.
            data = literal(data)
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

    function __dispatchInput(text)
        local calls = {}
        for _, handler in ipairs(inputHandlers) do
            calls[#calls + 1] = pack(handler, text)
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
