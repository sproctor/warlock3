package warlockfe.warlock3.scripting.js

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.io.files.Path
import org.mozilla.javascript.Context
import org.mozilla.javascript.EcmaError
import org.mozilla.javascript.EvaluatorException
import org.mozilla.javascript.FunctionObject
import org.mozilla.javascript.ScriptableObject
import org.mozilla.javascript.WrappedException
import warlockfe.warlock3.core.client.SendCommandType
import warlockfe.warlock3.core.client.WarlockClient
import warlockfe.warlock3.core.prefs.repositories.VariableRepository
import warlockfe.warlock3.core.script.ScriptInstance
import warlockfe.warlock3.core.script.ScriptManager
import warlockfe.warlock3.core.script.ScriptStatus
import warlockfe.warlock3.core.text.StyledString
import warlockfe.warlock3.core.text.WarlockStyle
import java.io.File
import java.io.InputStreamReader
import kotlin.concurrent.thread
import kotlin.reflect.jvm.javaMethod

class JsInstance(
    override val id: Long,
    override val name: String,
    private val file: Path,
    private val variableRepository: VariableRepository,
    private val scriptManager: ScriptManager,
) : ScriptInstance {
    override var status: ScriptStatus = ScriptStatus.NotStarted
        private set(newStatus) {
            field = newStatus
            scriptManager.scriptStateChanged(this)
        }

    private lateinit var thread: Thread

    val scope = CoroutineScope(Dispatchers.Default)

    lateinit var context: Context
        private set

    var client: WarlockClient? = null
        private set

    override fun start(
        client: WarlockClient,
        argumentString: String,
        onStop: () -> Unit,
        commandHandler: suspend (String) -> SendCommandType,
    ) {
        status = ScriptStatus.Running
        this.client = client
        thread =
            thread {
                context = Context.enter()
                context.languageVersion = Context.VERSION_ES6
                try {
                    val jsScope = context.initStandardObjects()
                    val reader = InputStreamReader(File(file.toString()).inputStream())
                    jsScope.put(
                        "client",
                        jsScope,
                        JavascriptClient(
                            client = client,
                            scope = scope,
                            variableRepository = variableRepository,
                            instance = this,
                        ),
                    )
                    // Reads/writes go straight to the config store (source of truth) so a variable
                    // read at script start can't race a not-yet-populated cached snapshot.
                    jsScope.put(
                        "variables",
                        jsScope,
                        JsStateMap(
                            variableRepository = variableRepository,
                            characterId = { client.characterId.value?.lowercase() },
                        ),
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
                        ),
                    )
                    ScriptableObject.defineClass(jsScope, MatchList::class.java)
                    context.evaluateReader(jsScope, reader, file.name, 1, null)
                } catch (_: StopException) {
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
                    runBlocking {
                        onStop()
                    }
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
            } catch (_: InterruptedException) {
                // don't care
            }
        }
        if (status == ScriptStatus.Stopped) {
            throw StopException()
        }
    }
}
