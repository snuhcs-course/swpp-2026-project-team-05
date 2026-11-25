package com.swpp.team5.frameless.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swpp.team5.frameless.data.FindingUi
import com.swpp.team5.frameless.ui.theme.Fl

/*
 * Paired expression comparison card (specs/features/paired-expression-comparison.md, Draft).
 *
 * Supplements the existing comparison sheet: it puts the source article's exact stored marker next to
 * the currently paged comparison article's exact stored evidence marker. It never judges, ranks, or
 * labels either side and never filters or rewrites stored evidence.
 */

internal const val ExpressionComparisonHeading = "표현 비교"
internal const val ExpressionComparisonSourceLabel = "현재 기사"
internal const val ExpressionComparisonTargetLabel = "다른 보도"
internal const val ExpressionComparisonTargetNote = "현재 비교 기사"
internal const val ExpressionComparisonConnector = "같은 내용, 다른 표현"

private val ExpressionMarkerTint = Color(0xFFE9E1F5)

/** One side of the card: a publisher and its exact stored marker and sentence. */
internal data class ExpressionSide(val publisher: String, val marker: String, val sentence: String)

internal data class ExpressionComparison(val source: ExpressionSide, val target: ExpressionSide)

/**
 * The card's data for the comparison page at [pageIndex], or null when the card must not render:
 * the source publisher or source marker is blank, the page does not exist, or that page has no
 * evidence with a non-blank marker. The target is the page's first evidence with a non-blank marker. All text is
 * the stored text, unchanged.
 */
internal fun expressionComparisonFor(
    sourcePublisher: String,
    finding: FindingUi,
    pageIndex: Int
): ExpressionComparison? {
    if (sourcePublisher.isBlank() || finding.markerText.isBlank()) return null
    val page = finding.comparisons.getOrNull(pageIndex) ?: return null
    val evidence = page.evidence.firstOrNull { it.markerText.isNotBlank() } ?: return null
    return ExpressionComparison(
        source = ExpressionSide(sourcePublisher, finding.markerText, finding.sentenceText),
        target = ExpressionSide(page.publisher.name, evidence.markerText, evidence.sentenceText)
    )
}

/** The sentence with its marker underlined where the marker occurs in it; the text itself is unchanged. */
private fun contextText(side: ExpressionSide): AnnotatedString = buildAnnotatedString {
    append(side.sentence)
    val start = side.sentence.indexOf(side.marker)
    if (start >= 0) {
        addStyle(
            SpanStyle(color = Fl.Ink2, textDecoration = TextDecoration.Underline),
            start,
            start + side.marker.length
        )
    }
}

@Composable
private fun SideLabel(text: String, filled: Boolean) {
    val shape = RoundedCornerShape(5.dp)
    Text(
        text,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        color = if (filled) Fl.OnAccent else Fl.Accent,
        modifier = Modifier
            .then(if (filled) Modifier.background(Fl.Accent, shape) else Modifier.border(1.dp, Fl.Accent, shape))
            .padding(horizontal = 6.dp, vertical = 1.dp)
    )
}

@Composable
private fun ExpressionSideBlock(side: ExpressionSide, label: String, filled: Boolean, note: String?) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            SideLabel(label, filled)
            Text(
                side.publisher,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = Fl.Ink2,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (note != null) Text(note, fontSize = 10.sp, color = Fl.Muted)
        }
        Text(
            buildAnnotatedString {
                append("“")
                append(side.marker)
                append("”")
                addStyle(SpanStyle(background = ExpressionMarkerTint), 0, length)
            },
            fontFamily = Fl.Serif,
            fontSize = 17.sp,
            lineHeight = 24.sp,
            fontWeight = FontWeight.Bold,
            color = Fl.Ink,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        if (side.sentence.isNotBlank()) {
            Text(
                contextText(side),
                fontFamily = Fl.Serif,
                fontSize = 12.sp,
                lineHeight = 18.sp,
                color = Fl.Muted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ExpressionConnector() {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(Modifier.weight(1f).height(1.dp).background(Fl.Line))
        Text(ExpressionComparisonConnector, fontSize = 10.sp, color = Fl.Muted)
        Box(Modifier.weight(1f).height(1.dp).background(Fl.Line))
    }
}

/** Vertical source-over-target card. The source block stays fixed; the target follows the pager. */
@Composable
internal fun ExpressionComparisonCard(comparison: ExpressionComparison, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier.fillMaxWidth()
            .semantics { contentDescription = ExpressionComparisonHeading }
    ) {
        Text(
            ExpressionComparisonHeading,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = Fl.Ink,
            modifier = Modifier.padding(bottom = 6.dp).semantics { heading() }
        )
        Column(
            Modifier.fillMaxWidth()
                .background(Fl.Card, shape)
                .border(1.dp, Fl.Line, shape)
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            ExpressionSideBlock(comparison.source, ExpressionComparisonSourceLabel, filled = true, note = null)
            ExpressionConnector()
            ExpressionSideBlock(
                comparison.target,
                ExpressionComparisonTargetLabel,
                filled = false,
                note = ExpressionComparisonTargetNote
            )
        }
    }
}
