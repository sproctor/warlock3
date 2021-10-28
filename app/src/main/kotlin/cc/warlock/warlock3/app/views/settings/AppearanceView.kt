package cc.warlock.warlock3.app.views.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.LocalTextStyle
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import cc.warlock.warlock3.app.util.toAnnotatedString
import cc.warlock.warlock3.app.util.toColor
import cc.warlock.warlock3.core.text.StyleRegistry
import cc.warlock.warlock3.core.text.StyledString
import cc.warlock.warlock3.core.text.WarlockColor
import cc.warlock.warlock3.core.text.WarlockStyle
import cc.warlock.warlock3.stormfront.StreamLine

@Composable
fun AppearanceView(
    defaultTextColor: WarlockColor,
    defaultBackgroundColor: WarlockColor,
    defaultFont: String,
    styleRegistry: StyleRegistry,
) {
    val styles by styleRegistry.styles.collectAsState()
    val previewLines = listOf(
        StreamLine(
            text = StyledString("[Riverhaven, Crescent Way]", style = styles["roomName"]),
            style = styles["roomName"],
            ignoreWhenBlank = false,
        ),
        StreamLine(
            text = StyledString(
                "This is the room description for some room in Riverhaven. It didn't exist in our old preview, so we're putting arbitrary text here.",
                style = styles["roomdescription"]
            ),
            style = null,
            ignoreWhenBlank = false,
        ),
        StreamLine(
            text = StyledString("You also see a ") + StyledString(
                "Sir Robyn",
                style = styles["bold"]
            ) + StyledString("."),
            style = null,
            ignoreWhenBlank = false,
        ),
        StreamLine(
            text = StyledString("say Hello", style = styles["command"]),
            style = null,
            ignoreWhenBlank = false,
        ),
        StreamLine(
            text = StyledString("You say", style = styles["speech"]) + StyledString(", \"Hello.\""),
            style = null,
            ignoreWhenBlank = false,
        ),
        StreamLine(
            text = StyledString("Someone whispers", style = styles["whisper"]) + StyledString(", \"Hi\""),
            style = null,
            ignoreWhenBlank = false,
        ),
        StreamLine(
            text = StyledString("Your mind hears Someone thinking, \"hello everyone\"", style = styles["thought"]),
            style = null,
            ignoreWhenBlank = false,
        ),
        StreamLine(
            text = StyledString(
                " __      __              .__                 __    \n" +
                        "/  \\    /  \\_____ _______|  |   ____   ____ |  | __\n" +
                        "\\   \\/\\/   /\\__  \\\\_  __ \\  |  /  _ \\_/ ___\\|  |/ /\n" +
                        " \\        /  / __ \\|  | \\/  |_(  <_> )  \\___|    < \n" +
                        "  \\__/\\  /  (____  /__|  |____/\\____/ \\___  >__|_ \\\n" +
                        "       \\/        \\/                       \\/     \\/",
                style = WarlockStyle(monospace = true)
            ),
            ignoreWhenBlank = false,
            style = null,
        )
    )

    Column {
        CompositionLocalProvider(LocalTextStyle provides TextStyle(color = defaultTextColor.toColor())) {
            Column(
                modifier = Modifier
                    .background(defaultBackgroundColor.toColor())
                    .padding(vertical = 4.dp)
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                previewLines.forEach { line ->
                    Box(
                        modifier = Modifier.fillMaxWidth()
                            .background(line.style?.backgroundColor?.toColor() ?: defaultBackgroundColor.toColor())
                            .padding(horizontal = 4.dp)
                    ) {
                        Text(text = line.text.toAnnotatedString(emptyMap()))
                    }
                }
            }
        }
    }
}