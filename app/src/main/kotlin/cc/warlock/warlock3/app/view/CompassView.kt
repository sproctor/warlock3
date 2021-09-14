package cc.warlock.warlock3.app.view

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cc.warlock.warlock3.app.viewmodel.CompassState
import cc.warlock.warlock3.app.viewmodel.CompassTheme
import cc.warlock.warlock3.app.viewmodel.loadTheme
import cc.warlock.warlock3.core.compass.DirectionType

@Composable
fun CompassView(state: CompassState, theme: CompassTheme) {
    Box(
        modifier = Modifier.padding(4.dp)
    ) {
        Image(bitmap = theme.background, contentDescription = theme.description)
        state.directions.forEach {
            val direction = theme.directions[it]!!
            Image(
                modifier = Modifier.offset(direction.position.first.dp, direction.position.second.dp),
                bitmap = direction.image,
                contentDescription = it.value
            )
        }
    }
}

@Preview
@Composable
fun EmptyCompassPreview() {
    CompassView(
        state = CompassState(directions = emptySet()),
        theme = loadTheme()
    )
}

@Preview
@Composable
fun CompassPreview() {
    CompassView(
        state = CompassState(directions = setOf(DirectionType.North, DirectionType.West)),
        theme = loadTheme()
    )
}