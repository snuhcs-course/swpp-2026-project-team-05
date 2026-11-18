package com.swpp.team5.frameless.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swpp.team5.frameless.data.ComparisonArticleUi
import com.swpp.team5.frameless.data.FindingUi
import com.swpp.team5.frameless.ui.theme.Fl

/*
 * Reader "비교 포인트" entry section (specs/features/reader-comparison-summary.md, Draft).
 *
 * Supplements the existing comparison sheet: it shows publisher names with their exact stored
 * markers and opens that sheet. It never renders comparisonOverview, explanation, or A–G labels.
 */

internal const val ComparisonPointsHeading = "비교 포인트"
internal const val ComparisonPointAction = "표현 비교 보기"
internal const val MaxComparisonPoints = 2

/** One publisher and the exact marker it used, as stored. */
internal data class MarkerLine(val publisher: String, val marker: String)

/** A Reader comparison point: the current article's marker and one real compared outlet's marker. */
internal data class ComparisonPoint(
    val finding: FindingUi,
    val source: MarkerLine,
    val target: MarkerLine
)

/** The first non-blank evidence marker of a compared article, exactly as stored, or null. */
internal fun firstEvidenceMarker(page: ComparisonArticleUi): String? =
    page.evidence.firstOrNull { it.markerText.isNotBlank() }?.markerText

/**
 * Up to [limit] comparison points in reading order (title before body, then marker start).
 *
 * A finding qualifies only when its own marker is non-blank and at least one compared article has a
 * non-blank evidence marker; the first such article (stored page order) is used. Points that would
 * render the same lines — same source marker, compared publisher and compared marker — appear once.
 */
internal fun comparisonPoints(
    sourcePublisher: String,
    findings: List<FindingUi>,
    limit: Int = MaxComparisonPoints
): List<ComparisonPoint> {
    if (limit <= 0) return emptyList()
    val seenFindings = mutableSetOf<String>()
    val seenLines = mutableSetOf<Triple<String, String, String>>()
    return findings
        .sortedWith(compareBy<FindingUi>({ if (it.scope == "TITLE") 0 else 1 }, { it.markerRange.start }))
        .mapNotNull { finding ->
            if (finding.markerText.isBlank()) return@mapNotNull null
            val page = finding.comparisons.firstOrNull { firstEvidenceMarker(it) != null }
                ?: return@mapNotNull null
            val point = ComparisonPoint(
                finding = finding,
                source = MarkerLine(sourcePublisher, finding.markerText),
                target = MarkerLine(page.publisher.name, firstEvidenceMarker(page)!!)
            )
            val key = Triple(finding.markerText, page.publisher.code, point.target.marker)
            if (!seenFindings.add(finding.id) || !seenLines.add(key)) null else point
        }
        .take(limit)
}

/** One rendered line: publisher, two spaces, quoted exact marker. */
internal fun markerLineText(line: MarkerLine): String = "${line.publisher}  “${line.marker}”"

/** Every string the section renders, in order. */
internal fun comparisonPointTexts(points: List<ComparisonPoint>): List<String> =
    if (points.isEmpty()) emptyList()
    else listOf(ComparisonPointsHeading) + points.flatMap {
        listOf(markerLineText(it.source), markerLineText(it.target), ComparisonPointAction)
    }

/** The Reader's finding selection: which finding is selected and whether its comparison sheet is open. */
internal data class FindingSelection(val finding: FindingUi?, val detailOpen: Boolean) {
    companion object {
        val None = FindingSelection(null, false)
    }
}

/** The existing preview action: a comparison-backed finding opens its sheet; a source-only one stays a preview. */
internal fun previewDetailSelection(current: FindingSelection): FindingSelection {
    val finding = current.finding ?: return current
    return if (finding.comparisons.isNotEmpty()) FindingSelection(finding, detailOpen = true) else current
}

/** A comparison point opens exactly the state the preview action opens for the same finding. */
internal fun comparisonPointSelection(finding: FindingUi): FindingSelection =
    previewDetailSelection(FindingSelection(finding, detailOpen = false))

/** One line as a single text: muted publisher, then the quoted marker in the article serif. */
@Composable
private fun MarkerLineText(line: MarkerLine) {
    Text(
        buildAnnotatedString {
            withStyle(SpanStyle(color = Fl.Muted, fontSize = 12.sp)) { append(line.publisher) }
            append("  ")
            withStyle(SpanStyle(fontFamily = Fl.Serif, fontWeight = FontWeight.SemiBold, color = Fl.Ink)) {
                append("“${line.marker}”")
            }
        },
        fontSize = 15.sp,
        lineHeight = 21.sp,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis
    )
}

/** Reader entry point above the title. Renders nothing when there are no points. */
@Composable
internal fun ComparisonPointsSection(
    points: List<ComparisonPoint>,
    onOpen: (FindingUi) -> Unit,
    modifier: Modifier = Modifier
) {
    if (points.isEmpty()) return
    Column(
        modifier.fillMaxWidth()
            .background(Fl.Inset, RoundedCornerShape(10.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .semantics { contentDescription = ComparisonPointsHeading }
    ) {
        Text(
            ComparisonPointsHeading,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = Fl.Ink,
            modifier = Modifier.semantics { heading() }
        )
        points.forEachIndexed { index, point ->
            if (index > 0) HorizontalDivider(color = Fl.Line)
            Column(
                Modifier.fillMaxWidth()
                    .clickable(onClickLabel = ComparisonPointAction, role = Role.Button) { onOpen(point.finding) }
                    .padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                MarkerLineText(point.source)
                MarkerLineText(point.target)
                Row(Modifier.fillMaxWidth()) {
                    Spacer(Modifier.weight(1f))
                    Text(
                        "$ComparisonPointAction  ›",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Fl.Accent,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }
            }
        }
    }
}
