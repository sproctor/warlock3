package warlockfe.warlock3.compose.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.io.files.Path
import warlockfe.warlock3.core.client.GameCharacter
import warlockfe.warlock3.core.prefs.ReleaseChannelSetting
import warlockfe.warlock3.core.prefs.ThemeSetting
import warlockfe.warlock3.core.prefs.config.GLOBAL_CHARACTER_ID
import warlockfe.warlock3.core.prefs.repositories.CharacterSettingsRepository
import warlockfe.warlock3.core.prefs.repositories.ClientSettingRepository
import warlockfe.warlock3.core.prefs.repositories.MAX_TYPE_AHEAD_KEY
import warlockfe.warlock3.core.prefs.repositories.SCRIPT_COMMAND_PREFIX_KEY
import warlockfe.warlock3.core.prefs.repositories.ScriptDirRepository
import warlockfe.warlock3.core.util.LogSettings
import warlockfe.warlock3.core.util.LogType
import warlockfe.warlock3.wrayth.settings.WraythImporter

class GeneralSettingsViewModel(
    initialCharacter: GameCharacter?,
    private val characterSettingsRepository: CharacterSettingsRepository,
    private val scriptDirRepository: ScriptDirRepository,
    private val clientSettingRepository: ClientSettingRepository,
    private val wraythImporter: WraythImporter,
) : ViewModel() {
    private val _currentCharacter = MutableStateFlow(initialCharacter)
    val currentCharacter: StateFlow<GameCharacter?> = _currentCharacter.asStateFlow()

    val markLinks: StateFlow<Boolean> =
        clientSettingRepository.observeMarkLinks().stateIn(viewModelScope, SharingStarted.Eagerly, true)

    val showImages: StateFlow<Boolean> =
        clientSettingRepository.observeShowImages().stateIn(viewModelScope, SharingStarted.Eagerly, true)

    val suppressPrompts: StateFlow<Boolean> =
        clientSettingRepository.observeSuppressPrompts().stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val autoConnectLastConnection: StateFlow<Boolean> =
        clientSettingRepository
            .observeAutoConnectLastConnection()
            .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val theme: StateFlow<ThemeSetting?> =
        clientSettingRepository.observeTheme().stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val releaseChannel: StateFlow<ReleaseChannelSetting?> =
        clientSettingRepository.observeReleaseChannel().stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val skinFile: StateFlow<String?> =
        clientSettingRepository.observeSkinFile().stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val logSettings: StateFlow<LogSettings?> =
        clientSettingRepository.observeLogSettings().stateIn(viewModelScope, SharingStarted.Eagerly, null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val scriptDirs: StateFlow<List<String>> =
        _currentCharacter
            .flatMapLatest { character ->
                scriptDirRepository.observeScriptDirs(characterId = character?.id ?: GLOBAL_CHARACTER_ID)
            }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val defaultScriptDir: String
        get() = scriptDirRepository.getDefaultDir()

    fun selectCharacter(character: GameCharacter?) {
        _currentCharacter.value = character
    }

    // The text fields read their stored value once and then write each edit from the view's own effects,
    // which cancel a write when the next edit arrives, so these suspend instead of launching.

    suspend fun getMaxScrollLines(): Int = clientSettingRepository.observeMaxScrollLines().first()

    suspend fun putMaxScrollLines(value: Int?) {
        clientSettingRepository.putMaxScrollLines(value)
    }

    suspend fun getMinCommandLength(): Int = clientSettingRepository.observeMinCommandLength().first()

    suspend fun putMinCommandLength(value: Int?) {
        clientSettingRepository.putMinCommandLength(value)
    }

    suspend fun getMaxTypeAhead(characterId: String): String? =
        characterSettingsRepository
            .observe(
                characterId = characterId,
                key = MAX_TYPE_AHEAD_KEY,
            ).first()

    fun saveMaxTypeAhead(
        characterId: String,
        value: String,
    ) {
        viewModelScope.launch {
            characterSettingsRepository.save(
                characterId = characterId,
                key = MAX_TYPE_AHEAD_KEY,
                value = value,
            )
        }
    }

    suspend fun getScriptCommandPrefix(characterId: String): String? =
        characterSettingsRepository.get(characterId, SCRIPT_COMMAND_PREFIX_KEY)

    suspend fun saveScriptCommandPrefix(
        characterId: String,
        value: String,
    ) {
        characterSettingsRepository.save(
            characterId = characterId,
            SCRIPT_COMMAND_PREFIX_KEY,
            value,
        )
    }

    fun putMarkLinks(value: Boolean) {
        viewModelScope.launch { clientSettingRepository.putMarkLinks(value) }
    }

    fun putShowImages(value: Boolean) {
        viewModelScope.launch { clientSettingRepository.putShowImages(value) }
    }

    fun putSuppressPrompts(value: Boolean) {
        viewModelScope.launch { clientSettingRepository.putSuppressPrompts(value) }
    }

    fun putAutoConnectLastConnection(value: Boolean) {
        viewModelScope.launch { clientSettingRepository.putAutoConnectLastConnection(value) }
    }

    fun addScriptDir(path: String) {
        val characterId = characterId()
        viewModelScope.launch { scriptDirRepository.save(characterId, path) }
    }

    fun deleteScriptDir(path: String) {
        val characterId = characterId()
        viewModelScope.launch {
            scriptDirRepository.delete(
                characterId = characterId,
                path = path,
            )
        }
    }

    fun putTheme(value: ThemeSetting) {
        viewModelScope.launch { clientSettingRepository.putTheme(value) }
    }

    fun putReleaseChannel(value: ReleaseChannelSetting) {
        viewModelScope.launch { clientSettingRepository.putReleaseChannel(value) }
    }

    fun putSkinFile(value: String?) {
        viewModelScope.launch { clientSettingRepository.putSkinFile(value) }
    }

    fun putLoggingPath(value: String) {
        viewModelScope.launch { clientSettingRepository.putLoggingPath(value) }
    }

    fun putLoggingType(value: LogType) {
        viewModelScope.launch { clientSettingRepository.putLoggingType(value) }
    }

    fun putLoggingTimestamps(value: Boolean) {
        viewModelScope.launch { clientSettingRepository.putLoggingTimestamps(value) }
    }

    /** Imports a Wrayth settings file for the selected character, then hands [onResult] its messages. */
    fun importWraythFile(
        path: String,
        onResult: (List<String>) -> Unit,
    ) {
        val characterId = characterId()
        viewModelScope.launch {
            onResult(wraythImporter.importFile(characterId, Path(path)))
        }
    }

    private fun characterId(): String = _currentCharacter.value?.id ?: GLOBAL_CHARACTER_ID
}
