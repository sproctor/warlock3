package warlockfe.warlock3.compose.util

import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.scale

@Stable
fun Modifier.mirror(): Modifier = scale(scaleX = -1f, scaleY = 1f)