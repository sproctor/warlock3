package cc.warlock.warlock3.core.script.js

import cc.warlock.warlock3.core.client.WarlockClient
import cc.warlock.warlock3.core.script.ScriptInstance
import cc.warlock.warlock3.core.script.VariableRegistry
import cc.warlock.warlock3.core.text.StyledString
import cc.warlock.warlock3.core.text.WarlockStyle
import cc.warlock.warlock3.core.util.toCaseInsensitiveMap
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import org.mozilla.javascript.Context
import org.mozilla.javascript.EvaluatorException
import org.mozilla.javascript.FunctionObject
import org.mozilla.javascript.ScriptableObject
import java.io.File
import java.io.InputStreamReader
import kotlin.concurrent.thread
import kotlin.reflect.jvm.javaMethod

class JsInstance(
    override val name: String,
    private val file: File,
    private val variableRegistry: VariableRegistry,
) : ScriptInstance {

    private var _isRunning = false
    override val isRunning: Boolean
        get() = _isRunning

    private var _isSuspended = false
    override val isSuspended: Boolean
        get() = _isSuspended

    private lateinit var thread: Thread

    val scope = CoroutineScope(Dispatchers.Default)

    lateinit var context: Context
        private set

    var client: WarlockClient? = null
        private set

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun start(client: WarlockClient, argumentString: String, onStop: () -> Unit) {
        _isRunning = true
        this.client = client
        thread = thread {
            context = Context.enter()
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
                        instance = this,
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
                            onPut = { name: String, value: String ->
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
                    jsScope.put(
                        "pause",
                        jsScope,
                        FunctionObject(
                            "pause",
                            JavascriptFunctions::pause.javaMethod,
                            jsScope,
                        ),
                    )
                    jsScope.put(
                        "exit",
                        jsScope,
                        FunctionObject(
                            "exit",
                            JavascriptFunctions::exit.javaMethod,
                            jsScope,
                        )
                    )
                }
                ScriptableObject.defineClass(jsScope, MatchList::class.java)
                context.evaluateReader(jsScope, reader, file.name, 1, null)
            } catch (e: StopException) {
                runBlocking {
                    client.print(StyledString("JS scripted stopped", style = WarlockStyle.Echo))
                }
            } catch (e: EvaluatorException) {
                runBlocking {
                    client.print(StyledString("Script error: ${e.message}", style = WarlockStyle.Error))
                }
            } finally {
                _isRunning = false
                Context.exit()
                onStop()
            }
        }
    }

    override fun stop() {
        _isRunning = false
        thread.interrupt()
        scope.cancel()
    }

    override fun suspend() {
        _isSuspended = true
        thread.interrupt()
    }

    override fun resume() {
        _isSuspended = false
        thread.interrupt()
    }

    fun checkStatus() {
        // Check suspend first, so if we're stopped while suspended, we immediately throw StopException
        while (isSuspended && isRunning) {
            try {
                Thread.sleep(1000)
            } catch (e: InterruptedException) {
                // don't care
            }
        }
        if (!isRunning) {
            throw StopException()
        }
    }
}