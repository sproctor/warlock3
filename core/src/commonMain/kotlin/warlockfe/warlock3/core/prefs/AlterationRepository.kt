package warlockfe.warlock3.core.prefs

import app.cash.sqldelight.coroutines.asFlow
import warlockfe.warlock3.core.prefs.models.Alteration
import warlockfe.warlock3.core.prefs.sql.AlterationQueries
import warlockfe.warlock3.core.util.mapToList
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.util.*

class AlterationRepository(
    private val alterationQueries: AlterationQueries,
    private val ioDispatcher: CoroutineDispatcher,
) {

    fun observeByCharacter(characterId: String): Flow<List<Alteration>> {
        return alterationQueries.getAlterationsByCharacter(
            characterId = characterId
        ) { id: UUID,
            _: String,
            pattern: String,
            sourceStream: String?,
            destinationStream: String?,
            result: String?,
            ignoreCase: Boolean,
            entireLine: Boolean ->
            Alteration(id, pattern, sourceStream, destinationStream, result, ignoreCase, entireLine)
        }
            .asFlow()
            .mapToList()
            .flowOn(ioDispatcher)
    }

    fun observeForCharacter(characterId: String): Flow<List<Alteration>> {
        return alterationQueries.getAlterationsForCharacter(
            characterId
        ) { id: UUID,
            _: String,
            pattern: String,
            sourceStream: String?,
            destinationStream: String?,
            result: String?,
            ignoreCase: Boolean,
            entireLine: Boolean ->
            Alteration(id, pattern, sourceStream, destinationStream, result, ignoreCase, entireLine)
        }
            .asFlow()
            .mapToList()
            .flowOn(ioDispatcher)
    }

    suspend fun save(characterId: String, alteration: Alteration) {
        withContext(ioDispatcher) {
            alterationQueries.save(
                warlockfe.warlock3.core.prefs.sql.Alteration(
                    id = alteration.id,
                    characterId = characterId,
                    pattern = alteration.pattern,
                    sourceStream = alteration.sourceStream,
                    destinationStream = alteration.destinationStream,
                    result = alteration.result,
                    ignoreCase = alteration.ignoreCase,
                    keepOriginal = alteration.keepOriginal,
                )
            )
        }
    }

    suspend fun saveGlobal(alteration: Alteration) {
        save("global", alteration)
    }

    suspend fun deleteById(id: UUID) {
        withContext(ioDispatcher) {
            alterationQueries.deleteById(id)
        }
    }
}