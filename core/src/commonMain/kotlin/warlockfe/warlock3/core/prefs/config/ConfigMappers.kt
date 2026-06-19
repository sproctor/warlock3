package warlockfe.warlock3.core.prefs.config

import warlockfe.warlock3.core.client.GameCharacter
import warlockfe.warlock3.core.prefs.models.Action
import warlockfe.warlock3.core.prefs.models.AliasEntity
import warlockfe.warlock3.core.prefs.models.AlterationEntity
import warlockfe.warlock3.core.prefs.models.Highlight
import warlockfe.warlock3.core.prefs.models.NameEntity
import warlockfe.warlock3.core.prefs.models.ProgressBarSettingEntity
import warlockfe.warlock3.core.sge.ConnectionProxySettings
import warlockfe.warlock3.core.sge.StoredConnection
import warlockfe.warlock3.core.text.StyleDefinition
import kotlin.uuid.Uuid

private fun String?.toUuidOrRandom(): Uuid = this?.let { runCatching { Uuid.parse(it) }.getOrNull() } ?: Uuid.random()

private fun String.toUuidOrNull(): Uuid? = runCatching { Uuid.parse(this) }.getOrNull()

internal fun ActionConfig.toAction(): Action =
    Action(
        id = id.toUuidOrRandom(),
        name = name,
        script = script,
        children = children.mapNotNull { it.toUuidOrNull() },
    )

internal fun Action.toConfig(): ActionConfig =
    ActionConfig(
        id = id.toString(),
        name = name,
        script = script,
        children = children.map { it.toString() },
    )

internal fun HighlightConfig.toHighlight(): Highlight =
    Highlight(
        id = id?.let { runCatching { Uuid.parse(it) }.getOrNull() } ?: Uuid.random(),
        pattern = pattern,
        styles = styles.associate { it.group to it.toStyleDefinition() },
        isRegex = isRegex,
        matchPartialWord = matchPartialWord,
        ignoreCase = ignoreCase,
        sound = sound,
    )

internal fun Highlight.toConfig(): HighlightConfig =
    HighlightConfig(
        id = id.toString(),
        pattern = pattern,
        isRegex = isRegex,
        matchPartialWord = matchPartialWord,
        ignoreCase = ignoreCase,
        sound = sound,
        styles = styles.map { (group, style) -> style.toStyleConfig(group) },
    )

internal fun HighlightStyleConfig.toStyleDefinition(): StyleDefinition =
    StyleDefinition(
        textColor = textColor,
        backgroundColor = backgroundColor,
        entireLine = entireLine,
        bold = bold,
        italic = italic,
        underline = underline,
        fontFamily = fontFamily,
        fontSize = fontSize,
        fontWeight = fontWeight,
    )

internal fun StyleDefinition.toStyleConfig(group: Int): HighlightStyleConfig =
    HighlightStyleConfig(
        group = group,
        textColor = textColor,
        backgroundColor = backgroundColor,
        entireLine = entireLine,
        bold = bold,
        italic = italic,
        underline = underline,
        fontFamily = fontFamily,
        fontSize = fontSize,
        fontWeight = fontWeight,
    )

internal fun NameConfig.toEntity(characterId: String): NameEntity =
    NameEntity(
        id = id?.let { runCatching { Uuid.parse(it) }.getOrNull() } ?: Uuid.random(),
        characterId = characterId,
        text = text,
        textColor = textColor,
        backgroundColor = backgroundColor,
        bold = bold,
        italic = italic,
        underline = underline,
        fontFamily = fontFamily,
        fontSize = fontSize,
        fontWeight = fontWeight,
        sound = sound,
    )

internal fun NameEntity.toConfig(): NameConfig =
    NameConfig(
        id = id.toString(),
        text = text,
        sound = sound,
        textColor = textColor,
        backgroundColor = backgroundColor,
        bold = bold,
        italic = italic,
        underline = underline,
        fontFamily = fontFamily,
        fontSize = fontSize,
        fontWeight = fontWeight,
    )

