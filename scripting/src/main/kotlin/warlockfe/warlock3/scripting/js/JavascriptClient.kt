package warlockfe.warlock3.scripting.js

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.runBlocking
import warlockfe.warlock3.core.client.ClientNavEvent
import warlockfe.warlock3.core.client.ClientPromptEvent
import warlockfe.warlock3.core.client.WarlockClient
import warlockfe.warlock3.core.prefs.VariableRepository
import warlockfe.warlock3.core.text.StyledString
import warlockfe.warlock3.core.text.WarlockStyle

class JavascriptClient(
    val client: WarlockClient,
    val scope: CoroutineScope,
    val variableRepository: VariableRepository,
    private val instance: JsInstance,
) {

    private val promptChannel = Channel<Unit>(0)
    private val navChannel = Channel<Unit>(0)

    private var loggingLevel = 30

    init {
        client.eventFlow
            .onEach { event ->
                when (event) {
                    is ClientPromptEvent -> {
                        promptChannel.trySend(Unit)
                    }

                    ClientNavEvent -> {
                        navChannel.trySend(Unit)
                    }

                    else -> Unit
                }
            }
            .launchIn(scope)
    }

    fun echo(text: String) {
        instance.checkStatus()
        runBlocking(scope.coroutineContext) {
            client.print(StyledString(text, style = WarlockStyle.Echo))
        }
    }

    fun put(command: String) {
        instance.checkStatus()
        runBlocking(scope.coroutineContext) {
            putCommand(command)
        }
    }

    fun waitForNav() {
        instance.checkStatus()
        runBlocking(scope.coroutineContext) {
            doWaitForNav()
        }
    }

    fun move(command: String) {
        instance.checkStatus()
        runBlocking(scope.coroutineContext) {
            putCommand(command)
            doWaitForNav()
        }
    }

    fun log(level: Int, message: String) {
        instance.checkStatus()
        if (level >= loggingLevel) {
            runBlocking(scope.coroutineContext) {
                client.debug(message)
            }
        }
    }

    fun waitForPrompt() {
        instance.checkStatus()
        runBlocking(scope.coroutineContext) {
            doWaitForPrompt()
        }
    }

    fun waitForRoundTime() {
        instance.checkStatus()
        runBlocking(scope.coroutineContext) {
            doWaitForRoundTime()
        }
    }

    suspend fun setVariable(name: String, value: String) {
        instance.checkStatus()
        val characterId = client.characterId.value?.lowercase() ?: return
        variableRepository.put(characterId, name, value)
    }

    private suspend fun doWaitForRoundTime() {
        log(5, "waiting for round time")
        while (true) {
            instance.checkStatus()
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