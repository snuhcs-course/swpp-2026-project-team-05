package com.swpp.team5.frameless.data

/** Client wait is bounded UX; the server remains the admission authority. */
internal fun boundedRetryAfter(value: String?): Long =
    (value?.trim()?.toLongOrNull() ?: 60L).coerceIn(1L, 3600L)
