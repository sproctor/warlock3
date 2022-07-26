package cc.warlock.warlock3.core.prefs

import cc.warlock.warlock3.core.prefs.models.Alteration
import cc.warlock.warlock3.core.prefs.sql.AlterationQueries
import com.squareup.sqldelight.runtime.coroutines.asFlow
import com.squareup.sqldelight.runtime.coroutines.mapToList
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
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
            .mapToList(ioDispatcher)
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
            .mapToList(ioDispatcher)
    }

    suspend fun save(characterId: String, alteration: Alteration) {
        withContext(ioDispatcher) {
            alterationQueries.save(
                cc.warlock.warlock3.core.prefs.sql.Alteration(
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