import kotlinx.coroutines.flow.Flow
import warlockfe.warlock3.core.prefs.models.NameEntity
import warlockfe.warlock3.core.prefs.repositories.NameRepository
import kotlin.uuid.Uuid

class FakeNameRepository : NameRepository {
    override fun observeGlobal(): Flow<List<NameEntity>> {
        TODO("Not yet implemented")
    }

    override fun observeByCharacter(characterId: String): Flow<List<NameEntity>> {
        TODO("Not yet implemented")
    }

    override fun observeForCharacter(characterId: String): Flow<List<NameEntity>> {
        TODO("Not yet implemented")
    }

    override suspend fun save(name: NameEntity) {
        TODO("Not yet implemented")
    }

    override suspend fun deleteByText(characterId: String, text: String) {
        TODO("Not yet implemented")
    }

    override suspend fun deleteById(id: Uuid) {
        TODO("Not yet implemented")
    }
}