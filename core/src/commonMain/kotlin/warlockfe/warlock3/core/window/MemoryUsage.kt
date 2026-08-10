package warlockfe.warlock3.core.window

/**
 * What one text stream is holding on to.
 *
 * Every count here is exact. [estimatedBytes] is a cost model (see [MemoryEstimate]), not a
 * measurement - the JVM offers no in-process way to size an object graph - so treat it as a way to
 * rank windows against each other, not as a heap figure.
 */
data class StreamMemoryUsage(
    val streamId: String,
    /** Lines currently visible in the window, after any name filter. */
    val shownLines: Int,
    /** Lines in the scrollback buffer, which is what [maxLines] caps. */
    val bufferedLines: Int,
    /**
     * Lines the stream still references, including ones already evicted from the buffer but not yet
     * dropped from the displayed list's backing (it compacts lazily). A [heldLines] far above
     * [bufferedLines] means something is pinning lines past their eviction.
     */
    val heldLines: Int,
    val maxLines: Int,
    val textCharacters: Long,
    /**
     * Entries in the component index: one per server component referenced by a buffered line, so a
     * line mentioning several counts several times and the total can sit well above [bufferedLines].
     *
     * Pruned as lines are evicted, so it tracks the buffer rather than the connection. What is worth
     * noticing is a count out of proportion to how many components the window's lines actually
     * carry, or one that climbs while [bufferedLines] holds steady.
     */
    val componentReferences: Long,
    val estimatedBytes: Long,
)

/** What a connection's window registry is holding, across all of its streams and panels. */
data class WindowMemoryUsage(
    val streams: List<StreamMemoryUsage>,
    val panelCount: Int,
) {
    val estimatedBytes: Long get() = streams.sumOf { it.estimatedBytes }

    companion object {
        val EMPTY = WindowMemoryUsage(streams = emptyList(), panelCount = 0)
    }
}

/**
 * A rough per-object cost model for [StreamMemoryUsage.estimatedBytes].
 *
 * Sized for a 64-bit JVM with compressed references: 16-byte object headers, 4-byte references, and
 * compact strings, which store ASCII (all game text) at one byte per character. The numbers are
 * deliberately coarse - the point is to show which window is holding the memory, and roughly how
 * much, without walking an object graph the JVM will not let us measure anyway.
 */
object MemoryEstimate {
    /** A small object: header plus a handful of fields or references. */
    const val OBJECT_BYTES = 48L

    /** An AnnotatedString span: the range object plus the SpanStyle it points at. */
    const val SPAN_BYTES = 120L

    /** One reference-sized slot, e.g. an entry in a list's backing array. */
    const val REFERENCE_BYTES = 4L

    private const val STRING_BASE_BYTES = 40L

    /** A String of [length] characters, including its backing byte array. */
    fun string(length: Int): Long = STRING_BASE_BYTES + length
}
