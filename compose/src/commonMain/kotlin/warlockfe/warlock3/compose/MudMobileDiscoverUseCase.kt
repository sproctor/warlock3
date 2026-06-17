package warlockfe.warlock3.compose

import co.touchlab.kermit.Logger
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import warlockfe.warlock3.core.mudmobile.CreateCharacterResult
import warlockfe.warlock3.core.mudmobile.MudMobileApi
import warlockfe.warlock3.core.sge.SgeClientFactory
import warlockfe.warlock3.core.sge.SgeEvent
import warlockfe.warlock3.core.sge.SgeSettings
import kotlin.time.Duration.Companion.seconds

/**
 * Discovers a user's characters for a given game via a local SGE login (account + password, no
 * secrets leave the machine) and registers each one with MUD Mobile via `POST /api/characters`
 * (§4.1b) so they appear in the picker. Used to populate a brand-new / empty character list from
 * Warlock instead of the dashboard.
 */
class MudMobileDiscoverUseCase(
    private val api: MudMobileApi,
    private val sgeClientFactory: SgeClientFactory,
) {
    private val logger = Logger.withTag("MudMobileDiscover")

    suspend operator fun invoke(
        token: String,
        sgeSettings: SgeSettings,
        account: String,
        password: String,
        gameCode: String,
    ): MudMobileDiscoverResult {
        if (password.isEmpty()) return MudMobileDiscoverResult.Failure("Enter a password.")

        val client = sgeClientFactory.create()
        val characters =
            try {
                if (!client.connect(sgeSettings)) {
                    return MudMobileDiscoverResult.Failure("Could not connect to SGE.")
                }
                client.login(account, password)
                // Drive the SGE request/response handshake: login -> select game -> character list.
                withTimeoutOrNull(30.seconds) {
                    while (true) {
                        when (val event = client.eventFlow.first()) {
                            is SgeEvent.SgeLoginSucceededEvent -> {
                                client.selectGame(gameCode)
                            }

                            is SgeEvent.SgeGameSelectedEvent -> {
                                client.requestCharacterList()
                            }

                            is SgeEvent.SgeCharactersReadyEvent -> {
                                return@withTimeoutOrNull event.characters
                            }

                            is SgeEvent.SgeErrorEvent -> {
                                return@withTimeoutOrNull null
                            }

                            else -> {}
                        }
                    }
                    @Suppress("UNREACHABLE_CODE")
                    null
                }
            } finally {
                client.close()
            } ?: return MudMobileDiscoverResult.Failure(
                "SGE login failed. Check the account, password, and game.",
            )

        if (characters.isEmpty()) {
            return MudMobileDiscoverResult.Failure("No characters found for that account in $gameCode.")
        }

        var added = 0
        for (character in characters) {
            when (
                val result =
                    api.createCharacter(
                        token = token,
                        account = account,
                        game = gameCode,
                        characterCode = character.code,
                        characterName = character.name,
                    )
            ) {
                is CreateCharacterResult.Success -> {
                    added++
                }

                CreateCharacterResult.Unauthorized -> {
                    return MudMobileDiscoverResult.Failure(
                        "Your MUD Mobile token is no longer valid. Reconnect your account.",
                    )
                }

                is CreateCharacterResult.Error -> {
                    logger.w { "Failed to register ${character.name}: ${result.message}" }
                }
            }
        }

        return if (added == 0) {
            MudMobileDiscoverResult.Failure("Couldn't register any characters with MUD Mobile.")
        } else {
            MudMobileDiscoverResult.Success(added)
        }
    }
}

sealed interface MudMobileDiscoverResult {
    data class Success(
        val added: Int,
    ) : MudMobileDiscoverResult

    data class Failure(
        val message: String,
    ) : MudMobileDiscoverResult
}
