package cc.warlock.warlock3.core.script

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class VariableRegistry(
    val variables: Flow<Map<String, Map<String, String>>>,
    val saveVariable: (character: String, name: String, value: String) -> Unit,
    val deleteVariable: (character: String, name: String) -> Unit,
) {
    fun getVariablesForCharacter(character: String): Flow<Map<String, String>> {
        return variables.map {
            it[character.lowercase()] ?: emptyMap()
        }
    }
}