package cc.warlock.warlock3.app.viewmodel

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import cc.warlock.warlock3.core.ClientCompassEvent
import cc.warlock.warlock3.core.compass.DirectionType
import cc.warlock.warlock3.stormfront.network.StormfrontClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import org.jetbrains.skia.Image
import java.util.*

class CompassViewModel(
    private val client: StormfrontClient
) {
    private val _compassState = MutableStateFlow(CompassState(emptySet()))
    val compassState = _compassState.asStateFlow()
    val theme = loadTheme()

    private val scope = CoroutineScope(Dispatchers.IO)

    init {
        scope.launch {
            client.eventFlow.collect { event ->
                if (event is ClientCompassEvent) {
                    _compassState.value = CompassState(directions = event.directions.toSet())
                }
            }
        }
    }
}

fun loadTheme(): CompassTheme {
    val properties = Properties()
    val stream = object {}.javaClass.getResourceAsStream("/images/compass/theme.properties")
        ?: throw Exception("Could not find compass theme file")
    properties.load(stream)
    stream.close()

    val directions = DirectionType.values().map { direction ->
        val xy = properties.getProperty("position.${direction.value}").split(",")
        val position = Pair(xy[0].toInt(), xy[1].toInt())
        val imageStream = object {}.javaClass.getResourceAsStream("/images/compass/${direction.value}_on.png")
            ?: throw Exception("Could not load compass image")
        val image = Image.makeFromEncoded(imageStream.readBytes()).toComposeImageBitmap()

        Pair(
            direction,
            CompassDirection(
                direction = direction,
                position = position,
                image = image,
            )
        )
    }.toMap()
    val mainImageStream = object {}.javaClass.getResourceAsStream("/images/compass/compass_main.png")
        ?: throw Exception("Could not load compass background")
    val mainImage = Image.makeFromEncoded(mainImageStream.readBytes()).toComposeImageBitmap()
    return CompassTheme(
        background = mainImage,
        description = properties.getProperty("theme.description", ""),
        directions = directions
    )
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