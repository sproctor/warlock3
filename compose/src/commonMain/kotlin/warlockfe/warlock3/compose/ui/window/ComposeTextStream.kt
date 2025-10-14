package warlockfe.warlock3.compose.ui.window

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.buildAnnotatedString
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.nibor.autolink.LinkExtractor
import org.nibor.autolink.LinkType
import warlockfe.warlock3.compose.model.ViewHighlight
import warlockfe.warlock3.compose.util.getEntireLineStyles
import warlockfe.warlock3.compose.util.highlight
import warlockfe.warlock3.compose.util.toAnnotatedString
import warlockfe.warlock3.compose.util.toSpanStyle
import warlockfe.warlock3.compose.util.toStyleDefinition
import warlockfe.warlock3.compose.util.toTimeString
import warlockfe.warlock3.core.client.WarlockAction
import warlockfe.warlock3.core.text.StyleDefinition
import warlockfe.warlock3.core.text.StyledString
import warlockfe.warlock3.core.text.WarlockStyle
import warlockfe.warlock3.core.text.flattenStyles
import warlockfe.warlock3.core.util.SoundPlayer
import warlockfe.warlock3.core.window.TextStream
import warlockfe.warlock3.core.window.Window
import warlockfe.warlock3.core.window.getComponents
import warlockfe.warlock3.wrayth.util.CompiledAlteration
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTime::class)
class ComposeTextStream(
    override val id: String,
    private var maxLines: Int,
    private var markLinks: Boolean,
    private val highlights: StateFlow<List<ViewHighlight>>,
    private val alterations: StateFlow<List<CompiledAlteration>>,
    private val presets: StateFlow<Map<String, StyleDefinition>>,
    private val windows: StateFlow<Map<String, Window>>,
    private val mainDispatcher: CoroutineDispatcher,
    private val ioDispatcher: CoroutineDispatcher,
    private val soundPlayer: SoundPlayer,
) : TextStream {

    private val cacheLines = mutableListOf<CachedLine?>()
    val lines = mutableStateListOf<StreamLine>()
    private val components = mutableStateMapOf<String, StyledString>()

    private var nextSerialNumber = 0L
    private var removedLines = 0L

    private var partialLine: StyledString? = null

    private val componentLocations = mutableMapOf<String, List<Long>>()

    var actionHandler: ((WarlockAction) -> Unit)? = null

    override suspend fun appendPartial(text: StyledString) {
        withContext(mainDispatcher) {
            doAppendPartial(text)
        }
    }

    override suspend fun appendPartialAndEol(text: StyledString) {
        withContext(mainDispatcher) {
            doAppendPartial(text)
            doAppendLine(partialLine!!, ignoreWhenBlank = false)
            partialLine = null
        }
    }

    private fun doAppendPartial(text: StyledString) {
        if (partialLine == null) {
            partialLine = text
        } else {
            partialLine = partialLine!! + text
        }
    }

    override suspend fun clear() {
        withContext(mainDispatcher) {
            lines.clear()
            partialLine = null
            componentLocations.clear()
            components.clear()
            nextSerialNumber = 0L
            removedLines = 0L
            cacheLines.clear()
        }
    }

    override suspend fun appendLine(text: StyledString, ignoreWhenBlank: Boolean) {
        // It's possible a component is added that is set prior
        // I don't think this happens in DR, so it's probably OK that we don't handle that case.
        withContext(mainDispatcher) {
            if (partialLine != null) {
                doAppendLine(partialLine!!, ignoreWhenBlank = false)
                partialLine = null
            }
            doAppendLine(text, ignoreWhenBlank)
        }
    }

    // Must be called from main thread
    private suspend fun doAppendLine(text: StyledString, ignoreWhenBlank: Boolean) {
        removeLines()
        text.getComponents().forEach { name ->
            val existingLocations = componentLocations[name] ?: emptyList()
            componentLocations[name] = existingLocations + nextSerialNumber
        }
        val cachedLine = CachedLine(
            text = text,
            timestamp = if (windows.value[id]?.showTimestamps ?: false) Clock.System.now() else null,
            ignoreWhenBlank = ignoreWhenBlank,
        )
        cacheLines.add(cachedLine)
        val line = cachedLine.toStreamLine(
            serialNumber = nextSerialNumber++,
            highlights = highlights.value,
            alterations = alterations.value,
            presets = presets.value,
            components = components,
            actionHandler = { action ->
                actionHandler?.invoke(action)
            },
            markLinks = markLinks,
        )
        lines.add(line)
        line.text?.let { playSound(it.text) }
    }

    private fun removeLines() {
        if (maxLines > 0 && lines.size >= maxLines) {
            lines.removeAt(0)
            cacheLines.removeAt(0)
            removedLines++
            // TODO: remove componentLocations if their line was removed
        }
    }

    override suspend fun appendResource(url: String) {
        withContext(mainDispatcher) {
            if (maxLines > 0 && lines.size >= maxLines) {
                lines.removeAt(0)
                cacheLines.removeAt(0)
                removedLines++
            }
            cacheLines.add(null)
            lines.add(
                StreamImageLine(
                    url = url,
                    serialNumber = nextSerialNumber++,
                )
            )
        }
    }

    override suspend fun updateComponent(name: String, value: StyledString) {
        withContext(mainDispatcher) {
            components[name] = value
            componentLocations[name]?.forEach { serialNumber ->
                val lineNumber = serialNumber - removedLines
                // If the component has scrolled back past the buffer, ignore it
                if (lineNumber >= 0) {
                    updateLine(lineNumber)
                }
            }
        }
    }

    private fun updateLine(lineNumber: Long) {
        val cachedLine = cacheLines[lineNumber.toInt()]
        if (cachedLine != null) {
            val line = lines[lineNumber.toInt()]
            lines[lineNumber.toInt()] = cachedLine.toStreamLine(
                serialNumber = line.serialNumber,
                highlights = highlights.value,
                alterations = alterations.value,
                presets = presets.value,
                components = components,
                actionHandler = { action ->
                    actionHandler?.invoke(action)
                },
                markLinks = markLinks,
            )
        }
    }

    private suspend fun playSound(line: String) {
        highlights.value.forEach { highlight ->
            if (highlight.sound != null && highlight.regex.containsMatchIn(line)) {
                withContext(ioDispatcher) {
                    launch {
                        soundPlayer.playSound(highlight.sound)
                    }
                }
            }
        }
    }

    suspend fun setMaxLines(maxLines: Int) {
        withContext(mainDispatcher) {
            this@ComposeTextStream.maxLines = maxLines
            if (lines.size > maxLines && maxLines > 0) {
                val oldSize = lines.size
                lines.removeRange(maxLines - 1, lines.lastIndex)
                removedLines += oldSize - lines.size
            }
        }
    }

    fun setMarkLinks(markLinks: Boolean) {
        this.markLinks = markLinks
    }
}

