package com.swpp.team5.frameless.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swpp.team5.frameless.data.ComparisonArticleUi
import com.swpp.team5.frameless.data.FindingUi
import com.swpp.team5.frameless.ui.theme.Fl

/** One side of a paired expression, taken verbatim from stored finding or comparison evidence. */
internal data class ExpressionSide(
    /** Publisher name as stored; may be blank for the current article if the reader has none. */
    val publisher: String,
    val phrase: String,
    /** Surrounding sentence, possibly windowed with a leading "…"; empty when it would only repeat [phrase]. */
    val context: String,
    /** Where [phrase] sits inside [context], or null if it does not occur there. */
    val phraseInContext: IntRange?
)

internal data class ExpressionPairUi(val current: ExpressionSide, val compared: ExpressionSide)

internal const val PAIR_HEADING = "표현 비교"
internal const val PAIR_CURRENT_ROLE = "현재 기사"
internal const val PAIR_COMPARED_ROLE = "다른 보도"
internal const val PAIR_CONNECTOR = "같은 내용을 이렇게 다르게"

/**
 * Pairs the selected finding's phrase with the matching phrase in one compared article.
 *
 * The compared side uses the page's first BODY evidence with a non-blank marker (the same passage the page
 * scrolls to), falling back to the first evidence of any scope. Returns null when either phrase is missing; no
 * text is ever generated or paraphrased.
 */
internal fun expressionPairFor(
    finding: FindingUi,
    sourcePublisher: String,
    page: ComparisonArticleUi
): ExpressionPairUi? {
    if (finding.markerText.isBlank()) return null
    val evidence = page.evidence.firstOrNull { it.scope == "BODY" && it.markerText.isNotBlank() }
        ?: page.evidence.firstOrNull { it.markerText.isNotBlank() }
        ?: return null
    return ExpressionPairUi(
        current = expressionSide(sourcePublisher, finding.markerText, finding.sentenceText),
        compared = expressionSide(page.publisher.name, evidence.markerText, evidence.sentenceText)
    )
}

private fun expressionSide(publisher: String, phrase: String, sentence: String): ExpressionSide {
    val cleanPhrase = phrase.trim()
    val cleanSentence = sentence.trim()
    if (cleanSentence.isEmpty() || cleanSentence == cleanPhrase) {
        return ExpressionSide(publisher.trim(), cleanPhrase, "", null)
    }
    val (context, range) = contextWindow(cleanSentence, cleanPhrase)
    return ExpressionSide(publisher.trim(), cleanPhrase, context, range)
}

/**
 * Keeps the phrase visible in a short context preview: when the phrase starts more than [lead] characters into
 * the sentence, the sentence is cut to start [lead] characters before it, prefixed with "…". Characters are
 * never altered otherwise.
 */
internal fun contextWindow(sentence: String, phrase: String, lead: Int = 18): Pair<String, IntRange?> {
    val start = if (phrase.isEmpty()) -1 else sentence.indexOf(phrase)
    if (start < 0) return sentence to null
    if (start <= lead) return sentence to (start until start + phrase.length)
    val cut = start - lead
    val windowed = "…" + sentence.substring(cut)
    val shifted = start - cut + 1
    return windowed to (shifted until shifted + phrase.length)
}

private object PairTokens {
    val Card = Color(0xFFFCFBF8)
    val CardBorder = Color(0xFFD9D4E8)
    val PhraseMark = Color(0xFFE6DFF5)
    val Rule = Color(0xFFDCD7E8)
}

/**
 * Compact comparison card at the top of a comparison page: heading, then one off-white card with the current
 * article's expression, a centred connector between hairlines, and the other report's expression. Both sides use
 * one shared layout (pill, publisher, phrase, one context line); the filled versus outlined pill only marks which
 * article is open, never a judgment. Each side is read by screen readers as one unit; the hairlines are silent.
 */
@Composable
internal fun ExpressionPairPanel(pair: ExpressionPairUi, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(14.dp)
    Column(modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 14.dp)) {
        Text(PAIR_HEADING, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = HomeTokens.Navy)
        Spacer(Modifier.height(8.dp))
        Column(
            Modifier.fillMaxWidth()
                .background(PairTokens.Card, shape)
                .border(1.dp, PairTokens.CardBorder, shape)
                .padding(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 14.dp)
        ) {
            ExpressionSideBlock(PAIR_CURRENT_ROLE, filledTag = true, side = pair.current)
            PairConnector()
            ExpressionSideBlock(PAIR_COMPARED_ROLE, filledTag = false, side = pair.compared)
        }
    }
}

@Composable
private fun SideTag(text: String, filled: Boolean) {
    val shape = RoundedCornerShape(5.dp)
    // Same size either way: both carry the 1dp border; only the fill differs.
    Text(
        text,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        color = if (filled) Color.White else HomeTokens.Navy,
        maxLines = 1,
        modifier = Modifier
            .background(if (filled) HomeTokens.Navy else Color.Transparent, shape)
            .border(1.dp, HomeTokens.Navy, shape)
            .padding(horizontal = 7.dp, vertical = 2.dp)
    )
}

@Composable
private fun ExpressionSideBlock(role: String, filledTag: Boolean, side: ExpressionSide) {
    val phrase = remember(side) {
        buildAnnotatedString {
            append("“")
            pushStyle(SpanStyle(background = PairTokens.PhraseMark))
            append(side.phrase)
            pop()
            append("”")
        }
    }
    val contextInk = Fl.Ink2
    val context = remember(side, contextInk) {
        buildAnnotatedString {
            append(side.context)
            side.phraseInContext?.let { range ->
                addStyle(
                    SpanStyle(color = contextInk, textDecoration = TextDecoration.Underline),
                    range.first, range.last + 1
                )
            }
        }
    }
    Column(Modifier.fillMaxWidth().semantics(mergeDescendants = true) { }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SideTag(role, filledTag)
            if (side.publisher.isNotEmpty()) {
                Spacer(Modifier.width(8.dp))
                Text(
                    side.publisher,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Fl.Ink2,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            phrase,
            fontFamily = Fl.Serif,
            fontSize = 22.sp,
            lineHeight = 30.sp,
            fontWeight = FontWeight.Bold,
            color = Fl.Ink,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        if (side.context.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Text(
                context,
                fontFamily = Fl.Serif,
                fontSize = 12.5.sp,
                lineHeight = 18.sp,
                color = Fl.Muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** Centred connector text between two hairlines; the hairlines carry no semantics. */
@Composable
private fun PairConnector() {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.weight(1f).height(1.dp).background(PairTokens.Rule))
        Text(
            PAIR_CONNECTOR,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Medium,
            color = Fl.Muted,
            modifier = Modifier.padding(horizontal = 10.dp)
        )
        Box(Modifier.weight(1f).height(1.dp).background(PairTokens.Rule))
    }
}
