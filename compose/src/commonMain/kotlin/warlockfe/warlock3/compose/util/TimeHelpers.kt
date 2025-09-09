package warlockfe.warlock3.compose.util

import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTime::class)
expect fun Instant.toTimeString(): String