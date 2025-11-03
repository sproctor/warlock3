import kotlinx.coroutines.flow.Flow
import warlockfe.warlock3.core.prefs.models.Highlight
import warlockfe.warlock3.core.prefs.repositories.HighlightRepository
import kotlin.uuid.Uuid

class FakeHighlightRepository : HighlightRepository {
    override fun observeGlobal(): Flow<List<Highlight>> {
        TODO("Not yet implemented")
    }

    override fun observeByCharacter(characterId: String): Flow<List<Highlight>> {
        TODO("Not yet implemented")
    }

    override fun observeForCharacter(characterId: String): Flow<List<Highlight>> {
        TODO("Not yet implemented")
    }

    override suspend fun save(characterId: String, highlight: Highlight) {
        TODO("Not yet implemented")
    }

    override suspend fun saveGlobal(highlight: Highlight) {
        TODO("Not yet implemented")
    }

    override suspend fun deleteByPattern(characterId: String, pattern: String) {
        TODO("Not yet implemented")
    }

    override suspend fun deleteById(id: Uuid) {
        TODO("Not yet implemented")
    }
}