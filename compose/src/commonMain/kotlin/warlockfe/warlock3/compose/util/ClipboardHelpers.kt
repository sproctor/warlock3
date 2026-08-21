package warlockfe.warlock3.compose.util

import androidx.compose.ui.platform.ClipEntry

/**
 * Plain-text conversions for [ClipEntry], which Compose does not offer in common code.
 *
 * `Clipboard` carries text as a platform [ClipEntry], and the helpers that read or build one from a
 * string are internal to Compose Foundation. Each platform does expose a public route to the same
 * thing, so these wrap that rather than fall back to the deprecated `ClipboardManager`.
 */
expect fun clipEntryOf(text: String): ClipEntry?

/** The entry's text, or null when it carries none (an image, a file, an unreadable transfer). */
expect fun ClipEntry.plainText(): String?
