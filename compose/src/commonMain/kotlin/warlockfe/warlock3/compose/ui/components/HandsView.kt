package warlockfe.warlock3.compose.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.icerock.moko.resources.ImageResource
import dev.icerock.moko.resources.compose.painterResource
import dev.icerock.moko.resources.getImageByFileName
import warlockfe.warlock3.compose.resources.MR

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
        HandBox(MR.images.left_hand_small, left)
        HandBox(MR.images.right_hand_small, right)
        HandBox(MR.images.spell_hand_small, spell)
    }
}

@Composable
fun RowScope.HandBox(image: ImageResource, value: String) {
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
            Image(painter = painterResource(image), contentDescription = "left hand")
            Spacer(Modifier.width(8.dp))
            Text(value)
        }
    }
}

//@Preview
//@Composable
//fun HandsViewPreview() {
//    HandsViewContent(left = "some item", right = "", spell = "a spell")
//}
