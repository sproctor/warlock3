package cc.warlock.warlock3.app.model

data class GameCharacter(
    val gameCode: String,
    val characterName: String,
) {
    val key: String = "${gameCode.lowercase()}:${characterName.lowercase()}"
}