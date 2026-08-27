package warlockfe.warlock3.wrayth.settings

import kotlinx.io.buffered
import kotlinx.io.files.FileSystem
import kotlinx.io.files.Path
import kotlinx.io.readByteArray
import kotlinx.serialization.decodeFromString
import nl.adaptivity.xmlutil.serialization.XML
import warlockfe.warlock3.core.macro.MacroCommands
import warlockfe.warlock3.core.prefs.config.NameConfig
import warlockfe.warlock3.core.prefs.models.Highlight
import warlockfe.warlock3.core.prefs.models.Ignore
import warlockfe.warlock3.core.prefs.models.IgnoreMatchMode
import warlockfe.warlock3.core.prefs.models.MacroEntity
import warlockfe.warlock3.core.prefs.repositories.HighlightRepository
import warlockfe.warlock3.core.prefs.repositories.IgnoreRepository
import warlockfe.warlock3.core.prefs.repositories.MacroRepository
import warlockfe.warlock3.core.prefs.repositories.NameRepository
import warlockfe.warlock3.core.text.StyleDefinition
import warlockfe.warlock3.core.text.WarlockColor
import warlockfe.warlock3.core.text.toLayer
import warlockfe.warlock3.core.util.decodeWindows1252
import warlockfe.warlock3.core.util.toWarlockColor
import kotlin.uuid.Uuid

