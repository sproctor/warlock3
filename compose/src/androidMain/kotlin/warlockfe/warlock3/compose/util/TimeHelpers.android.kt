package warlockfe.warlock3.compose.util

import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.TimeZone
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlin.time.toJavaInstant

private val timeFormatter = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)

@OptIn(ExperimentalTime::class)
actual fun Instant.toTimeString(): String {
    val zonedDateTime = toJavaInstant().atZone(TimeZone.getDefault().toZoneId())
    return timeFormatter.format(zonedDateTime)
}