internal fun AliasConfig.toEntity(characterId: String): AliasEntity =
    AliasEntity(
        id = id.toUuidOrRandom(),
        characterId = characterId,
        pattern = pattern,
        replacement = replacement,
    )

internal fun AliasEntity.toConfig(): AliasConfig =
    AliasConfig(
        id = id.toString(),
        pattern = pattern,
        replacement = replacement,
    )

internal fun AlterationConfig.toEntity(characterId: String): AlterationEntity =
    AlterationEntity(
        id = id.toUuidOrRandom(),
        characterId = characterId,
        pattern = pattern,
        sourceStream = sourceStream,
        destinationStream = destinationStream,
        result = result,
        ignoreCase = ignoreCase,
        keepOriginal = keepOriginal,
    )

internal fun AlterationEntity.toConfig(): AlterationConfig =
    AlterationConfig(
        id = id.toString(),
        pattern = pattern,
        sourceStream = sourceStream,
        destinationStream = destinationStream,
        result = result,
        ignoreCase = ignoreCase,
        keepOriginal = keepOriginal,
    )

internal fun PresetStyleConfig.toStyleDefinition(): StyleDefinition =
    StyleDefinition(
        textColor = textColor,
        backgroundColor = backgroundColor,
        entireLine = entireLine,
        bold = bold,
        italic = italic,
        underline = underline,
        fontFamily = fontFamily,
        fontSize = fontSize,
        fontWeight = fontWeight,
    )

internal fun StyleDefinition.toPresetStyleConfig(): PresetStyleConfig =
    PresetStyleConfig(
        textColor = textColor,
        backgroundColor = backgroundColor,
        entireLine = entireLine,
        bold = bold,
        italic = italic,
        underline = underline,
        fontFamily = fontFamily,
        fontSize = fontSize,
        fontWeight = fontWeight,
    )

// Window styling carries only the visual style fields the window settings UI edits; geometry lives
// in SQLite and the bold/italic/underline/entireLine flags aren't part of per-window styling.
internal fun WindowStyleConfig.toStyleDefinition(): StyleDefinition =
    StyleDefinition(
        textColor = textColor,
        backgroundColor = backgroundColor,
        fontFamily = fontFamily,
        fontSize = fontSize,
        fontWeight = fontWeight,
    )

internal fun StyleDefinition.toWindowStyleConfig(nameFilter: Boolean): WindowStyleConfig =
    WindowStyleConfig(
        textColor = textColor,
        backgroundColor = backgroundColor,
        fontFamily = fontFamily,
        fontSize = fontSize,
        fontWeight = fontWeight,
        nameFilter = nameFilter,
    )

internal fun ProgressBarConfig.toEntity(
    characterId: String,
    id: String,
): ProgressBarSettingEntity =
    ProgressBarSettingEntity(
        characterId = characterId,
        id = id,
        barColor = barColor,
        backgroundColor = backgroundColor,
        textColor = textColor,
    )

internal fun ProgressBarSettingEntity.toConfig(): ProgressBarConfig =
    ProgressBarConfig(
        barColor = barColor,
        backgroundColor = backgroundColor,
        textColor = textColor,
    )

internal fun CharacterEntry.toGameCharacter(): GameCharacter =
    GameCharacter(
        id = id,
        gameCode = gameCode,
        name = name,
    )

internal fun GameCharacter.toCharacterEntry(): CharacterEntry =
    CharacterEntry(
        id = id,
        gameCode = gameCode,
        name = name,
    )

// The password is joined in from the SQLite account table (credentials stay out of plaintext TOML).
internal fun ConnectionConfig.toStoredConnection(password: String?): StoredConnection =
    StoredConnection(
        id = id,
        name = name,
        username = username,
        password = password,
        character = character,
        code = gameCode,
        proxySettings =
            ConnectionProxySettings(
                enabled = proxyEnabled,
                launchCommand = proxyLaunchCommand,
                host = proxyHost,
                port = proxyPort,
            ),
        mudMobile = mudMobile,
        characterCode = characterCode,
    )
