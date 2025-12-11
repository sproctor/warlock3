package warlockfe.warlock3.compose.util

import warlockfe.warlock3.compose.components.CompassDirection
import warlockfe.warlock3.compose.components.CompassTheme
import warlockfe.warlock3.compose.generated.resources.Res
import warlockfe.warlock3.compose.generated.resources.compass_main
import warlockfe.warlock3.compose.generated.resources.down_on
import warlockfe.warlock3.compose.generated.resources.east_on
import warlockfe.warlock3.compose.generated.resources.north_on
import warlockfe.warlock3.compose.generated.resources.northeast_on
import warlockfe.warlock3.compose.generated.resources.northwest_on
import warlockfe.warlock3.compose.generated.resources.out_on
import warlockfe.warlock3.compose.generated.resources.south_on
import warlockfe.warlock3.compose.generated.resources.southeast_on
import warlockfe.warlock3.compose.generated.resources.southwest_on
import warlockfe.warlock3.compose.generated.resources.up_on
import warlockfe.warlock3.compose.generated.resources.west_on
import warlockfe.warlock3.compose.model.SkinObject
import warlockfe.warlock3.core.compass.DirectionType

fun loadCompassTheme(skin: Map<String, SkinObject>): CompassTheme {
    val compassTheme = skin["compass"]
    val directions = DirectionType.entries.associateWith { direction ->
        val skinObject = compassTheme?.children?.get(direction.value)
        val position = Pair(skinObject?.left ?: 0, skinObject?.top ?: 0)
        val image = when (direction) {
            DirectionType.North -> Res.drawable.north_on
            DirectionType.Northwest -> Res.drawable.northwest_on
            DirectionType.Down -> Res.drawable.down_on
            DirectionType.East -> Res.drawable.east_on
            DirectionType.Out -> Res.drawable.out_on
            DirectionType.Northeast -> Res.drawable.northeast_on
            DirectionType.South -> Res.drawable.south_on
            DirectionType.Southeast -> Res.drawable.southeast_on
            DirectionType.Up -> Res.drawable.up_on
            DirectionType.Southwest -> Res.drawable.southwest_on
            DirectionType.West -> Res.drawable.west_on
        }

        CompassDirection(
            direction = direction,
            position = position,
            image = image,
        )
    }
    return CompassTheme(
        size = 63, // Modify this when changing the compass image
        background = Res.drawable.compass_main,
        directions = directions
    )
}
