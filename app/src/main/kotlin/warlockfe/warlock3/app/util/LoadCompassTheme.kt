package warlockfe.warlock3.app.util

import androidx.compose.ui.graphics.toComposeImageBitmap
import warlockfe.warlock3.app.components.CompassDirection
import warlockfe.warlock3.app.components.CompassTheme
import warlockfe.warlock3.core.compass.DirectionType
import org.jetbrains.skia.Image
import java.util.*

fun loadCompassTheme(): CompassTheme {
    val properties = Properties()
    val stream = object {}.javaClass.getResourceAsStream("/images/compass/theme.properties")
        ?: throw Exception("Could not find compass theme file")
    properties.load(stream)
    stream.close()

    val directions = DirectionType.entries.associateWith { direction ->
        val xy = properties.getProperty("position.${direction.value}").split(",")
        val position = Pair(xy[0].toInt(), xy[1].toInt())
        val imageStream = object {}.javaClass.getResourceAsStream("/images/compass/${direction.value}_on.png")
            ?: throw Exception("Could not load compass image")
        val image = Image.makeFromEncoded(imageStream.readBytes()).toComposeImageBitmap()

        CompassDirection(
            direction = direction,
            position = position,
            image = image,
        )
    }
    val mainImageStream = object {}.javaClass.getResourceAsStream("/images/compass/compass_main.png")
        ?: throw Exception("Could not load compass background")
    val mainImage = Image.makeFromEncoded(mainImageStream.readBytes()).toComposeImageBitmap()
    return CompassTheme(
        background = mainImage,
        description = properties.getProperty("theme.description", ""),
        directions = directions
    )
}