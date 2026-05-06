import org.antlr.v4.kotlinruntime.CharStreams
import org.antlr.v4.kotlinruntime.CommonTokenStream
import kotlin.test.Test
import kotlin.test.assertEquals
import warlockfe.warlock3.core.client.BackgroundImageHorizontalAlignment
import warlockfe.warlock3.core.client.BackgroundImageMode
import warlockfe.warlock3.core.client.BackgroundImageVerticalAlignment
import warlockfe.warlock3.wrayth.parsers.generated.WraythLexer
import warlockfe.warlock3.wrayth.parsers.generated.WraythParser
import warlockfe.warlock3.wrayth.protocol.WraythBackgroundEvent
import warlockfe.warlock3.wrayth.protocol.WraythNavEvent
import warlockfe.warlock3.wrayth.protocol.WraythNodeVisitor
import warlockfe.warlock3.wrayth.protocol.WraythProtocolHandler

class WraythParserTests {
    @Test
    fun typicalParser() {
        val inputStream = CharStreams.fromString("<tag1 id=\"1223\"> hello &gt; sproctor </tag1>")
        val lexer = WraythLexer(inputStream)
        val tokens = CommonTokenStream(lexer)
        val parser = WraythParser(tokens)
        WraythNodeVisitor.visitDocument(parser.document())
        assertEquals(0, parser.numberOfSyntaxErrors)
    }

    @Test
    fun backgroundTagIncludesWindowAndImage() {
        val events = WraythProtocolHandler().parseLine("<background window=\"main\" img=\"room.png\"/>")

        assertEquals(
            WraythBackgroundEvent(
                windowName = "main",
                image = "room.png",
                mode = BackgroundImageMode.HEIGHT_FILL,
            ),
            events.first(),
        )
    }

    @Test
    fun backgroundTagIncludesMode() {
        val modes = mapOf(
            "fill" to BackgroundImageMode.FILL,
            "hfill" to BackgroundImageMode.HEIGHT_FILL,
            "wfill" to BackgroundImageMode.WIDTH_FILL,
            "full" to BackgroundImageMode.FULL,
            "gradient" to BackgroundImageMode.GRADIENT,
        )

        modes.forEach { (mode, expectedMode) ->
            val events = WraythProtocolHandler().parseLine("<background window=\"main\" img=\"room.png\" mode=\"$mode\"/>")

            assertEquals(
                WraythBackgroundEvent(windowName = "main", image = "room.png", mode = expectedMode),
                events.first(),
            )
        }
    }

    @Test
    fun backgroundGradientTagIncludesStartAndEnd() {
        val events = WraythProtocolHandler().parseLine(
            "<background window=\"main\" img=\"room.png\" mode=\"gradient\" start=\"25\" end=\"75\"/>"
        )

        assertEquals(
            WraythBackgroundEvent(
                windowName = "main",
                image = "room.png",
                mode = BackgroundImageMode.GRADIENT,
                gradientStart = 25,
                gradientEnd = 75,
            ),
            events.first(),
        )
    }

    @Test
    fun backgroundGradientStartAndEndAreClamped() {
        val events = WraythProtocolHandler().parseLine(
            "<background img=\"room.png\" mode=\"gradient\" start=\"-10\" end=\"120\"/>"
        )

        assertEquals(
            WraythBackgroundEvent(
                windowName = null,
                image = "room.png",
                mode = BackgroundImageMode.GRADIENT,
                gradientStart = 0,
                gradientEnd = 100,
            ),
            events.first(),
        )
    }

    @Test
    fun backgroundTagIncludesOpacity() {
        val events = WraythProtocolHandler().parseLine("<background img=\"room.png\" opacity=\"45\"/>")

        assertEquals(
            WraythBackgroundEvent(
                windowName = null,
                image = "room.png",
                opacity = 45,
            ),
            events.first(),
        )
    }

    @Test
    fun backgroundGradientOpacityDoesNotClampStartAndEnd() {
        val events = WraythProtocolHandler().parseLine(
            "<background img=\"room.png\" mode=\"gradient\" opacity=\"60\" start=\"90\" end=\"30\"/>"
        )

        assertEquals(
            WraythBackgroundEvent(
                windowName = null,
                image = "room.png",
                mode = BackgroundImageMode.GRADIENT,
                gradientStart = 90,
                gradientEnd = 30,
                opacity = 60,
            ),
            events.first(),
        )
    }

    @Test
    fun backgroundOpacityIsClamped() {
        val events = WraythProtocolHandler().parseLine("<background img=\"room.png\" opacity=\"120\"/>")

        assertEquals(
            WraythBackgroundEvent(
                windowName = null,
                image = "room.png",
                opacity = 100,
            ),
            events.first(),
        )
    }

    @Test
    fun backgroundTagIncludesAlignment() {
        val horizontalAlignments = mapOf(
            "left" to BackgroundImageHorizontalAlignment.LEFT,
            "center" to BackgroundImageHorizontalAlignment.CENTER,
            "right" to BackgroundImageHorizontalAlignment.RIGHT,
        )
        val verticalAlignments = mapOf(
            "top" to BackgroundImageVerticalAlignment.TOP,
            "middle" to BackgroundImageVerticalAlignment.MIDDLE,
            "bottom" to BackgroundImageVerticalAlignment.BOTTOM,
        )

        horizontalAlignments.forEach { (align, expectedHorizontalAlignment) ->
            verticalAlignments.forEach { (valign, expectedVerticalAlignment) ->
                val events = WraythProtocolHandler().parseLine(
                    "<background img=\"room.png\" align=\"$align\" valign=\"$valign\"/>"
                )

                assertEquals(
                    WraythBackgroundEvent(
                        windowName = null,
                        image = "room.png",
                        horizontalAlignment = expectedHorizontalAlignment,
                        verticalAlignment = expectedVerticalAlignment,
                    ),
                    events.first(),
                )
            }
        }
    }

    @Test
    fun unknownBackgroundAlignmentUsesDefault() {
        val events = WraythProtocolHandler().parseLine(
            "<background img=\"room.png\" align=\"unknown\" valign=\"unknown\"/>"
        )

        assertEquals(
            WraythBackgroundEvent(
                windowName = null,
                image = "room.png",
                horizontalAlignment = BackgroundImageHorizontalAlignment.CENTER,
                verticalAlignment = BackgroundImageVerticalAlignment.MIDDLE,
            ),
            events.first(),
        )
    }

    @Test
    fun unknownBackgroundModeUsesDefault() {
        val events = WraythProtocolHandler().parseLine("<background img=\"room.png\" mode=\"unknown\"/>")

        assertEquals(
            WraythBackgroundEvent(
                windowName = null,
                image = "room.png",
                mode = BackgroundImageMode.HEIGHT_FILL,
            ),
            events.first(),
        )
    }

    @Test
    fun emptyBackgroundTagClearsBackground() {
        val events = WraythProtocolHandler().parseLine("<background/>")

        assertEquals(
            WraythBackgroundEvent(
                windowName = null,
                image = null,
                mode = BackgroundImageMode.HEIGHT_FILL,
            ),
            events.first(),
        )
    }

    @Test
    fun backgroundClearAllAttributeClearsAllBackgrounds() {
        val events = WraythProtocolHandler().parseLine("<background clearall/>")

        assertEquals(
            WraythBackgroundEvent(
                windowName = null,
                image = null,
                mode = BackgroundImageMode.HEIGHT_FILL,
                clearAll = true,
            ),
            events.first(),
        )
    }
}
