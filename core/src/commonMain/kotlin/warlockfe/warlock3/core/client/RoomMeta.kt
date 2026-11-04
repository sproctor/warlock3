package warlockfe.warlock3.core.client

/**
 * The current room's `<roommeta>`. [climate], [terrain] and [realm] are the game's numeric codes;
 * [waterLevel] is 0 for dry land up to 3. A null [inside] means the game didn't say.
 */
data class RoomMeta(
    val climate: Int,
    val terrain: Int,
    val realm: Int?,
    val inside: Boolean?,
    val sanctuary: Boolean,
    val bonfire: Boolean,
    val locker: Boolean,
    val waterLevel: Int,
    val weather: RoomWeather?,
)

enum class RoomWeather {
    STORM,
    SNOW,
}
