package warlockfe.warlock3.compose.ui.game

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.painterResource
import warlockfe.warlock3.compose.generated.resources.Res
import warlockfe.warlock3.compose.generated.resources.front_hand
import warlockfe.warlock3.compose.generated.resources.star_shine
import warlockfe.warlock3.compose.util.mirror
import warlockfe.warlock3.core.client.HandBlock

/** The hands row: the [blocks] that are shown, in order, the hands with their icons and the rest with their labels. */
@Composable
fun HandsView(
    blocks: List<HandBlock>,
    left: String?,
    right: String?,
    spell: String?,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        for (block in blocks) {
            if (!block.shown) continue
            when (block.id) {
                HandBlock.LEFT -> {
                    HandBox(
                        icon = {
                            Icon(
                                modifier = Modifier.rotate(90f).mirror(),
                                painter = painterResource(Res.drawable.front_hand),
                                contentDescription = "Left hand",
                            )
                        },
                        value = left ?: "",
                    )
                }

                HandBlock.RIGHT -> {
                    HandBox(
                        icon = {
                            Icon(
                                modifier = Modifier.rotate(-90f),
                                painter = painterResource(Res.drawable.front_hand),
                                contentDescription = "Right hand",
                            )
                        },
                        value = right ?: "",
                    )
                }

                HandBlock.SPELL -> {
                    HandBox(
                        icon = {
                            Icon(
                                painter = painterResource(Res.drawable.star_shine),
                                contentDescription = "Spell",
                            )
                        },
                        value = spell ?: "",
                    )
                }

                else -> {
                    HandBox(
                        icon = { Text(text = block.label ?: block.id, maxLines = 1, color = MaterialTheme.colorScheme.onSurfaceVariant) },
                        value = block.value ?: "",
                    )
                }
            }
        }
    }
}

@Composable
fun RowScope.HandBox(
    icon: @Composable () -> Unit,
    value: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = MaterialTheme.shapes.small,
        modifier = modifier.weight(1f),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Row(
            modifier = Modifier.padding(4.dp),
        ) {
            icon()
            Spacer(Modifier.width(8.dp))
            Text(
                text = value,
                maxLines = 1,
            )
        }
    }
}

@Preview
@Composable
private fun HandsViewPreview() {
    HandsView(blocks = HandBlock.HANDS, left = "some item", right = "", spell = "a spell")
}