@OptIn(ExperimentalTime::class)
data class CachedLine(
    val text: StyledString,
    val timestamp: Instant?,
    val ignoreWhenBlank: Boolean,
) {
    fun toStreamLine(
        serialNumber: Long,
        highlights: List<ViewHighlight>,
        alterations: List<CompiledAlteration>,
        presets: Map<String, StyleDefinition>,
        components: Map<String, StyledString>,
        actionHandler: (WarlockAction) -> Unit,
        markLinks: Boolean,
    ): StreamTextLine {
        return text.toStreamLine(
            ignoreWhenBlank = ignoreWhenBlank,
            serialNumber = serialNumber,
            timestamp = timestamp,
            highlights = highlights,
            alterations = alterations,
            presets = presets,
            components = components,
            actionHandler = actionHandler,
            markLinks = markLinks,
        )
    }
}

@OptIn(ExperimentalTime::class)
fun StyledString.toStreamLine(
    ignoreWhenBlank: Boolean,
    serialNumber: Long,
    timestamp: Instant?,
    highlights: List<ViewHighlight>,
    alterations: List<CompiledAlteration>,
    presets: Map<String, StyleDefinition>,
    components: Map<String, StyledString>,
    actionHandler: (WarlockAction) -> Unit,
    markLinks: Boolean,
): StreamTextLine {
    val text = if (timestamp != null) {
        this + StyledString(" [${timestamp.toTimeString()}]")
    } else {
        this
    }
    val textWithComponents = text.toAnnotatedString(
        variables = components,
        styleMap = presets,
        actionHandler = actionHandler,
    )
        .alter(alterations)
        ?.takeIf { !ignoreWhenBlank || it.isNotBlank() }
    val highlightedResult = textWithComponents?.highlight(highlights)
    val lineStyle = flattenStyles(
        (highlightedResult?.entireLineStyles ?: emptyList()) +
                getEntireLineStyles(
                    variables = components,
                    styleMap = presets,
                )
    )
    return StreamTextLine(
        text = highlightedResult?.let {
            buildAnnotatedString {
                lineStyle?.let { pushStyle(it.toSpanStyle()) }
                append(highlightedResult.text)
                if (markLinks) {
                    linkExtractor.extractLinks(highlightedResult.text.text).forEach { link ->
                        if (highlightedResult.text.getLinkAnnotations(link.beginIndex, link.endIndex).isEmpty()) {
                            addStyle(
                                style = WarlockStyle("link").toStyleDefinition(presets).toSpanStyle(),
                                start = link.beginIndex,
                                end = link.endIndex,
                            )
                            val substring = highlightedResult.text.substring(link.beginIndex, link.endIndex)
                            addLink(
                                url = LinkAnnotation.Url(
                                    if (link.type == LinkType.URL) {
                                        substring
                                    } else {
                                        "http://$substring"
                                    }
                                ),
                                start = link.beginIndex,
                                end = link.endIndex,
                            )
                        }
                    }
                }
                if (lineStyle != null) pop()
            }
        },
        entireLineStyle = lineStyle,
        serialNumber = serialNumber,
    )
}

private fun AnnotatedString.alter(alterations: List<CompiledAlteration>): AnnotatedString? {
    var result = this
    alterations.forEach { alteration ->
        val match = alteration.match(result.text)
        if (match != null) {
            result = buildAnnotatedString {
                append(result.take(match.matchResult.range.first))
                match.text?.let { append(it) }
                append(result.substring(match.matchResult.range.last + 1))
            }
            if (result.isEmpty())
                return null
        }
    }
    return result
}

private val linkExtractor = LinkExtractor.builder().linkTypes(setOf(LinkType.URL, LinkType.WWW)).build()
