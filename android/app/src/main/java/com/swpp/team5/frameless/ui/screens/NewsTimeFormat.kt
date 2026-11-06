package com.swpp.team5.frameless.ui.screens

import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val newsTimestampFormatter =
    DateTimeFormatter.ofPattern("M월 d일 HH:mm", Locale.KOREAN)

internal fun formatNewsTimestamp(raw: String): String = try {
    OffsetDateTime.parse(raw)
        .atZoneSameInstant(ZoneId.systemDefault())
        .format(newsTimestampFormatter)
} catch (_: Exception) {
    raw
}
