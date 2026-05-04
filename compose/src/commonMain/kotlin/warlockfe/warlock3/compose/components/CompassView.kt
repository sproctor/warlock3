package warlockfe.warlock3.compose.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.FixedScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import co.touchlab.kermit.Logger
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import warlockfe.warlock3.compose.generated.resources.Res
import warlockfe.warlock3.compose.generated.resources.compass_main
import warlockfe.warlock3.compose.util.LocalSkin
import warlockfe.warlock3.compose.util.loadCompassTheme
import warlockfe.warlock3.core.compass.DirectionType

@Composable
fun CompassView(
    size: Dp,
    state: CompassState,
    onClick: (DirectionType) -> Unit,
    modifier: Modifier = Modifier,
) {
    var compassTheme by remember {
        mutableStateOf(
            CompassTheme(
                size = 63,
                background = Res.drawable.compass_main,
                directions = emptyMap(),
            ),
        )
    }
    val skin = LocalSkin.current
    LaunchedEffect(skin) {
        compassTheme = loadCompassTheme(skin)
    }
    val backgroundPainter = painterResource(compassTheme.background)
    val scale = with(LocalDensity.current) { size.toPx() } / compassTheme.size
    // TODO: Scale compass to fit height instead of using density
    Box(
        modifier = modifier.size(size),
    ) {
        Image(
            painter = backgroundPainter,
            contentDescription = "Compass",
            contentScale = FixedScale(scale),
        )
        state.directions.forEach {
            val direction = compassTheme.directions[it]
            if (direction != null) {
                Image(
                    modifier =
                        Modifier
                            .offset { IntOffset(direction.position.first, direction.position.second) * scale }
                            .clickable {
                                Logger.d { "Clicked on direction: ${direction.direction}" }
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
    val directions: Set<DirectionType>,
)

data class CompassTheme(
    val size: Int,
    val background: DrawableResource,
    val directions: Map<DirectionType, CompassDirection>,
)

data class CompassDirection(
    val direction: DirectionType,
    val position: Pair<Int, Int>,
    val image: DrawableResource,
)

@Preview
@Composable
private fun EmptyCompassPreview() {
    CompassView(
        size = 80.dp,
        state = CompassState(directions = emptySet()),
        onClick = {},
    )
}

@Preview
@Composable
private fun CompassPreview() {
    CompassView(
        size = 80.dp,
        state = CompassState(directions = setOf(DirectionType.North, DirectionType.West)),
        onClick = {},
    )
}
