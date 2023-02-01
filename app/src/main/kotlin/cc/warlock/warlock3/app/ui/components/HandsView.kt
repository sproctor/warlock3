package cc.warlock.warlock3.app.ui.components

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.dp

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
        val leftImage = remember {
            object {}.javaClass.getResourceAsStream("/images/left_hand_small.gif").use {
                org.jetbrains.skia.Image.makeFromEncoded(it!!.readBytes()).toComposeImageBitmap()
            }
        }
        HandBox(leftImage, left)
        val rightImage = remember {
            object {}.javaClass.getResourceAsStream("/images/right_hand_small.gif").use {
                org.jetbrains.skia.Image.makeFromEncoded(it!!.readBytes()).toComposeImageBitmap()
            }
        }
        HandBox(rightImage, right)
        val spellImage = remember {
            object {}.javaClass.getResourceAsStream("/images/spell_hand_small.gif").use {
                org.jetbrains.skia.Image.makeFromEncoded(it!!.readBytes()).toComposeImageBitmap()
            }
        }
        HandBox(spellImage, spell)
    }
}

@Composable
fun RowScope.HandBox(image: ImageBitmap, value: String) {
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
            Image(bitmap = image, contentDescription = "left hand")
            Spacer(Modifier.width(8.dp))
            Text(value)
        }
    }
}

@Preview
@Composable
fun HandsViewPreview() {
    HandsViewContent(left = "some item", right = "", spell = "a spell")
}