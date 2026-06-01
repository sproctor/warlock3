package warlockfe.warlock3.compose.util

/**
 * Reads the entries of a zip archive held entirely in memory, returning a map of entry name to its
 * raw bytes. Directory entries are omitted. Implemented per-platform since there is no common zip
 * facility; not supported on iOS.
 */
expect fun readZipEntries(bytes: ByteArray): Map<String, ByteArray>
