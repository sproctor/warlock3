package warlockfe.warlock3.compose

import co.touchlab.kermit.Logger
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import warlockfe.warlock3.compose.generated.resources.Res
import warlockfe.warlock3.compose.util.SkinLoader

/**
 * Watch the user's configured skin file and keep [AppContainer.skin] in sync.
 *
 * The pipeline (observe the setting -> read bytes -> parse -> publish, logging any parse failure) is
 * identical on every platform; only sourcing the bytes from a user-selected file differs.
 * [readSkinFile] returns the bytes for a configured skin path, or null to fall back to the bundled
 * `skin.zip`. iOS has no user skin file, so it omits [readSkinFile] and always uses the bundle.
 */
fun AppContainer.observeSkin(
    logger: Logger,
    readSkinFile: (path: String) -> ByteArray? = { null },
) {
    clientSettings
        .observeSkinFile()
        .onEach { skinFile ->
            val bytes = skinFile?.let { readSkinFile(it) } ?: Res.readBytes("files/skin.zip")
            try {
                skin.value = SkinLoader.parse(bytes)
            } catch (e: Exception) {
                // TODO: notify user of error
                logger.e(e) { "Failed to load skin file" }
            }
        }.launchIn(externalScope)
}
