package warlockfe.warlock3.compose.ui.window

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import coil3.compose.AsyncImage
import org.jetbrains.compose.resources.painterResource
import warlockfe.warlock3.compose.generated.resources.Res
import warlockfe.warlock3.compose.generated.resources.allDrawableResources
import warlockfe.warlock3.compose.generated.resources.broken_image
import warlockfe.warlock3.compose.model.SkinObject
import warlockfe.warlock3.compose.util.LocalSkin
import warlockfe.warlock3.compose.util.getColorGroup
import warlockfe.warlock3.core.util.getIgnoringCase
import kotlin.io.encoding.Base64

@Composable
internal fun PanelImage(
    skinObject: SkinObject?,
    name: String?,
    contentColor: Color,
    onClick: (() -> Unit)?,
) {
    val skin = LocalSkin.current
    val colorGroup = skinObject.getColorGroup()
    val imageData = name?.let { skin.getIgnoringCase(it) }
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val isPressed by interactionSource.collectIsPressedAsState()
    Box(
        modifier =
            if (onClick != null) {
                Modifier
                    .pointerHoverIcon(PointerIcon.Hand)
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = onClick,
                    )
            } else {
                Modifier
            },
        contentAlignment = Alignment.Center,
    ) {
        val imageEntry = imageData?.takeIf { it.image != null } ?: skinObject
        val image = imageEntry?.image?.data?.let { Base64.decode(it) }
        val drawable = imageEntry?.image?.name?.let { Res.allDrawableResources[it] }
        if (image != null) {
            AsyncImage(image, contentDescription = null)
        } else if (drawable != null) {
            Image(
                painter = painterResource(drawable),
                colorFilter = ColorFilter.tint(imageEntry.getColorGroup().text.takeOrElse { contentColor }),
                contentDescription = null,
            )
        } else {
            Image(
                painter = painterResource(Res.drawable.broken_image),
                colorFilter = ColorFilter.tint(colorGroup.text.takeOrElse { contentColor.copy(alpha = 0.38f) }),
                contentDescription = null,
            )
        }
        if (onClick != null) {
            val overlayAlpha =
                when {
                    isPressed && isHovered -> 0.172f
                    isPressed -> 0.10f
                    isHovered -> 0.08f
                    else -> 0f
                }
            if (overlayAlpha > 0f) {
                Box(
                    modifier =
                        Modifier
                            .matchParentSize()
                            .background(contentColor.copy(alpha = overlayAlpha)),
                )
            }
        }
    }
}
