package com.swpp.team5.frameless.ui.screens

import android.view.HapticFeedbackConstants
import com.swpp.team5.frameless.data.FindingUi
import kotlin.math.abs

internal const val PeekBandStartFraction = 0.45f
internal const val PeekBandEndFraction = 0.65f
internal const val PeekAnchorFraction = 0.55f
internal const val MaxPeekComparisonPublishers = 2
internal const val AnnotationHapticMinIntervalMillis = 150L

/** Uses only low-intensity, system-governed haptics; API 26 stays silent. */
internal fun annotationHapticFeedbackConstant(sdkInt: Int): Int? = when {
    sdkInt >= 34 -> HapticFeedbackConstants.SEGMENT_FREQUENT_TICK
    sdkInt >= 27 -> HapticFeedbackConstants.TEXT_HANDLE_MOVE
    else -> null
}

/** A measured BODY marker in root coordinates, kept independent from Compose state. */
internal data class PeekMarkerPosition(
    val findingId: String,
    val centerY: Float,
    val readingOrder: Int,
    val visualLine: Int = readingOrder,
    val physicalCenterY: Float = centerY
)

/**
 * Gives separate findings on one rendered text line small virtual vertical slots.
 * This affects only selection timing; article layout and marker drawing stay unchanged.
 */
internal fun spaceSameLinePeekMarkers(
    markers: List<PeekMarkerPosition>,
    stepPx: Float
): List<PeekMarkerPosition> {
    if (stepPx <= 0f) return markers
    return markers.groupBy { it.visualLine }.values.flatMap { lineMarkers ->
        if (lineMarkers.size == 1) return@flatMap lineMarkers
        val ordered = lineMarkers.sortedBy { it.readingOrder }
        val lineCenter = ordered.map { it.physicalCenterY }.average().toFloat()
        val midpoint = (ordered.lastIndex) / 2f
        ordered.mapIndexed { index, marker ->
            marker.copy(centerY = lineCenter + (index - midpoint) * stepPx)
        }
    }
}

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
    viewportBottom: Float,
    atScrollEnd: Boolean = false
): String? {
    val height = viewportBottom - viewportTop
    if (height <= 0f) return null
    if (atScrollEnd) {
        return markers.asSequence()
            .filter { it.physicalCenterY in viewportTop..viewportBottom }
            .maxByOrNull { it.readingOrder }
            ?.findingId
    }
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

