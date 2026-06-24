package warlockfe.warlock3.scripting.js

import co.touchlab.kermit.Logger
import kotlinx.coroutines.runBlocking
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.ScriptableObject
import org.mozilla.javascript.Symbol
import warlockfe.warlock3.core.prefs.repositories.VariableRepository

/**
 * Backs the JS `variables` object. Reads and writes go straight to [variableRepository] (the config
 * store, our source of truth) rather than a cached snapshot, so a variable read right as the script
 * starts can't race an out-of-date copy. Rhino invokes these accessors synchronously on the script
 * thread; reads hit the in-memory store directly (no blocking), while the suspend writes are bridged
 * with [runBlocking].
 */
class JsStateMap(
    private val variableRepository: VariableRepository,
    private val characterId: () -> String?,
) : ScriptableObject() {
    private val logger = Logger.withTag("JsStateMap")

    override fun getClassName(): String = "VariablesMap"

    private fun variables(): Map<String, String> = characterId()?.let { id -> variableRepository.getVariables(id) } ?: emptyMap()

    override fun get(
        name: String?,
        start: Scriptable?,
    ): String? {
        logger.d { "getting $name" }
        val id = characterId() ?: return null
        return name?.let { variableRepository.getVariable(id, it) }
    }

    override fun get(
        index: Int,
        start: Scriptable?,
    ): String? = get(index.toString(), start)

    override fun get(
        key: Symbol?,
        start: Scriptable?,
    ): String? = get(key?.toString(), start)

    override fun get(key: Any?): String? = get(key?.toString(), null)

    override fun has(
        name: String?,
        start: Scriptable?,
    ): Boolean = name?.let { get(it, start) != null } ?: false

    override fun has(
        index: Int,
        start: Scriptable?,
    ): Boolean = has(index.toString(), start)

    override fun has(
        key: Symbol?,
        start: Scriptable?,
    ): Boolean = has(key?.toString(), start)

    override fun put(
        name: String?,
        start: Scriptable?,
        value: Any?,
    ) {
        if (name != null && value is String) {
            logger.d { "saving $name = $value" }
            val id = characterId() ?: return
            runBlocking { variableRepository.put(id, name, value) }
        }
    }

    override fun put(
        index: Int,
        start: Scriptable?,
        value: Any?,
    ) {
        put(index.toString(), start, value)
    }

    override fun put(
        key: Symbol?,
        start: Scriptable?,
        value: Any?,
    ) {
        put(key?.toString(), start, value)
    }

    override fun delete(name: String?) {
        if (name != null) {
            val id = characterId() ?: return
            runBlocking { variableRepository.delete(id, name) }
        }
    }

    override fun delete(index: Int) {
        delete(index.toString())
    }

    override fun delete(key: Symbol?) {
        delete(key?.toString())
    }

    override fun size(): Int = variables().size

    override fun isEmpty(): Boolean = variables().isEmpty()
}
