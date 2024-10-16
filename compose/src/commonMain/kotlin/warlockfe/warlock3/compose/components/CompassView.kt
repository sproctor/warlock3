package warlockfe.warlock3.compose.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.icerock.moko.resources.ImageResource
import dev.icerock.moko.resources.compose.painterResource
import warlockfe.warlock3.compose.util.LocalLogger
import warlockfe.warlock3.core.compass.DirectionType

@Composable
fun CompassView(
    state: CompassState,
    theme: CompassTheme,
    onClick: (DirectionType) -> Unit
) {
    val logger = LocalLogger.current
    Box(
        modifier = Modifier.padding(4.dp)
    ) {
        Image(painter = painterResource(theme.background), contentDescription = theme.description)
        state.directions.forEach {
            val direction = theme.directions[it]!!
            Image(
                modifier = Modifier
                    .offset(direction.position.first.dp, direction.position.second.dp)
                    .clickable {
                        logger.debug { "Clicked on direction: ${direction.direction}" }
                        onClick(direction.direction)
                    },
                painter = painterResource(direction.image),
                contentDescription = it.value
            )
        }
    }
}

data class CompassState(
    val directions: Set<DirectionType>
)

data class CompassTheme(
    val background: ImageResource,
    val description: String,
    val directions: Map<DirectionType, CompassDirection>
)

data class CompassDirection(
    val direction: DirectionType,
    val position: Pair<Int, Int>,
    val image: ImageResource,
)

//@Preview
//@Composable
//fun EmptyCompassPreview() {
//    CompassView(
//        state = CompassState(directions = emptySet()),
//        theme = loadCompassTheme(),
//        onClick = {}
//    )
//}
//
//@Preview
//@Composable
//fun CompassPreview() {
//    CompassView(
//        state = CompassState(directions = setOf(DirectionType.North, DirectionType.West)),
//        theme = loadCompassTheme(),
//        onClick = {}
//    )
//}
