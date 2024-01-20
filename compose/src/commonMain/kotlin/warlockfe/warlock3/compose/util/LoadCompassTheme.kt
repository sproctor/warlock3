package warlockfe.warlock3.compose.util

import warlockfe.warlock3.compose.components.CompassDirection
import warlockfe.warlock3.compose.components.CompassTheme
import warlockfe.warlock3.compose.resources.MR
import warlockfe.warlock3.core.compass.DirectionType
import java.util.Properties

fun loadCompassTheme(themeProperties: Properties): CompassTheme {

    val directions = DirectionType.entries.associateWith { direction ->
        val xy = themeProperties.getProperty("position.${direction.value}").split(",")
        val position = Pair(xy[0].toInt(), xy[1].toInt())
        val image = when (direction) {
            DirectionType.North -> MR.images.north_on
            DirectionType.Northwest -> MR.images.northeast_on
            DirectionType.Down -> MR.images.down_on
            DirectionType.East -> MR.images.east_on
            DirectionType.Out -> MR.images.out_on
            DirectionType.Northeast -> MR.images.northeast_on
            DirectionType.South -> MR.images.south_on
            DirectionType.Southeast -> MR.images.southeast_on
            DirectionType.Up -> MR.images.up_on
            DirectionType.Southwest -> MR.images.southwest_on
            DirectionType.West -> MR.images.west_on
        }

        CompassDirection(
            direction = direction,
            position = position,
            image = image,
        )
    }
    return CompassTheme(
        background = MR.images.compass_main,
        description = themeProperties.getProperty("theme.description", ""),
        directions = directions
    )
}
