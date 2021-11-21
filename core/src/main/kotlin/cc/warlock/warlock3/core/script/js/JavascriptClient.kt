package cc.warlock.warlock3.core.script.js

import cc.warlock.warlock3.core.client.WarlockClient
import cc.warlock.warlock3.core.script.VariableRegistry
import cc.warlock.warlock3.core.text.StyledString
import cc.warlock.warlock3.core.text.WarlockStyle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.CoroutineContext

class JavascriptClient(
    val client: WarlockClient,
    val context: CoroutineContext,
    val variableRegistry: VariableRegistry,
) {

    private val promptChannel = Channel<Unit>(0)
    private val navChannel = Channel<Unit>(0)

    private val mutex = Mutex()
    private var typeAhead = 0

    private var loggingLevel = 30

    fun echo(text: String) {
        runBlocking(context) {
            client.print(StyledString(text, style = WarlockStyle.Echo))
        }
    }

    fun put(command: String) {
        runBlocking(context) {
            putCommand(command)
        }
    }

    fun waitForNav() {
        runBlocking(context) {
            doWaitForNav()
        }
    }

    fun move(command: String) {
        runBlocking(context) {
            putCommand(command)
            doWaitForNav()
        }
    }

    fun log(level: Int, message: String) {
        if (level >= loggingLevel) {
            runBlocking(context) {
                client.debug(message)
            }
        }
    }

    fun waitForPrompt() {
        runBlocking(context) {
            doWaitForPrompt()
        }
    }

    fun waitForRoundTime() {
        runBlocking(context) {
            doWaitForRoundTime()
        }
    }

    fun setVariable(name: String, value: String) {
        val characterId = client.characterId.value ?: return
        variableRegistry.saveVariable(characterId, name, value)
    }

    fun exit() {
        throw StopException()
    }

    private suspend fun doWaitForRoundTime() {
        log(5, "waiting for round time")
        while (true) {
            val roundEnd = client.properties.value["roundtime"]?.toLongOrNull()?.let { it * 1000L } ?: return
            val currentTime = client.time
            if (roundEnd < currentTime) {
                break
            }
            val duration = roundEnd - currentTime
            log(0, "wait duration: ${duration}ms")
            delay(duration)
        }
        log(5, "done waiting for round time")
    }

    private suspend fun putCommand(command: String) {
        doWaitForRoundTime()
        mutex.withLock {
            typeAhead++
        }
        if (typeAhead > client.maxTypeAhead) {
            waitForPrompt()
        }
        log(5, "sending command: $command")
        client.sendCommand(command)
    }

    private suspend fun doWaitForNav() {
        log(5, "waiting for next room")
        navChannel.receive()
    }

    private suspend fun doWaitForPrompt() {
        log(5, "waiting for next prompt")
        promptChannel.receive()
    }
}