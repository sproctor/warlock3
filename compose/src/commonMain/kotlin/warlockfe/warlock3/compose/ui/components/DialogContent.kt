package warlockfe.warlock3.compose.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.constraintlayout.compose.ConstraintLayout
import org.jetbrains.compose.ui.tooling.preview.Preview
import warlockfe.warlock3.core.client.DataDistance
import warlockfe.warlock3.core.client.DialogObject
import warlockfe.warlock3.core.client.Percentage
import kotlin.math.min

@Composable
fun DialogContent(
    dataObjects: List<DialogObject>,
    modifier: Modifier = Modifier,
    executeCommand: (String) -> Unit,
) {
    val colors = mutableMapOf<String, ColorGroup>()
    dataObjects.forEach { data ->
        if (data is DialogObject.Skin) {
            data.controls.forEach { id ->
                val colorGroup = when (data.name) {
                    "healthBar" ->
                        ColorGroup(
                            text = Color.White,
                            bar = Color(0xFF800000),
                            background = Color.DarkGray
                        )

                    "manaBar" ->
                        ColorGroup(
                            text = Color.White,
                            bar = Color.Blue,
                            background = Color.DarkGray
                        )

                    "staminaBar" ->
                        ColorGroup(
                            text = Color.Black,
                            bar = Color(0xFFD0982F),
                            background = Color(0xFFDECCAA)
                        )

                    "spiritBar" ->
                        ColorGroup(
                            text = Color.Black,
                            bar = Color.LightGray,
                            background = Color.Gray
                        )

                    else ->
                        ColorGroup(
                            text = Color.White,
                            bar = Color.Blue,
                            background = Color.Gray
                        )
                }
                colors[id] = colorGroup
            }
        }
    }
    BoxWithConstraints(modifier) {
        val maxWidth = maxWidth
        val maxHeight = maxHeight
        ConstraintLayout {
            val refs = dataObjects.associate { it.id to createRef() }
            var defaultLeftMargin = 0.dp
            var defaultTopMargin = 0.dp
            dataObjects.forEach { data ->
                val width = data.width?.toDp(maxWidth) ?: data.getWidth()
                val height = data.height?.toDp(maxHeight) ?: Dp.Unspecified
                val offsetX = when (data.align) {
                    "n" -> (maxWidth - (width.takeIf { it.isSpecified } ?: 0.dp)) / 2
                    "ne" -> maxWidth - (width.takeIf { it.isSpecified } ?: 0.dp)
                    else -> 0.dp
                }
                val leftMargin = (data.left?.toDp(maxWidth)?.let { it + offsetX } ?: defaultLeftMargin)
                val topMargin = data.top?.toDp(maxWidth) ?: defaultTopMargin
                defaultTopMargin = topMargin
                defaultLeftMargin = leftMargin + width
                val colors = colors[data.id] ?: ColorGroup(
                    text = Color.White,
                    bar = Color.Blue,
                    background = Color.Gray
                )
                DataObjectContent(
                    modifier = Modifier.size(width, height)
                        .constrainAs(refs[data.id]!!) {
                            val topAnchor = data.topAnchor?.let { refs[it]?.bottom } ?: parent.top
                            top.linkTo(topAnchor, topMargin)
                            val leftAnchor = data.leftAnchor?.let { refs[it]?.absoluteRight } ?: parent.absoluteLeft
                            absoluteLeft.linkTo(leftAnchor, leftMargin)
                        },
                    colorGroup = colors,
                    dataObject = data,
                    executeCommand = executeCommand,
                )
            }
        }
    }
}

@Composable
private fun DataObjectContent(
    modifier: Modifier,
    colorGroup: ColorGroup,
    dataObject: DialogObject,
    executeCommand: (String) -> Unit,
) {
    when (dataObject) {
        is DialogObject.ProgressBar -> ProgressBar(modifier, colorGroup, dataObject)
        is DialogObject.Label -> Label(modifier, colorGroup, dataObject.value ?: "")
        is DialogObject.Link -> Link(modifier, colorGroup, dataObject, executeCommand)
        is DialogObject.Image -> DialogImage(modifier, colorGroup, dataObject, executeCommand)
        else -> {
            // todo
        }
    }
}

