package cc.warlock.warlock3.core.script.js

import kotlinx.coroutines.flow.StateFlow
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.ScriptableObject
import org.mozilla.javascript.Symbol

class JsStateMap<T>(
    private val map: StateFlow<MutableMap<String, T>>,
    private val onPut: (name: String, value: T) -> Unit,
    private val onDelete: (name: String) -> Unit,
) : ScriptableObject() {

    override fun getClassName(): String = "VariablesMap"

    override fun get(name: String?, start: Scriptable?): T? {
        println("getting $name")
        return name?.let { map.value[it] }
    }

    override fun get(index: Int, start: Scriptable?): T? {
        return get(index.toString(), start)
    }

    override fun get(key: Symbol?, start: Scriptable?): T? {
        return get(key?.toString(), start)
    }

    override fun get(key: Any?): T? {
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
            println("saving $name = $value")
            map.value[name] = value as T
            onPut(name, value as T)
            println("saved")
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