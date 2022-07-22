package cc.warlock.warlock3.core.script.js

import cc.warlock.warlock3.core.client.WarlockClient
import cc.warlock.warlock3.core.prefs.VariableRepository
import cc.warlock.warlock3.core.script.ScriptInstance
import cc.warlock.warlock3.core.script.ScriptStatus
import cc.warlock.warlock3.core.text.StyledString
import cc.warlock.warlock3.core.text.WarlockStyle
import cc.warlock.warlock3.core.util.toCaseInsensitiveMap
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.mozilla.javascript.*
import java.io.File
import java.io.InputStreamReader
import java.util.*
import kotlin.concurrent.thread
import kotlin.reflect.jvm.javaMethod

class JsInstance(
    override val name: String,
    private val file: File,
    private val variableRepository: VariableRepository,
) : ScriptInstance {

    override val id: UUID = UUID.randomUUID()

    override var status: ScriptStatus = ScriptStatus.NotStarted
        private set

    private lateinit var thread: Thread

    val scope = CoroutineScope(Dispatchers.Default)

    lateinit var context: Context
        private set

    var client: WarlockClient? = null
        private set

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun start(client: WarlockClient, argumentString: String, onStop: () -> Unit) {
        status = ScriptStatus.Running
        this.client = client
        thread = thread {
            context = Context.enter()
            context.languageVersion = Context.VERSION_ES6
            try {
                val jsScope = context.initStandardObjects()
                val reader = InputStreamReader(file.inputStream())
                jsScope.put(
                    "client",
                    jsScope,
                    JavascriptClient(
                        client = client,
                        scope = scope,
                        variableRepository = variableRepository,
                        instance = this,
                    )
                )
                runBlocking {
                    val globalVariables = client.characterId.flatMapLatest { id ->
                        if (id != null) {
                            variableRepository.observeCharacterVariables(id).map {
                                println("reloading variables $it")
                                it.map { variable -> Pair(variable.name, variable.value) }
                                    .toMap()
                                    .toCaseInsensitiveMap()
                                    .toMutableMap()
                            }
                        } else {
                            flow<MutableMap<String, String>> {
                                emit(mutableMapOf())
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
                                    runBlocking {
                                        variableRepository.put(characterId, name, value)
                                    }
                                }
                            },
                            onDelete = {
                                client.characterId.value?.let { characterId ->
                                    runBlocking {
                                        variableRepository.delete(characterId, name)
                                    }
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
                // nothing to do
            } catch (e: WrappedException) {
                val wrappedException = e.wrappedException
                if (wrappedException is StopException) {
                    // nothing to do
                } else {
                    e.printStackTrace()
                    // FIXME: What should we do here? Is this correct?
                    runBlocking {
                        client.print(StyledString("Script error: ${e.message}", style = WarlockStyle.Error))
                    }
                }
            } catch (e: EvaluatorException) {
                runBlocking {
                    e.printStackTrace()
                    client.print(StyledString("Script error: ${e.message}", style = WarlockStyle.Error))
                }
            } catch (e: EcmaError) {
                runBlocking {
                    e.printStackTrace()
                    client.print(StyledString("Script error: ${e.message}", style = WarlockStyle.Error))
                }
            } finally {
                status = ScriptStatus.Stopped
                Context.exit()
                onStop()
            }
        }
    }

    override fun stop() {
        status = ScriptStatus.Stopped
        thread.interrupt()
        scope.cancel()
    }

    override fun suspend() {
        status = ScriptStatus.Suspended
        thread.interrupt()
    }

    override fun resume() {
        status = ScriptStatus.Running
        thread.interrupt()
    }

    fun checkStatus() {
        // Check suspend first, so if we're stopped while suspended, we immediately throw StopException
        while (status == ScriptStatus.Suspended) {
            try {
                Thread.sleep(1000)
            } catch (e: InterruptedException) {
                // don't care
            }
        }
        if (status == ScriptStatus.Stopped) {
            throw StopException()
        }
    }
}