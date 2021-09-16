package cc.warlock.warlock3.app.view

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import cc.warlock.warlock3.app.viewmodel.GameViewModel

@Composable
fun HandsView(viewModel: GameViewModel) {
    val properties by viewModel.properties.collectAsState()
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
                org.jetbrains.skia.Image.makeFromEncoded(it!!.readBytes()).asImageBitmap()
            }
        }
        HandBox(leftImage, left)
        val rightImage = remember {
            object {}.javaClass.getResourceAsStream("/images/right_hand_small.gif").use {
                org.jetbrains.skia.Image.makeFromEncoded(it!!.readBytes()).asImageBitmap()
            }
        }
        HandBox(rightImage, right)
        val spellImage = remember {
            object {}.javaClass.getResourceAsStream("/images/spell_hand_small.gif").use {
                org.jetbrains.skia.Image.makeFromEncoded(it!!.readBytes()).asImageBitmap()
            }
        }
        HandBox(spellImage, spell)
    }
}

@Composable
fun RowScope.HandBox(image: ImageBitmap, value: String) {
    val shape = MaterialTheme.shapes.medium
    Row(
        modifier = Modifier
            .weight(1f)
            .padding(4.dp)
            .border(width = 1.dp, shape = shape, color = Color.Black)
            .background(color = Color.LightGray, shape = shape)
            .padding(4.dp)
    ) {
        Image(bitmap = image, contentDescription = "left hand")
        Text(value)
    }
}

@Preview
@Composable
fun HandsViewPreview() {
    HandsViewContent(left = "some item", right = "", spell = "a spell")
}