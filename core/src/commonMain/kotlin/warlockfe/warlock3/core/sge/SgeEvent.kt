package warlockfe.warlock3.core.sge

sealed class SgeEvent {
    object SgeLoginSucceededEvent : SgeEvent()
    data class SgeGamesReadyEvent(val games: List<SgeGame>) : SgeEvent()
    object SgeGameSelectedEvent : SgeEvent()
    data class SgeCharactersReadyEvent(val characters: List<SgeCharacter>) : SgeEvent()
    data class SgeReadyToPlayEvent(val credentials: SimuGameCredentials) : SgeEvent()
    data class SgeErrorEvent(val errorCode: SgeError) : SgeEvent()
}