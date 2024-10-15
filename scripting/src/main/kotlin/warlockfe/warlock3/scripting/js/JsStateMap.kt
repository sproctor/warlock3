package warlockfe.warlock3.scripting.js

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.StateFlow
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.ScriptableObject
import org.mozilla.javascript.Symbol

class JsStateMap(
    private val map: StateFlow<MutableMap<String, String>>,
    private val onPut: (name: String, value: String) -> Unit,
    private val onDelete: (name: String) -> Unit,
) : ScriptableObject() {

    private val logger = KotlinLogging.logger {}

    override fun getClassName(): String = "VariablesMap"

    override fun get(name: String?, start: Scriptable?): String? {
        logger.debug { "getting $name" }
        return name?.let { map.value[it] }
    }

    override fun get(index: Int, start: Scriptable?): String? {
        return get(index.toString(), start)
    }

    override fun get(key: Symbol?, start: Scriptable?): String? {
        return get(key?.toString(), start)
    }

    override fun get(key: Any?): String? {
        return get(key?.toString(), null)
    }

    override fun has(name: String?, start: Scriptable?): Boolean {
        return name?.let { map.value.containsKey(it) } ?: false
    }

    override fun has(index: Int, start: Scriptable?): Boolean {
        return has(index.toString(), start)
    }

    override fun has(key: Symbol?, start: Scriptable?): Boolean {
        return has(key?.toString(), start)
    }

    override fun put(name: String?, start: Scriptable?, value: Any?) {
        if (name != null) {
            logger.debug { "saving $name = $value" }
            if (value is String) {
                map.value[name] = value
                onPut(name, value)
                logger.debug { "saved" }
            }
        }
    }

    override fun put(index: Int, start: Scriptable?, value: Any?) {
        put(index.toString(), start, value)
    }

    override fun put(key: Symbol?, start: Scriptable?, value: Any?) {
        put(key?.toString(), start, value)
    }

    override fun delete(name: String?) {
        if (name != null) {
            map.value.remove(name)
            onDelete(name)
        }
    }

    override fun delete(index: Int) {
        delete(index.toString())
    }

    override fun delete(key: Symbol?) {
        delete(key?.toString())
    }

    override fun size(): Int {
        return map.value.size
    }

    override fun isEmpty(): Boolean {
        return map.value.isEmpty()
    }

}