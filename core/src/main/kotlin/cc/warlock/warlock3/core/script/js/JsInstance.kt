package cc.warlock.warlock3.core.script.js

import cc.warlock.warlock3.core.client.WarlockClient
import cc.warlock.warlock3.core.script.ScriptInstance
import cc.warlock.warlock3.core.script.VariableRegistry
import cc.warlock.warlock3.core.text.StyledString
import cc.warlock.warlock3.core.text.WarlockStyle
import cc.warlock.warlock3.core.util.toCaseInsensitiveMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.runBlocking
import org.mozilla.javascript.Context
import org.mozilla.javascript.ScriptableObject
import java.io.File
import java.io.InputStreamReader
import kotlin.concurrent.thread

class JsInstance(
    override val name: String,
    private val file: File,
    private val variableRegistry: VariableRegistry,
) : ScriptInstance {

    private var _isRunning = false
    override val isRunning: Boolean
        get() = _isRunning

    private lateinit var thread: Thread

    private var scope = CoroutineScope(Dispatchers.Default)

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun start(client: WarlockClient, argumentString: String, onStop: () -> Unit) {
        _isRunning = true
        thread = thread {
            val context = Context.enter()
            try {
                val jsScope = context.initStandardObjects()
                val reader = InputStreamReader(file.inputStream())
                jsScope.put(
                    "client",
                    jsScope,
                    JavascriptClient(
                        client = client,
                        context = scope.coroutineContext,
                        variableRegistry = variableRegistry,
                    )
                )
                runBlocking {
                    val globalVariables = client.characterId.flatMapLatest { id ->
                        if (id != null) {
                            variableRegistry.getVariablesForCharacter(id).map {
                                println("reloading variables $it")
                                it.toCaseInsensitiveMap().toMutableMap()
                            }
                        } else {
                            flow {
                                mutableMapOf<String, String>()
                            }
                        }
                    }
                        .stateIn(scope = scope)
                    jsScope.put(
                        "variables",
                        jsScope,
                        JsStateMap(
                            map = globalVariables,
                            onPut = { name, value ->
                                client.characterId.value?.let { characterId ->
                                    variableRegistry.saveVariable(characterId, name, value)
                                }
                            },
                            onDelete = {
                                client.characterId.value?.let { characterId ->
                                    variableRegistry.deleteVariable(characterId, name)
                                }
                            },
                        )
                    )
                }
                ScriptableObject.defineClass(jsScope, MatchList::class.java)
                context.evaluateReader(jsScope, reader, file.name, 1, null)
            } catch (e: StopException) {
                runBlocking {
                    client.print(StyledString("JS scripted stopped", style = WarlockStyle.Echo))
                }
            } finally {
                Context.exit()
                onStop()
            }
        }
    }

    override fun stop() {
        _isRunning = false
        thread.interrupt()
    }

    override fun suspend() {
        TODO("Not yet implemented")
    }

    override fun resume() {
        TODO("Not yet implemented")
    }
}