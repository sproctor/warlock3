package warlockfe.warlock3.compose.ui.components

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import coil.compose.AsyncImage

@Composable
actual fun StatusImage(name: String, modifier: Modifier) {
    object {}.javaClass.getResourceAsStream("/images/status/$name.png")?.use {
        Image(bitmap = BitmapFactory.decodeStream(it).asImageBitmap(), modifier = modifier, contentDescription = name)
    } ?: object {}.javaClass.getResourceAsStream("/images/status/$name.svg")?.use {
        AsyncImage(
            modifier = modifier,
            model = it,
            contentDescription = name,
        )
    }
}