class WraythImporter(
    private val highlightRepository: HighlightRepository,
    private val nameRepository: NameRepository,
    private val ignoreRepository: IgnoreRepository,
    private val macroRepository: MacroRepository,
    private val fileSystem: FileSystem,
) {
    suspend fun importFile(
        characterId: String,
        file: Path,
    ): List<String> {
        val messages = mutableListOf<String>()
        try {
            val source = fileSystem.source(file).buffered()
            val contents = source.readByteArray().decodeWindows1252()
            val wraythSettings = importString(contents)
            val settings = translateSettings(wraythSettings, characterId)

            settings.highlights.forEach { highlight ->
                highlightRepository.save(
                    characterId = characterId,
                    highlight = highlight,
                )
            }
            messages.add("Imported ${settings.highlights.size} highlights")
            settings.names.forEach { name ->
                nameRepository.save(characterId, name)
            }
            messages.add("Imported ${settings.names.size} names")
            settings.ignores.forEach { ignore ->
                ignoreRepository.save(characterId, ignore)
            }
            messages.add("Imported ${settings.ignores.size} ignores")
            macroRepository.importMacros(settings.macros)
            messages.add("Imported ${settings.macros.size} macros")
            if (settings.ignoredMacros.isNotEmpty()) {
                messages.add("Ignored ${settings.ignoredMacros.size} invalid macros:")
                settings.ignoredMacros.forEach { ignoredMacro ->
                    messages.add("${ignoredMacro.key} - ${ignoredMacro.action}")
                }
            }
            return messages
        } catch (e: Exception) {
            messages.add(e.stackTraceToString())
            return messages
        }
    }

    internal fun translateSettings(
        settings: WraythSettings,
        characterId: String,
    ): WarlockSettings {
        val colors = mutableMapOf<String, String>()
        settings.palette.forEach { color ->
            if (color.id != null && color.color != null) {
                colors[color.id] = color.color
            }
        }
        val ignoredMacros = mutableListOf<WraythMacro>()
        return WarlockSettings(
            highlights =
                settings.strings.mapNotNull { highlight ->
                    highlight.text?.let { text ->
                        Highlight(
                            id = Uuid.random(),
                            pattern = text,
                            styles =
                                mapOf(
                                    0 to
                                        StyleDefinition(
                                            textColor = highlight.color.toWarlockColor(colors),
                                            backgroundColor = highlight.bgcolor.toWarlockColor(colors),
                                            entireLine = highlight.line == "y",
                                        ).toLayer(),
                                ),
                            isRegex = false,
                            // See WraythHighlight for the verified attribute semantics.
                            matchPartialWord = highlight.word == "y",
                            ignoreCase = highlight.case == "y",
                            sound = highlight.sound,
                        )
                    }
                },
            // word/case semantics verified against the real client (see WraythHighlight): word="y" is
            // "Match Partial Word(s)" (absent = whole word), case="y" is "Ignore Case" (absent =
            // case-sensitive). 1.0.1.28 doesn't persist either flag for ignores, so entries are
            // usually bare, but the attributes are honored when present. The section's disable=
            // master switch has no Warlock equivalent and is not imported. Wrayth has no entire-line
            // ignore, so LINE never comes from an import.
            ignores =
                settings.ignores?.entries.orEmpty().mapNotNull { entry ->
                    entry.text?.let { text ->
                        Ignore(
                            id = Uuid.random(),
                            pattern = text,
                            isRegex = false,
                            matchMode = if (entry.word == "y") IgnoreMatchMode.CONTAINS else IgnoreMatchMode.WORD,
                            ignoreCase = entry.case == "y",
                        )
                    }
                },
            names =
                settings.names.mapNotNull { name ->
                    name.text?.let { text ->
                        NameConfig(
                            id = Uuid.random().toString(),
                            text = text,
                            textColor = name.color.toWarlockColor(colors),
                            backgroundColor = name.bgcolor.toWarlockColor(colors),
                            bold = false,
                            italic = false,
                            underline = false,
                            sound = name.sound,
                        )
                    }
                },
            macros =
                settings.macros
                    .firstOrNull { it.id == "0" }
                    ?.macros
                    ?.mapNotNull { wraythMacro ->
                        var wraythKey = wraythMacro.key
                        wraythKey = wraythKey.replace("Alt-", "")
                        wraythKey = wraythKey.replace("Ctrl-", "")
                        wraythKey = wraythKey.replace("Shift-", "")
                        val keyCode = WraythKeyMapping.keyMap[wraythKey] ?: wraythKey.uppercase()

                        // Map shouldn't have side-effects, I'm lazy
                        if (keyCode.isBlank() || keyCode.contains(' ')) {
                            ignoredMacros.add(wraythMacro)
                            return@mapNotNull null
                        }

                        val validMacroCommands = MacroCommands.commands.map { it.name } + MacroCommands.commands.flatMap { it.aliases }
                        // This is a quick and sloppy way to make sure we can handle the macro command
                        if (wraythMacro.action.startsWith('{') &&
                            !validMacroCommands.contains(
                                wraythMacro.action
                                    .removePrefix("{")
                                    .removeSuffix("}")
                                    .lowercase(),
                            )
                        ) {
                            ignoredMacros.add(wraythMacro)
                            return@mapNotNull null
                        }
                        val keyString =
                            buildString {
                                if (wraythMacro.key.contains("Ctrl-")) {
                                    append("ctrl ")
                                }
                                if (wraythMacro.key.contains("Alt-")) {
                                    append("alt ")
                                }
                                if (wraythMacro.key.contains("Shift-")) {
                                    append("shift ")
                                }
                                append(keyCode)
                            }
                        MacroEntity(
                            characterId = characterId,
                            key = keyString,
                            value = wraythMacro.action,
                            keyCode = 0,
                            ctrl = false,
                            alt = false,
                            shift = false,
                            meta = false,
                        )
                    } ?: emptyList(),
            ignoredMacros = ignoredMacros,
        )
    }

    internal fun importString(text: String): WraythSettings {
        val parser =
            XML.v1 {
                policy {
                    pedantic = false
                    ignoreUnknownChildren()
                }
            }
        return parser.decodeFromString<WraythSettings>(text)
    }
}

private fun String?.toWarlockColor(colors: Map<String, String>): WarlockColor {
    if (this == null) return WarlockColor.Unspecified
    val hex =
        if (startsWith("@")) {
            val key = removePrefix("@")
            colors[key]
        } else {
            this
        }
    return hex?.toWarlockColor() ?: WarlockColor.Unspecified
}

internal data class WarlockSettings(
    val highlights: List<Highlight>,
    val ignores: List<Ignore>,
    val names: List<NameConfig>,
    val macros: List<MacroEntity>,
    val ignoredMacros: List<WraythMacro>,
)
