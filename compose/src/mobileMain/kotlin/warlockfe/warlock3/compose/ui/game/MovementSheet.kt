package warlockfe.warlock3.compose.ui.game

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import warlockfe.warlock3.compose.generated.resources.Res
import warlockfe.warlock3.compose.generated.resources.east
import warlockfe.warlock3.compose.generated.resources.logout
import warlockfe.warlock3.compose.generated.resources.north
import warlockfe.warlock3.compose.generated.resources.north_east
import warlockfe.warlock3.compose.generated.resources.north_west
import warlockfe.warlock3.compose.generated.resources.south
import warlockfe.warlock3.compose.generated.resources.south_east
import warlockfe.warlock3.compose.generated.resources.south_west
import warlockfe.warlock3.compose.generated.resources.stairs_down
import warlockfe.warlock3.compose.generated.resources.stairs_up
import warlockfe.warlock3.compose.generated.resources.west
import warlockfe.warlock3.core.compass.Direction

/**
 * The movement bottom sheet opened by the phone movement FAB: a 3x3 direction grid (with "out" in
 * the centre) plus an up/down row. Lit cells are available exits; tapping one sends the movement
 * command and dismisses the sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MovementSheet(
    directions: Set<Direction>,
    onMove: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        DirectionGrid(
            directions = directions,
            onMove = { value ->
                onMove(value)
                onDismiss()
            },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp),
        )
    }
}

@Composable
private fun DirectionGrid(
    directions: Set<Direction>,
    onMove: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val available = remember(directions) { directions.map { it.value.lowercase() }.toSet() }
    val rows =
        listOf(
            listOf("nw" to Res.drawable.north_west, "n" to Res.drawable.north, "ne" to Res.drawable.north_east),
            listOf("w" to Res.drawable.west, "out" to Res.drawable.logout, "e" to Res.drawable.east),
            listOf("sw" to Res.drawable.south_west, "s" to Res.drawable.south, "se" to Res.drawable.south_east),
        )
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = "Move",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { (value, icon) ->
                    DirectionCell(
                        icon = icon,
                        contentDescription = value,
                        lit = available.contains(value),
                        onClick = { onMove(value) },
                        modifier = Modifier.weight(1f).aspectRatio(1f),
                    )
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
        ) {
            DirectionPill(
                label = "up",
                icon = Res.drawable.stairs_up,
                lit = available.contains("up"),
                onClick = { onMove("up") },
            )
            DirectionPill(
                label = "down",
                icon = Res.drawable.stairs_down,
                lit = available.contains("down"),
                onClick = { onMove("down") },
            )
        }
    }
}

@Composable
private fun DirectionCell(
    icon: DrawableResource,
    contentDescription: String,
    lit: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val container =
        if (lit) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant
    val content =
        if (lit) {
            MaterialTheme.colorScheme.onSecondaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
        }
    Box(
        modifier =
            modifier
                .clip(RoundedCornerShape(18.dp))
                .background(container)
                .then(if (lit) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            modifier = Modifier.fillMaxSize(0.5f),
            painter = painterResource(icon),
            contentDescription = contentDescription,
            tint = content,
        )
    }
}

@Composable
private fun DirectionPill(
    label: String,
    icon: DrawableResource,
    lit: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(20.dp)
    val container = if (lit) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent
    val content =
        if (lit) {
            MaterialTheme.colorScheme.onSecondaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
        }
    Row(
        modifier =
            Modifier
                .clip(shape)
                .background(container)
                .then(
                    if (lit) {
                        Modifier.clickable(onClick = onClick)
                    } else {
                        Modifier.border(1.dp, MaterialTheme.colorScheme.outline, shape)
                    },
                ).height(40.dp)
                .padding(horizontal = 22.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Icon(
            modifier = Modifier.height(20.dp).aspectRatio(1f),
            painter = painterResource(icon),
            contentDescription = null,
            tint = content,
        )
        Text(text = label, color = content, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
    }
}
