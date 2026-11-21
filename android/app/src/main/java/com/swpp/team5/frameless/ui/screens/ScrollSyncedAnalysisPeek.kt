package com.swpp.team5.frameless.ui.screens

import com.swpp.team5.frameless.data.FindingUi
import kotlin.math.abs

internal const val PeekDwellMillis = 500L
internal const val PeekBandStartFraction = 0.45f
internal const val PeekBandEndFraction = 0.65f
internal const val PeekAnchorFraction = 0.55f
internal const val MaxPeekComparisonPublishers = 2

/** A measured BODY marker in root coordinates, kept independent from Compose state. */
internal data class PeekMarkerPosition(
    val findingId: String,
    val centerY: Float,
    val readingOrder: Int
)

/** One exact expression used by another publisher. */
internal data class PeekComparisonExpression(
    val publisher: String,
    val marker: String
)

/** Small presentation model; no reviewed text is generated or rewritten here. */
internal data class AnalysisPeekContent(
    val marker: String,
    val explanation: String,
    val comparisons: List<PeekComparisonExpression>,
    val remainingPublisherCount: Int
)

/**
 * Chooses the eligible marker nearest the 55% reading anchor.
 * Band boundaries are inclusive and reading order resolves an exact tie.
 */
internal fun selectScrollSyncedPeekCandidate(
    markers: List<PeekMarkerPosition>,
    viewportTop: Float,
    viewportBottom: Float
): String? {
    val height = viewportBottom - viewportTop
    if (height <= 0f) return null
    val bandTop = viewportTop + height * PeekBandStartFraction
    val bandBottom = viewportTop + height * PeekBandEndFraction
    val anchor = viewportTop + height * PeekAnchorFraction
    return markers.asSequence()
        .filter { it.centerY in bandTop..bandBottom }
        .minWithOrNull(compareBy<PeekMarkerPosition>({ abs(it.centerY - anchor) }, { it.readingOrder }))
        ?.findingId
}

internal fun analysisPeekContent(finding: FindingUi): AnalysisPeekContent {
    val publisherRows = finding.comparisons.asSequence()
        .mapNotNull { page ->
            val marker = firstEvidenceMarker(page)?.takeIf(String::isNotBlank) ?: return@mapNotNull null
            page.publisher.code to PeekComparisonExpression(page.publisher.name, marker)
        }
        .distinctBy { it.first }
        .map { it.second }
        .toList()
    return AnalysisPeekContent(
        marker = finding.markerText,
        explanation = finding.explanation,
        comparisons = publisherRows.take(MaxPeekComparisonPublishers),
        remainingPublisherCount = (publisherRows.size - MaxPeekComparisonPublishers).coerceAtLeast(0)
    )
}

