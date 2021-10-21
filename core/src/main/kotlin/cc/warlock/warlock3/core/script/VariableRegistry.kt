package cc.warlock.warlock3.core.script

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class VariableRegistry(
    initialVariables: Map<String, Map<String, String>>,
    private val saveVariables: (Map<String, Map<String, String>>) -> Unit
) {
    private val _variables = MutableStateFlow(initialVariables)
    val variables = _variables.asStateFlow()

    private val lock = ReentrantLock()

    fun setVariable(character: String, name: String, value: String) {
        lock.withLock {
            val newVariables = (_variables.value[character] ?: emptyMap()) + (name to value)
            _variables.value += character to newVariables
            saveVariables(_variables.value)
        }
    }

    fun deleteVariable(character: String, name: String) {
        lock.withLock {
            val newVariables = (_variables.value[character] ?: emptyMap()) - name
            _variables.value += character to newVariables
            saveVariables(_variables.value)
        }
    }

    fun getVariablesForCharacter(character: String): Flow<Map<String, String>> {
        return variables.map {
            it[character] ?: emptyMap()
        }
    }
}