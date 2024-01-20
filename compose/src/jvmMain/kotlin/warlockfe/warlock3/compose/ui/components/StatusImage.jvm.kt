package warlockfe.warlock3.compose.ui.components

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.loadSvgPainter
import dev.icerock.moko.resources.compose.painterResource
import dev.icerock.moko.resources.getImageByFileName
import warlockfe.warlock3.compose.resources.MR
import java.io.File

@Composable
actual fun StatusImage(name: String, modifier: Modifier) {
    val density = LocalDensity.current
    val painter =
        MR.images.getImageByFileName("$name.png")?.let {
            painterResource(it)
        } ?: MR.images.getImageByFileName("$name.svg")?.filePath?.let {
            loadSvgPainter(File(it).inputStream(), density)
        }
    if (painter != null) {
        Image(painter = painter, modifier = modifier, contentDescription = name)
    }
}