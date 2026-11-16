package warlockfe.warlock3.compose.ui.game

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow

/**
 * Observes a per-character flow from a stream of connected character ids: [block]'s flow for each
 * character, switched whenever the character changes, and nothing while none is connected.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal fun <T> Flow<String?>.observePerCharacter(block: (characterId: String) -> Flow<T>): Flow<T> =
    flatMapLatest { characterId ->
        if (characterId != null) block(characterId) else flow {}
    }
