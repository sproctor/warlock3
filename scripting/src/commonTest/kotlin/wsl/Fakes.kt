package warlockfe.warlock3.scripting.wsl

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.io.files.Path
import warlockfe.warlock3.core.client.SendCommandType
import warlockfe.warlock3.core.client.WarlockClient
import warlockfe.warlock3.core.prefs.dao.VariableDao
import warlockfe.warlock3.core.prefs.models.Highlight
import warlockfe.warlock3.core.prefs.models.NameEntity
import warlockfe.warlock3.core.prefs.models.VariableEntity
import warlockfe.warlock3.core.prefs.repositories.HighlightRepository
import warlockfe.warlock3.core.prefs.repositories.NameRepository
import warlockfe.warlock3.core.script.ScriptData
import warlockfe.warlock3.core.script.ScriptInstance
import warlockfe.warlock3.core.script.ScriptManager
import warlockfe.warlock3.core.util.SoundPlayer
import kotlin.uuid.Uuid

class FakeScriptManager : ScriptManager {
    override val runningScripts = MutableStateFlow<Map<Long, ScriptData>>(emptyMap())

    val stateChanges = mutableListOf<ScriptInstance>()

    override suspend fun startScript(
        client: WarlockClient,
        command: String,
        commandHandler: suspend (String) -> SendCommandType,
    ) = Unit

    override suspend fun startScript(
        client: WarlockClient,
        file: Path,
        commandHandler: suspend (String) -> SendCommandType,
    ) = Unit

    override suspend fun startScript(
        client: WarlockClient,
        name: String,
        contents: String,
        commandHandler: suspend (String) -> SendCommandType,
    ) = Unit

    override fun findScriptInstance(description: String): ScriptInstance? = null

    override fun scriptStateChanged(instance: ScriptInstance) {
        stateChanges += instance
    }
}

class FakeSoundPlayer : SoundPlayer {
    val playedSounds = mutableListOf<String>()

    override suspend fun playSound(filename: String): String? {
        playedSounds += filename
        return null
    }
}

/** In-memory [VariableDao] backing a real [VariableRepository] in tests. */
class FakeVariableDao : VariableDao {
    private val variables = MutableStateFlow<List<VariableEntity>>(emptyList())

    override fun observeByCharacter(characterId: String): Flow<List<VariableEntity>> = variables

    override suspend fun getAllByCharacter(characterId: String): List<VariableEntity> =
        variables.value.filter { it.characterId == characterId }

    override suspend fun delete(
        characterId: String,
        name: String,
    ) {
        variables.value = variables.value.filterNot { it.characterId == characterId && it.name == name }
    }

    override suspend fun deleteByCharacter(characterId: String) {
        variables.value = variables.value.filterNot { it.characterId == characterId }
    }

    override suspend fun save(variable: VariableEntity) {
        variables.value =
            variables.value.filterNot { it.characterId == variable.characterId && it.name == variable.name } + variable
    }
}

class FakeHighlightRepository : HighlightRepository {
    val saved = mutableListOf<Pair<String, Highlight>>()
    val deletedPatterns = mutableListOf<Pair<String, String>>()

    override fun observeGlobal(): Flow<List<Highlight>> = flowOf(emptyList())

    override fun observeByCharacter(characterId: String): Flow<List<Highlight>> = flowOf(emptyList())

    override fun observeForCharacter(characterId: String): Flow<List<Highlight>> = flowOf(emptyList())

    override suspend fun save(
        characterId: String,
        highlight: Highlight,
    ) {
        saved += characterId to highlight
    }

    override suspend fun saveGlobal(highlight: Highlight) {
        saved += "global" to highlight
    }

    override suspend fun deleteByPattern(
        characterId: String,
        pattern: String,
    ) {
        deletedPatterns += characterId to pattern
    }

    override suspend fun deleteById(id: Uuid) = Unit

    override suspend fun move(
        characterId: String,
        fromIndex: Int,
        toIndex: Int,
    ) = Unit
}

class FakeNameRepository : NameRepository {
    val saved = mutableListOf<NameEntity>()
    val deletedText = mutableListOf<Pair<String, String>>()

    override fun observeGlobal(): Flow<List<NameEntity>> = flowOf(emptyList())

    override fun observeByCharacter(characterId: String): Flow<List<NameEntity>> = flowOf(emptyList())

    override fun observeForCharacter(characterId: String): Flow<List<NameEntity>> = flowOf(emptyList())

    override suspend fun save(name: NameEntity) {
        saved += name
    }

    override suspend fun deleteByText(
        characterId: String,
        text: String,
    ) {
        deletedText += characterId to text
    }

    override suspend fun deleteById(id: Uuid) = Unit
}
