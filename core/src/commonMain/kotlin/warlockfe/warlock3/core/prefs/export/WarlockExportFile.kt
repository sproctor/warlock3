package warlockfe.warlock3.core.prefs.export

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Top-level, self-describing container written to / read from an export file. The discriminator
 * lets import tell a full backup apart from a single exported character without guessing:
 *  - [Full] restores the entire setup (accounts, connections, every character, global settings).
 *  - [SingleCharacter] carries one character's settings; on import the user picks which existing
 *    character to apply them to.
 */
@Serializable
sealed interface WarlockExportFile {
    @Serializable
    @SerialName("full")
    data class Full(
        val export: WarlockExport,
    ) : WarlockExportFile

    @Serializable
    @SerialName("character")
    data class SingleCharacter(
        val character: CharacterExport,
    ) : WarlockExportFile
}
