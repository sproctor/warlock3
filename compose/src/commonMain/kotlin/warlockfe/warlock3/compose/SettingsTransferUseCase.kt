package warlockfe.warlock3.compose

import kotlinx.serialization.json.Json
import warlockfe.warlock3.core.prefs.export.CharacterExport
import warlockfe.warlock3.core.prefs.export.WarlockExport
import warlockfe.warlock3.core.prefs.export.WarlockExportFile
import warlockfe.warlock3.core.prefs.repositories.ExportRepository
import warlockfe.warlock3.core.prefs.repositories.ImportMode

/**
 * Serializes settings to / from the on-disk export format and drives the underlying
 * [ExportRepository]. Keeps file (de)serialization out of the UI layer; callers handle only the
 * file bytes and the user's import choices.
 */
class SettingsTransferUseCase(
    private val exportRepository: ExportRepository,
) {
    private val json =
        Json {
            prettyPrint = true
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

    /** Serialize the entire setup (accounts, connections, every character, global settings). */
    suspend fun exportAll(): String =
        json.encodeToString(WarlockExportFile.serializer(), WarlockExportFile.Full(exportRepository.getExport()))

    /** Serialize a single character's settings. */
    suspend fun exportCharacter(characterId: String): String =
        json.encodeToString(
            WarlockExportFile.serializer(),
            WarlockExportFile.SingleCharacter(exportRepository.getCharacterExport(characterId)),
        )

    /** Parse a previously exported file. Throws if the text is not a valid export. */
    fun parse(text: String): WarlockExportFile = json.decodeFromString(WarlockExportFile.serializer(), text)

    suspend fun importFull(
        export: WarlockExport,
        resolutions: Map<String, ImportMode>,
    ) = exportRepository.importFull(export, resolutions)

    suspend fun importCharacter(
        source: CharacterExport,
        targetCharacterId: String,
        mode: ImportMode,
    ) = exportRepository.importCharacter(source, targetCharacterId, mode)
}
