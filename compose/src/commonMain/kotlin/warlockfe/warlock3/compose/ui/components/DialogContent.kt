package warlockfe.warlock3.compose.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import androidx.constraintlayout.compose.ConstrainedLayoutReference
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
    println(dataObjects.toString())
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
        ConstraintLayout(
            Modifier.fillMaxSize()
        ) {
            val refs = dataObjects.associate { it.id to createRef() }
            var lastRef: ConstrainedLayoutReference? = null
            dataObjects.forEach { data ->
                val colors = colors[data.id] ?: ColorGroup(
                    text = Color.White,
                    bar = Color.Blue,
                    background = Color.Gray
                )
                DataObjectContent(
                    modifier = Modifier
                        .size(
                            width = data.width?.toDp(maxWidth) ?: Dp.Unspecified,
                            height = data.height?.toDp(maxHeight) ?: Dp.Unspecified,
                        )
                        .constrainAs(refs[data.id]!!) {
                            val topAnchor = if (data.topAnchor != null) {
                                refs[data.topAnchor]?.bottom ?: parent.top
                            } else if (data.top == null) {
                                lastRef?.top ?: parent.top
                            } else {
                                parent.top
                            }
                            top.linkTo(
                                anchor = topAnchor,
                                margin = data.top?.toDp(maxWidth) ?: 0.dp
                            )
                            val leftMargin = data.left?.toDp(maxWidth) ?: 0.dp
                            when (data.align) {
                                "n" -> {
                                    absoluteLeft.linkTo(parent.absoluteLeft, leftMargin)
                                    absoluteRight.linkTo(parent.absoluteRight, -leftMargin)
                                    // We're ignoring anchor_left in this situation, is that ok?
                                }

                                "ne" -> {
                                    absoluteRight.linkTo(parent.absoluteRight, leftMargin)
                                }

                                else -> {
                                    // nw and default are treated the same
                                    val leftAnchor = if (data.leftAnchor != null) {
                                        refs[data.leftAnchor]?.absoluteRight ?: parent.absoluteLeft
                                    } else if (data.left == null) {
                                        lastRef?.absoluteRight ?: parent.absoluteLeft
                                    } else {
                                        parent.absoluteLeft
                                    }
                                    absoluteLeft.linkTo(leftAnchor, leftMargin)
                                }
                            }
                            lastRef = refs[data.id]
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
        is DialogObject.Button -> DialogButton(modifier, dataObject, executeCommand)
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
                style = MaterialTheme.typography.labelSmall
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
    Box(modifier = modifier.padding(horizontal = 4.dp)) {
        Text(
            modifier = Modifier.align(Alignment.Center),
            text = text,
            color = colorGroup.text,
            style = MaterialTheme.typography.labelSmall
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
    Box(modifier = modifier.padding(horizontal = 4.dp)) {
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
            style = MaterialTheme.typography.labelSmall,
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

@Composable
private fun DialogButton(
    modifier: Modifier,
    data: DialogObject.Button,
    executeCommand: (String) -> Unit,
) {
    val shape = MaterialTheme.shapes.extraSmall
    Box(
        modifier = modifier
            .border(
                width = Dp.Hairline,
                color = MaterialTheme.colorScheme.outline,
                shape = shape
            )
            .background(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = shape
            )
    ) {
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
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

private data class ColorGroup(val text: Color, val bar: Color, val background: Color)

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
            value = "skin",
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

@Preview
@Composable
fun ExperiencePreview() {
    val dialogData = listOf(
        DialogObject.Label(
            id = "yourLvl",
            left = DataDistance.Pixels(value = 0),
            top = DataDistance.Pixels(value = 0),
            width = DataDistance.Pixels(value = 160),
            height = DataDistance.Pixels(value = 15),
            align = "n",
            topAnchor = null,
            leftAnchor = null,
            tooltip = null,
            value = "Level 0"
        ),
        DialogObject.ProgressBar(
            id = "mindState",
            left = DataDistance.Pixels(value = 3),
            top = DataDistance.Pixels(value = 45),
            width = DataDistance.Pixels(value = 160),
            height = DataDistance.Pixels(value = 15),
            align = "n",
            topAnchor = null,
            leftAnchor = null,
            tooltip = null,
            value = Percentage(value = 0),
            text = "clear as a bell"
        ),
        DialogObject.ProgressBar(
            id = "nextLvlPB",
            left = DataDistance.Pixels(value = 3),
            top = DataDistance.Pixels(value = 20),
            width = DataDistance.Pixels(value = 160),
            height = DataDistance.Pixels(value = 15),
            align = "n",
            topAnchor = null,
            leftAnchor = null,
            tooltip = null,
            value = Percentage(value = 6),
            text = "2365 until next level"
        ),
        DialogObject.Label(
            id = "PTPs",
            left = DataDistance.Pixels(value = 20),
            top = DataDistance.Pixels(value = 0),
            width = DataDistance.Pixels(value = 80),
            height = DataDistance.Pixels(value = 20),
            align = null,
            topAnchor = "mindState",
            leftAnchor = null,
            tooltip = "Physical Training Points",
            value = "5 PTPs"
        ),
        DialogObject.Label(
            id = "MTPs",
            left = DataDistance.Pixels(value = 0),
            top = DataDistance.Pixels(value = 0),
            width = DataDistance.Pixels(value = 80),
            height = DataDistance.Pixels(value = 20),
            align = null,
            topAnchor = "mindState",
            leftAnchor = "PTPs",
            tooltip = "Mental Training Points",
            value = "3 MTPs"
        ),
        DialogObject.Label(
            id = "p2m",
            left = DataDistance.Pixels(value = 20),
            top = DataDistance.Pixels(value = 0),
            width = DataDistance.Pixels(value = 80),
            height = DataDistance.Pixels(value = 20),
            align = null,
            topAnchor = "PTPs",
            leftAnchor = null,
            tooltip = "Physical tps that have been converted to Mental tps",
            value = "0 P2M"
        ),
        DialogObject.Label(
            id = "m2p",
            left = DataDistance.Pixels(value = 0),
            top = DataDistance.Pixels(value = 0),
            width = DataDistance.Pixels(value = 80),
            height = DataDistance.Pixels(value = 20),
            align = null,
            topAnchor = "MTPs",
            leftAnchor = "p2m",
            tooltip = "Mental tps that have been converted to Physical tps",
            value = "0 M2P"
        ),
        DialogObject.Link(
            id = "exprLNK",
            left = DataDistance.Pixels(value = 20),
            top = DataDistance.Pixels(value = 0),
            width = DataDistance.Pixels(value = 80),
            height = null,
            align = null,
            topAnchor = "p2m",
            leftAnchor = null,
            tooltip = null,
            value = "Details",
            cmd = "experie",
            echo = "experience"
        ),
        DialogObject.Link(
            id = "goalsLNK",
            left = DataDistance.Pixels(value = 0),
            top = DataDistance.Pixels(value = 0),
            width = DataDistance.Pixels(value = 80),
            height = null,
            align = null,
            topAnchor = "m2p",
            leftAnchor = "exprLNK",
            tooltip = null,
            value = "Skill Goals",
            cmd = "goals",
            echo = "goals"
        )
    )
    DialogContent(
        dataObjects = dialogData,
        modifier = Modifier.size(190.dp, 200.dp),
        executeCommand = {},
    )
}