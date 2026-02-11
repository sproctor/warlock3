import kotlinx.coroutines.flow.Flow
import warlockfe.warlock3.core.prefs.dao.MacroDao
import warlockfe.warlock3.core.prefs.models.MacroEntity

class FakeMacroDao : MacroDao {
    override suspend fun getGlobalCount(): Int {
        TODO("Not yet implemented")
    }

    override suspend fun getOldMacros(): List<MacroEntity> {
        TODO("Not yet implemented")
    }

    override fun observeGlobals(): Flow<List<MacroEntity>> {
        TODO("Not yet implemented")
    }

    override fun observeByCharacter(characterId: String): Flow<List<MacroEntity>> {
        TODO("Not yet implemented")
    }

    override suspend fun getByCharacter(characterId: String): List<MacroEntity> {
        TODO("Not yet implemented")
    }

    override fun observeByCharacterWithGlobals(characterId: String): Flow<List<MacroEntity>> {
        TODO("Not yet implemented")
    }

    override suspend fun save(macro: MacroEntity) {
        println("Saving macro: $macro")
    }

    override suspend fun delete(characterId: String, keyString: String) {
        TODO("Not yet implemented")
    }

    override suspend fun deleteByKey(characterId: String, key: String) {
        TODO("Not yet implemented")
    }

    override suspend fun deleteAllGlobals() {
        TODO("Not yet implemented")
    }
}