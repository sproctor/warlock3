package warlockfe.warlock3.compose.ui.window

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import coil3.compose.AsyncImagePainter
import coil3.compose.LocalPlatformContext
import coil3.compose.rememberAsyncImagePainter
import coil3.request.ImageRequest
import coil3.size.Size
import warlockfe.warlock3.core.window.BackgroundImageHorizontalAlignment
import warlockfe.warlock3.core.window.BackgroundImageMode
import warlockfe.warlock3.core.window.BackgroundImageVerticalAlignment
import warlockfe.warlock3.core.window.ClientBackgroundImage

// Platform-agnostic helpers shared by the desktop (Jewel) and mobile (Material3) window views, which
// previously carried byte-for-byte copies of all of this.

@Composable
internal fun WindowBackgroundImage(
    backgroundImage: ClientBackgroundImage,
    width: Dp,
    height: Dp,
    modifier: Modifier = Modifier,
) {
    val painter =
        rememberAsyncImagePainter(
            ImageRequest
                .Builder(LocalPlatformContext.current)
                .data(backgroundImage.image)
                .size(Size.ORIGINAL)
                .build(),
        )
    val painterState by painter.state.collectAsState()
    val state = painterState as? AsyncImagePainter.State.Success ?: return
    val imageHeight =
        state.result.image.height
            .takeIf { it > 0 } ?: return
    val imageWidth =
        state.result.image.width
            .takeIf { it > 0 } ?: return
    val aspectRatio = imageWidth.toFloat() / imageHeight.toFloat()
    val imageWidthDp = with(LocalDensity.current) { imageWidth.toDp() }
    val imageHeightDp = with(LocalDensity.current) { imageHeight.toDp() }
    val scaledWidth = height * aspectRatio
    val scaledHeight = width / aspectRatio
    val imageModifier =
        when (backgroundImage.mode) {
            BackgroundImageMode.FILL -> {
                modifier.fillMaxSize()
            }

            BackgroundImageMode.HEIGHT_FILL,
            BackgroundImageMode.GRADIENT,
            -> {
                modifier
                    .fillMaxHeight()
                    .width(scaledWidth)
            }

            BackgroundImageMode.WIDTH_FILL -> {
                modifier
                    .fillMaxWidth()
                    .height(scaledHeight)
            }

            BackgroundImageMode.FULL -> {
                modifier
                    .requiredWidth(imageWidthDp)
                    .requiredHeight(imageHeightDp)
            }
        }.then(backgroundImage.opacityModifier())
            .then(backgroundImage.gradientModifier())

    Image(
        modifier = imageModifier,
        painter = painter,
        contentDescription = null,
        contentScale = backgroundImage.mode.contentScale(),
    )
}

internal fun ClientBackgroundImage.backgroundAlignment(): Alignment =
    when (verticalAlignment) {
        BackgroundImageVerticalAlignment.TOP -> {
            when (horizontalAlignment) {
                BackgroundImageHorizontalAlignment.LEFT -> Alignment.TopStart
                BackgroundImageHorizontalAlignment.CENTER -> Alignment.TopCenter
                BackgroundImageHorizontalAlignment.RIGHT -> Alignment.TopEnd
            }
        }

        BackgroundImageVerticalAlignment.MIDDLE -> {
            when (horizontalAlignment) {
                BackgroundImageHorizontalAlignment.LEFT -> Alignment.CenterStart
                BackgroundImageHorizontalAlignment.CENTER -> Alignment.Center
                BackgroundImageHorizontalAlignment.RIGHT -> Alignment.CenterEnd
            }
        }

        BackgroundImageVerticalAlignment.BOTTOM -> {
            when (horizontalAlignment) {
                BackgroundImageHorizontalAlignment.LEFT -> Alignment.BottomStart
                BackgroundImageHorizontalAlignment.CENTER -> Alignment.BottomCenter
                BackgroundImageHorizontalAlignment.RIGHT -> Alignment.BottomEnd
            }
        }
    }

internal fun BackgroundImageMode.contentScale(): ContentScale =
    when (this) {
        BackgroundImageMode.FILL -> ContentScale.FillBounds

        BackgroundImageMode.WIDTH_FILL -> ContentScale.FillWidth

        BackgroundImageMode.FULL -> ContentScale.None

        BackgroundImageMode.HEIGHT_FILL,
        BackgroundImageMode.GRADIENT,
        -> ContentScale.FillHeight
    }

internal fun ClientBackgroundImage.gradientModifier(): Modifier =
    when (mode) {
        BackgroundImageMode.GRADIENT -> {
            Modifier
                .graphicsLayer {
                    compositingStrategy = CompositingStrategy.Offscreen
                }.drawWithContent {
                    drawContent()
                    drawRect(
                        brush = Brush.horizontalGradient(*gradientColorStops()),
                        blendMode = BlendMode.DstIn,
                    )
                }
        }

        else -> {
            Modifier
        }
    }

internal fun ClientBackgroundImage.gradientColorStops(): Array<Pair<Float, Color>> {
    val start = gradientStart.toPercentFraction()
    val end = gradientEnd.toPercentFraction()
    val transparent = Color.Black.copy(alpha = 0f)
    val opaque = Color.Black.copy(alpha = opacity.toPercentFraction())

    return if (start <= end) {
        arrayOf(
            0f to transparent,
            start to transparent,
            end to opaque,
            1f to opaque,
        )
    } else {
        arrayOf(
            0f to opaque,
            end to opaque,
            start to transparent,
            1f to transparent,
        )
    }
}

internal fun ClientBackgroundImage.opacityModifier(): Modifier =
    if (mode == BackgroundImageMode.GRADIENT || opacity == 100) {
        Modifier
    } else {
        Modifier.graphicsLayer {
            alpha = opacity.toPercentFraction()
        }
    }

internal fun Int.toPercentFraction(): Float = coerceIn(0, 100) / 100f

internal fun StreamTextLine.isShowing(openWindows: List<String>): Boolean =
    this.showWhenClosed == null ||
        openWindows.none {
            it == this.showWhenClosed
        }

internal fun List<StreamLine>.isPreviousPrompt(
    index: Int,
    openWindows: List<String>,
): Boolean {
    var currentIndex = index - 1
    while (currentIndex >= 0) {
        val line = this[currentIndex] as? StreamTextLine ?: return false
        if (line.isShowing(openWindows)) {
            return line.isPrompt
        }
        currentIndex--
    }
    return false
}
