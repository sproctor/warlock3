package warlockfe.warlock3.compose.ui.components

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.unit.dp
import warlockfe.warlock3.compose.icons.Front_hand
import warlockfe.warlock3.compose.icons.Magic_button
import warlockfe.warlock3.compose.util.mirror

@Composable
fun HandsView(properties: Map<String, String>) {
    HandsViewContent(
        left = properties["left"] ?: "",
        right = properties["right"] ?: "",
        spell = properties["spell"] ?: ""
    )
}

@Composable
fun HandsViewContent(left: String, right: String, spell: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        HandBox(
            icon = {
                Icon(
                    modifier = Modifier.rotate(90f).mirror(),
                    imageVector = Front_hand,
                    contentDescription = "Left hand",
                )
            },
            value = left
        )
        HandBox(
            icon = {
                Icon(
                    modifier = Modifier.rotate(-90f),
                    imageVector = Front_hand,
                    contentDescription = "Right hand",
                )
            },
            value = right
        )
        HandBox(
            icon = {
                Icon(
                    imageVector = Magic_button,
                    contentDescription = "Spell",
                )
            },
            value = spell,
        )
    }
}

@Composable
fun RowScope.HandBox(icon: @Composable () -> Unit, value: String) {
    Surface(
        shape = MaterialTheme.shapes.small,
        modifier = Modifier
            .weight(1f)
            .padding(4.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Row(
            modifier = Modifier.padding(4.dp)
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

//@Preview
//@Composable
//fun HandsViewPreview() {
//    HandsViewContent(left = "some item", right = "", spell = "a spell")
//}
