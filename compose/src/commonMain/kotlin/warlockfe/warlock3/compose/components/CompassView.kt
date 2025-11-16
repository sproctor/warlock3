package warlockfe.warlock3.compose.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.FixedScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import warlockfe.warlock3.compose.util.LocalCompassTheme
import warlockfe.warlock3.compose.util.LocalLogger
import warlockfe.warlock3.core.compass.DirectionType

@Composable
fun CompassView(
    modifier: Modifier,
    state: CompassState,
    onClick: (DirectionType) -> Unit
) {
    val scale = LocalDensity.current.density * 1.5f
    val logger = LocalLogger.current
    val theme = LocalCompassTheme.current
    val backgroundPainter = painterResource(theme.background)
    // TODO: Scale compass to fit height instead of using density
    Box(
        modifier = modifier
    ) {
        Image(
            painter = backgroundPainter,
            contentDescription = "Compass",
            contentScale = FixedScale(scale),
        )
        state.directions.forEach {
            val direction = theme.directions[it]
            if (direction != null) {
                Image(
                    modifier = Modifier
                        .offset { IntOffset(direction.position.first, direction.position.second) * scale }
                        .clickable {
                            logger.debug { "Clicked on direction: ${direction.direction}" }
                            onClick(direction.direction)
                        },
                    painter = painterResource(direction.image),
                    contentDescription = it.value,
                    contentScale = FixedScale(scale),
                )
            }
        }
    }
}

data class CompassState(
    val directions: Set<DirectionType>
)

data class CompassTheme(
    val background: DrawableResource,
    val directions: Map<DirectionType, CompassDirection>
)

data class CompassDirection(
    val direction: DirectionType,
    val position: Pair<Int, Int>,
    val image: DrawableResource,
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