private fun DataDistance.toDp(maxWidth: Dp): Dp {
    return when (this) {
        is DataDistance.Percent -> maxWidth * value.value / 100
        is DataDistance.Pixels -> value.dp
    }
}

@Composable
private fun ProgressBar(
    modifier: Modifier,
    colorGroup: ColorGroup,
    progressBarData: DialogObject.ProgressBar,
) {
    BoxWithConstraints(
        modifier = modifier.background(colorGroup.background)
    ) {
        val percent = min(progressBarData.value.value, 100)
        val width = maxWidth * percent / 100
        Box(modifier = Modifier.width(width).fillMaxHeight().background(colorGroup.bar))
        progressBarData.text?.let { text ->
            Text(
                modifier = Modifier.align(Alignment.Center),
                text = text,
                color = colorGroup.text,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun Label(
    modifier: Modifier,
    colorGroup: ColorGroup,
    text: String,
) {
    Box(modifier = modifier) {
        Text(
            modifier = Modifier.align(Alignment.Center),
            text = text,
            color = colorGroup.text,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun Link(
    modifier: Modifier,
    colorGroup: ColorGroup,
    data: DialogObject.Link,
    executeCommand: (String) -> Unit,
) {
    Box(modifier = modifier) {
        Text(
            modifier = Modifier.align(Alignment.Center),
            text = buildAnnotatedString {
                pushLink(
                    LinkAnnotation.Clickable("action") {
                        executeCommand(data.cmd ?: "")
                    }
                )
                append(data.value)
                pop()
            },
            color = colorGroup.text,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun DialogImage(
    modifier: Modifier,
    colorGroup: ColorGroup,
    data: DialogObject.Image,
    executeCommand: (String) -> Unit,
) {
    Box(
        modifier = modifier.then(
            if (data.cmd != null) {
                Modifier.clickable {
                    executeCommand(data.cmd!!)
                }
            } else {
                Modifier
            }
        )
    ) {
        Icon(
            imageVector = Icons.Default.Search,
            contentDescription = null,
            tint = colorGroup.bar,
        )
    }
}

private data class ColorGroup(val text: Color, val bar: Color, val background: Color)

private fun DialogObject.getWidth(): Dp {
    return when (this) {
        is DialogObject.ProgressBar -> {
            getTextWidth(text)
        }
        is DialogObject.Label -> {
            getTextWidth(value)
        }
        is DialogObject.Image -> 0.dp
        is DialogObject.Link ->
            getTextWidth(value)
        is DialogObject.Skin -> 0.dp
        is DialogObject.Button -> getTextWidth(value)
    }
}

private fun getTextWidth(text: String?): Dp {
    return text?.let { 11.dp * it.length } ?: 0.dp
}

@Preview
@Composable
fun VitalBarsPreview() {
    val dialogData = listOf(
        DialogObject.Skin(
            id = "healthSkin",
            left = null,
            top = null,
            width = null,
            height = null,
            align = null,
            topAnchor = null,
            leftAnchor = null,
            tooltip = null,
            name = "healthBar",
            controls = listOf("health")
        ),
        DialogObject.ProgressBar(
            id = "health",
            left = DataDistance.Percent(value = Percentage(value = 0)),
            top = DataDistance.Percent(value = Percentage(value = 0)),
            width = DataDistance.Percent(value = Percentage(value = 25)),
            height = DataDistance.Percent(value = Percentage(value = 100)),
            align = null,
            topAnchor = null,
            leftAnchor = null,
            tooltip = null,
            value = Percentage(value = 100),
            text = "health 26/26"
        ),
        DialogObject.Skin(
            id = "manaSkin",
            left = DataDistance.Percent(value = Percentage(value = 25)),
            top = DataDistance.Percent(value = Percentage(value = 0)),
            width = DataDistance.Percent(value = Percentage(value = 25)),
            height = DataDistance.Percent(value = Percentage(value = 100)),
            align = null,
            topAnchor = null,
            leftAnchor = null,
            tooltip = null,
            name = "manaBar",
            controls = listOf("mana")
        ),
        DialogObject.ProgressBar(
            id = "mana",
            left = DataDistance.Percent(value = Percentage(value = 25)),
            top = DataDistance.Percent(value = Percentage(value = 0)),
            width = DataDistance.Percent(value = Percentage(value = 25)),
            height = DataDistance.Percent(value = Percentage(value = 100)),
            align = null,
            topAnchor = null,
            leftAnchor = null,
            tooltip = null,
            value = Percentage(value = 100),
            text = "mana 2/2"
        ),
        DialogObject.Skin(
            id = "spiritSkin",
            left = DataDistance.Percent(value = Percentage(value = 75)),
            top = DataDistance.Percent(value = Percentage(value = 0)),
            width = DataDistance.Percent(value = Percentage(value = 25)),
            height = DataDistance.Percent(value = Percentage(value = 100)),
            align = null,
            topAnchor = null,
            leftAnchor = null,
            tooltip = null,
            name = "spiritBar",
            controls = listOf("spirit")
        ),
        DialogObject.ProgressBar(
            id = "spirit",
            left = DataDistance.Percent(value = Percentage(value = 75)),
            top = DataDistance.Percent(value = Percentage(value = 0)),
            width = DataDistance.Percent(value = Percentage(value = 25)),
            height = DataDistance.Percent(value = Percentage(value = 100)),
            align = null,
            topAnchor = null,
            leftAnchor = null,
            tooltip = null,
            value = Percentage(value = 100),
            text = "spirit 7/7"
        ),
        DialogObject.Skin(
            id = "staminaSkin",
            left = DataDistance.Percent(value = Percentage(value = 50)),
            top = DataDistance.Percent(value = Percentage(value = 0)),
            width = DataDistance.Percent(value = Percentage(value = 25)),
            height = DataDistance.Percent(value = Percentage(value = 100)),
            align = null,
            topAnchor = null,
            leftAnchor = null,
            tooltip = null,
            name = "staminaBar",
            controls = listOf("stamina")
        ),
        DialogObject.ProgressBar(
            id = "stamina",
            left = DataDistance.Percent(value = Percentage(value = 50)),
            top = DataDistance.Percent(value = Percentage(value = 0)),
            width = DataDistance.Percent(value = Percentage(value = 25)),
            height = DataDistance.Percent(value = Percentage(value = 100)),
            align = null,
            topAnchor = null,
            leftAnchor = null,
            tooltip = null,
            value = Percentage(value = 100),
            text = "stamina 26/26"
        )
    )
    DialogContent(
        dataObjects = dialogData,
        modifier = Modifier.size(400.dp, 24.dp),
        executeCommand = {},
    )
}


@Preview
@Composable
fun CombatDialogPreview() {
    val dialogData = listOf(
        DialogObject.Image(
            id = "unsheathe",
            left = DataDistance.Pixels(value = -50),
            top = DataDistance.Pixels(value = 3),
            width = DataDistance.Pixels(value = 29),
            height = DataDistance.Pixels(value = 29),
            align = "n",
            topAnchor = null,
            leftAnchor = null,
            tooltip = "Unsheathe Weapon",
            name = "SwordBtn",
            cmd = "_ready weapon",
            echo = "ready weapon"
        ),
        DialogObject.Image(
            id = "readyshield",
            left = DataDistance.Pixels(value = 3),
            top = null,
            width = DataDistance.Pixels(value = 29),
            height = DataDistance.Pixels(value = 29),
            align = null,
            topAnchor = null,
            leftAnchor = "unsheathe",
            tooltip = "Ready Shield",
            name = "ShieldBtn",
            cmd = "_ready shield",
            echo = "ready shield"
        ),
        DialogObject.Image(
            id = "sheathe",
            left = DataDistance.Pixels(value = 8),
            top = null,
            width = DataDistance.Pixels(value = 29),
            height = DataDistance.Pixels(value = 29),
            align = null,
            topAnchor = null,
            leftAnchor = "readyshield",
            tooltip = "Sheathe Weapon",
            name = "NoSwordBtn",
            cmd = "_store weapon",
            echo = "store weapon"
        ),
        DialogObject.Image(
            id = "storeshield",
            left = DataDistance.Pixels(value = 3),
            top = null,
            width = DataDistance.Pixels(value = 29),
            height = DataDistance.Pixels(value = 29),
            align = null,
            topAnchor = null,
            leftAnchor = "sheathe",
            tooltip = "Store Shield",
            name = "NoShieldBtn",
            cmd = "_store shield",
            echo = "store shield"
        ),
        DialogObject.Link(
            id = "lnConfigure",
            left = DataDistance.Pixels(value = 0),
            top = DataDistance.Pixels(value = 30),
            width = null,
            height = null,
            align = "n",
            topAnchor = null,
            leftAnchor = null,
            tooltip = null,
            value = "configure",
            cmd = "_cmbtpl configure dialog",
            echo = "configure"
        ),
        DialogObject.ProgressBar(
            id = "pbarStance",
            left = DataDistance.Pixels(value = 0),
            top = DataDistance.Pixels(value = 51),
            width = DataDistance.Pixels(value = 130),
            height = DataDistance.Pixels(value = 16),
            align = "n",
            topAnchor = null,
            leftAnchor = null,
            tooltip = "Percent of stance contributing to defense",
            value = Percentage(value = 100),
            text = "defensive(100 %)"
        ),
        DialogObject.Button(
            id = "cmdDefStance",
            left = DataDistance.Pixels(value = 0),
            top = DataDistance.Pixels(value = 70),
            width = DataDistance.Pixels(value = 55),
            height = DataDistance.Pixels(value = 20),
            align = "nw",
            topAnchor = null,
            leftAnchor = null,
            tooltip = "Assume a Defensive Stance",
            value = "defense",
            cmd = "_stance defensive",
            echo = "stance defensive"
        ),
        DialogObject.Button(
            id = "cmdOffStance",
            left = DataDistance.Pixels(value = 0),
            top = DataDistance.Pixels(value = 70),
            width = DataDistance.Pixels(value = 50),
            height = DataDistance.Pixels(value = 20),
            align = "ne",
            topAnchor = null,
            leftAnchor = null,
            tooltip = "Assume an Offensive Stance",
            value = "offense",
            cmd = "_stance offensive",
            echo = "stance offensive"
        ),
        DialogObject.Button(
            id = "cmdTarget",
            left = DataDistance.Pixels(value = 0),
            top = DataDistance.Pixels(value = 93),
            width = DataDistance.Pixels(value = 55),
            height = DataDistance.Pixels(value = 20),
            align = "nw",
            topAnchor = null,
            leftAnchor = null,
            tooltip = "Select a Random Target",
            value = "target",
            cmd = "target random",
            echo = null
        ),
        DialogObject.Button(
            id = "cmdAttack",
            left = DataDistance.Pixels(value = 0),
            top = DataDistance.Pixels(value = 93),
            width = DataDistance.Pixels(value = 50),
            height = DataDistance.Pixels(value = 20),
            align = "ne",
            topAnchor = null,
            leftAnchor = null,
            tooltip = "Attack Current Target",
            value = "attack",
            cmd = "attack",
            echo = null
        ),
        DialogObject.Button(
            id = "cmdHide",
            left = DataDistance.Pixels(value = 0),
            top = DataDistance.Pixels(value = 116),
            width = DataDistance.Pixels(value = 55),
            height = DataDistance.Pixels(value = 20),
            align = "nw",
            topAnchor = null,
            leftAnchor = null,
            tooltip = "Hide",
            value = "hide",
            cmd = "hide",
            echo = null
        ),
        DialogObject.Button(
            id = "cmdAmbush",
            left = DataDistance.Pixels(value = 0),
            top = DataDistance.Pixels(value = 116),
            width = DataDistance.Pixels(value = 50),
            height = DataDistance.Pixels(value = 20),
            align = "ne",
            topAnchor = null,
            leftAnchor = null,
            tooltip = "Ambush Current Target",
            value = "ambush",
            cmd = "ambush",
            echo = null
        ),
        DialogObject.Button(
            id = "cmdJab",
            left = DataDistance.Pixels(value = 0),
            top = DataDistance.Pixels(value = 139),
            width = DataDistance.Pixels(value = 38),
            align = null,
            height = null,
            topAnchor = null,
            leftAnchor = null,
            tooltip = "Jab Current Target",
            value = "jab",
            cmd = "jab",
            echo = null
        ),
        DialogObject.Button(
            id = "cmdPunch",
            left = DataDistance.Pixels(value = 38),
            top = DataDistance.Pixels(value = 139),
            width = DataDistance.Pixels(value = 53),
            align = null,
            height = null,
            topAnchor = null,
            leftAnchor = null,
            tooltip = "Punch Current Target",
            value = "punch",
            cmd = "punch",
            echo = null
        ),
        DialogObject.Button(
            id = "cmdGrapple",
            left = DataDistance.Pixels(value = 91),
            top = DataDistance.Pixels(value = 139),
            width = DataDistance.Pixels(value = 59),
            align = null,
            height = null,
            topAnchor = null,
            leftAnchor = null,
            tooltip = "Grapple Current Target",
            value = "grapple",
            cmd = "grapple",
            echo = null
        ),
        DialogObject.Button(
            id = "cmdKick",
            left = DataDistance.Pixels(value = 150),
            top = DataDistance.Pixels(value = 139),
            width = DataDistance.Pixels(value = 40),
            height = null,
            align = null,
            topAnchor = null,
            leftAnchor = null,
            tooltip = "Kick Current Target",
            value = "kick",
            cmd = "kick",
            echo = null
        ),
        DialogObject.Button(
            id = "cmdQuickstrike",
            left = DataDistance.Pixels(value = 53),
            top = DataDistance.Pixels(value = 165),
            width = DataDistance.Pixels(value = 137),
            height = DataDistance.Pixels(value = 20),
            align = null,
            topAnchor = null,
            leftAnchor = null,
            tooltip = "Quickstrike Next Action",
            value = "prepare to quickstrike",
            cmd = "quickstrike % uDEQuickstrike %",
            echo = null
        ),
        DialogObject.Link(
            id = "lnSkin",
            left = DataDistance.Pixels(value = 0),
            top = DataDistance.Pixels(value = 191),
            width = null,
            height = null,
            align = null,
            topAnchor = null,
            leftAnchor = null,
            tooltip = null,
            value = "MMMM",
            cmd = "_skin",
            echo = "skin"
        ),
        DialogObject.Link(
            id = "lnSearch",
            left = null,
            top = null,
            width = null,
            height = null,
            align = null,
            topAnchor = null,
            leftAnchor = null,
            tooltip = null,
            value = "search",
            cmd = "_search",
            echo = "search"
        ),
        DialogObject.Link(
            id = "lnGrip",
            left = null,
            top = null,
            width = null,
            height = null,
            align = null,
            topAnchor = null,
            leftAnchor = null,
            tooltip = null,
            value = "grip",
            cmd = "_grip",
            echo = "grip"
        )
    )
    DialogContent(
        dataObjects = dialogData,
        modifier = Modifier.size(190.dp, 219.dp),
        executeCommand = {},
    )
}
