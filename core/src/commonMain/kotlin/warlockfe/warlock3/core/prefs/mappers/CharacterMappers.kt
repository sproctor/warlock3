package warlockfe.warlock3.core.prefs.mappers

import warlockfe.warlock3.core.client.GameCharacter
import warlockfe.warlock3.core.prefs.models.CharacterEntity

fun CharacterEntity.toGameCharacter(): GameCharacter {
    return GameCharacter(
        accountId = accountId,
        id = id,
        gameCode = gameCode,
        name = name,
    )
}

fun GameCharacter.toEntity(): CharacterEntity {
    return CharacterEntity(
        accountId = accountId,
        id = id,
        gameCode = gameCode,
        name = name
    )
}
