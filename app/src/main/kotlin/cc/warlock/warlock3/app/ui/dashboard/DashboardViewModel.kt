package cc.warlock.warlock3.app.ui.dashboard

import cc.warlock.warlock3.core.prefs.CharacterRepository

class DashboardViewModel(
    characterRepository: CharacterRepository
) {
    val characters = characterRepository.observeAllCharacters()
}