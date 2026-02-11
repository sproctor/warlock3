package warlockfe.warlock3.wrayth.settings

import kotlinx.io.buffered
import kotlinx.io.files.FileSystem
import kotlinx.io.files.Path
import kotlinx.io.readByteArray
import kotlinx.serialization.decodeFromString
import nl.adaptivity.xmlutil.serialization.XML
import warlockfe.warlock3.core.prefs.models.Highlight
import warlockfe.warlock3.core.prefs.models.MacroEntity
import warlockfe.warlock3.core.prefs.models.NameEntity
import warlockfe.warlock3.core.prefs.repositories.HighlightRepository
import warlockfe.warlock3.core.prefs.repositories.NameRepository
import warlockfe.warlock3.core.text.StyleDefinition
import warlockfe.warlock3.core.text.WarlockColor
import warlockfe.warlock3.core.util.decodeWindows1252
import warlockfe.warlock3.core.util.toWarlockColor
import kotlin.uuid.Uuid

class WraythImporter(
    private val highlightRepository: HighlightRepository,
    private val nameRepository: NameRepository,
    private val fileSystem: FileSystem,
) {
    suspend fun importFile(characterId: String, file: Path): Boolean {
        try {
            val source = fileSystem.source(file).buffered()
            val contents = source.readByteArray().decodeWindows1252()
            val wraythSettings = importString(contents)
            val settings = translateSettings(wraythSettings, characterId)

            settings.highlights.forEach { highlight ->
                highlightRepository.save(
                    characterId = characterId,
                    highlight = highlight
                )
            }
            settings.names.forEach { name ->
                nameRepository.save(name)
            }
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    internal fun translateSettings(settings: WraythSettings, characterId: String): WarlockSettings {
        val colors = mutableMapOf<String, String>()
        settings.palette.forEach { color ->
            if (color.id != null && color.color != null) {
                colors[color.id] = color.color
            }
        }
        return WarlockSettings(
            highlights = settings.strings.mapNotNull { highlight ->
                highlight.text?.let { text ->
                    Highlight(
                        id = Uuid.random(),
                        pattern = text,
                        styles = mapOf(
                            0 to StyleDefinition(
                                textColor = highlight.color.toWarlockColor(colors),
                                backgroundColor = highlight.bgcolor.toWarlockColor(colors),
                                entireLine = highlight.line == "y",
                            )
                        ),
                        isRegex = false,
                        matchPartialWord = true,
                        ignoreCase = false,
                        sound = highlight.sound,
                    )
                }
            },
            names = settings.names.mapNotNull { name ->
                name.text?.let { text ->
                    NameEntity(
                        id = Uuid.random(),
                        characterId = characterId,
                        text = text,
                        textColor = name.color.toWarlockColor(colors),
                        backgroundColor = name.bgcolor.toWarlockColor(colors),
                        bold = false,
                        italic = false,
                        underline = false,
                        fontFamily = null,
                        fontSize = null,
                        sound = name.sound,
                    )
                }
            },
            macros = settings.macroSets.firstOrNull { it.id == "0" }
                ?.macros?.map { wraythMacro ->
                    TODO()
                    MacroEntity(
                        characterId = characterId,
                        key = "",
                        value = wraythMacro.action,
                        keyCode = 0,
                        ctrl = false,
                        alt = false,
                        shift = false,
                        meta = false,
                    )
                } ?: emptyList(),
            ignoredMacros = emptyList(),
        )
    }

    internal fun importString(text: String): WraythSettings {
        val parser = XML {
            defaultPolicy {
                pedantic = false
                ignoreUnknownChildren()
            }
        }
        return parser.decodeFromString<WraythSettings>(text)
    }
}

private fun String?.toWarlockColor(colors: Map<String, String>): WarlockColor {
    if (this == null) return WarlockColor.Unspecified
    val hex = if (startsWith("@")) {
        val key = removePrefix("@")
        colors[key]
    } else {
        this
    }
    return hex?.toWarlockColor() ?: WarlockColor.Unspecified
}

internal data class WarlockSettings(
    val highlights: List<Highlight>,
    val names: List<NameEntity>,
    val macros: List<MacroEntity>,
    val ignoredMacros: List<WraythMacro>,
)
