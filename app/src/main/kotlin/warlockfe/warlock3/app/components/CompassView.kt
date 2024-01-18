package warlockfe.warlock3.app.components

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.dp
import warlockfe.warlock3.app.util.loadCompassTheme
import warlockfe.warlock3.core.compass.DirectionType

@Composable
fun CompassView(
    state: CompassState,
    theme: CompassTheme,
    onClick: (DirectionType) -> Unit
) {
    Box(
        modifier = Modifier.padding(4.dp)
    ) {
        Image(bitmap = theme.background, contentDescription = theme.description)
        state.directions.forEach {
            val direction = theme.directions[it]!!
            Image(
                modifier = Modifier
                    .offset(direction.position.first.dp, direction.position.second.dp)
                    .clickable {
                        println("Clicked on direction: ${direction.direction}")
                        onClick(direction.direction)
                    },
                bitmap = direction.image,
                contentDescription = it.value
            )
        }
    }
}

data class CompassState(
    val directions: Set<DirectionType>
)

data class CompassTheme(
    val background: ImageBitmap,
    val description: String,
    val directions: Map<DirectionType, CompassDirection>
)

data class CompassDirection(
    val direction: DirectionType,
    val position: Pair<Int, Int>,
    val image: ImageBitmap,
)

@Preview
@Composable
fun EmptyCompassPreview() {
    CompassView(
        state = CompassState(directions = emptySet()),
        theme = loadCompassTheme(),
        onClick = {}
    )
}

@Preview
@Composable
fun CompassPreview() {
    CompassView(
        state = CompassState(directions = setOf(DirectionType.North, DirectionType.West)),
        theme = loadCompassTheme(),
        onClick = {}
    )
}