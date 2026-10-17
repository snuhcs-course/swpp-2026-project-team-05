package com.swpp.team5.frameless.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swpp.team5.frameless.data.ExpressionDifference
import com.swpp.team5.frameless.ui.state.Clip
import com.swpp.team5.frameless.ui.state.InputSentence
import com.swpp.team5.frameless.ui.state.MatchKind
import com.swpp.team5.frameless.ui.state.kindOf
import com.swpp.team5.frameless.ui.state.two
import com.swpp.team5.frameless.ui.theme.Fl
import com.swpp.team5.frameless.ui.theme.TagPill

/**
 * 입력 기사 문장 하나.
 * 문장 배경색은 서버 relation 값으로만 정한다: 같은 내용이 있으면 청록, 다른 해석/표현만 있으면 라일락, 대응이 없으면 회색.
 */
@Composable
internal fun ArticleSentenceCard(
    number: Int,
    sentence: InputSentence,
    matches: List<Clip>,
    selected: Boolean,
    inSelectedIssue: Boolean,
    expressions: List<ExpressionDifference>,
    onClick: () -> Unit
) {
    val sameCount = matches.filter { kindOf(it.relationRaw) == MatchKind.SAME }.map { it.article.articleIndex }.distinct().size
    val diffCount = matches.filter { kindOf(it.relationRaw) == MatchKind.DIFF }.map { it.article.articleIndex }.distinct().size
    val tint = when {
        sameCount > 0 -> Fl.TintSame
        diffCount > 0 -> Fl.TintDiff
        else -> Fl.TintNone
    }
    val text = remember(sentence.quote, tint, expressions) { tinted(sentence.quote, tint, expressions) }
    val shape = RoundedCornerShape(10.dp)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                when {
                    selected -> Modifier.border(2.dp, Fl.Accent, shape)
                    inSelectedIssue -> Modifier.border(1.dp, Fl.Line, shape)
                    else -> Modifier
                }
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Text(
                text = two(number),
                fontSize = 11.sp,
                color = Fl.Muted,
                modifier = Modifier.padding(top = 4.dp, end = 10.dp)
            )
            Text(
                text = text,
                fontFamily = Fl.Serif,
                fontSize = 15.sp,
                lineHeight = 28.sp,
                color = Fl.Ink,
                modifier = Modifier.weight(1f)
            )
        }
        Row(
            modifier = Modifier
                .padding(start = 26.dp)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (sameCount > 0) TagPill("같은 내용 · ${sameCount}곳", Fl.TintSame, Fl.InkSame)
            if (diffCount > 0) TagPill("다른 해석/표현 · ${diffCount}곳", Fl.TintDiff, Fl.InkDiff)
            if (sameCount == 0 && diffCount == 0) {
                Text("다른 기사에서 대응 문장을 찾지 못했어요", fontSize = 11.sp, color = Fl.Muted)
            }
            if (sentence.issueIndexes.isNotEmpty()) {
                Text("쟁점 " + sentence.issueIndexes.joinToString(", ") { two(it + 1) }, fontSize = 11.sp, color = Fl.Muted)
            }
        }
    }
}

/** 문장 전체에 배경색을 칠하고, 서버가 보낸 rubric 표현이 있으면 그 부분에 밑줄을 긋는다 */
internal fun tinted(text: String, tint: Color?, expressions: List<ExpressionDifference>): AnnotatedString {
    val ranges = expressions.mapNotNull { e ->
        val start = text.indexOf(e.span)
        if (start < 0 || e.span.isBlank()) null else Triple(start, start + e.span.length, e)
    }.sortedBy { it.first }
    return buildAnnotatedString {
        val base = if (tint != null) SpanStyle(background = tint) else SpanStyle()
        withStyle(base) {
            var cursor = 0
            for ((start, end, e) in ranges) {
                if (start < cursor) continue
                append(text.substring(cursor, start))
                val mark = if (e.maxMarkedness >= 1) {
                    SpanStyle(textDecoration = TextDecoration.Underline, fontWeight = FontWeight.Bold)
                } else SpanStyle()
                withStyle(mark) { append(text.substring(start, end)) }
                cursor = end
            }
            append(text.substring(cursor))
        }
    }
}
