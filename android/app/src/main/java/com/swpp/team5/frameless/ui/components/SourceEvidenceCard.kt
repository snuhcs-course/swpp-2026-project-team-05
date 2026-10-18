package com.swpp.team5.frameless.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swpp.team5.frameless.BuildConfig
import com.swpp.team5.frameless.data.ArticleRef
import com.swpp.team5.frameless.data.Relation
import com.swpp.team5.frameless.ui.state.AnswerView
import com.swpp.team5.frameless.ui.state.ExpressionItem
import com.swpp.team5.frameless.ui.state.InformationItem
import com.swpp.team5.frameless.ui.state.cleanQuote
import com.swpp.team5.frameless.ui.state.Clip
import com.swpp.team5.frameless.ui.state.MatchKind
import com.swpp.team5.frameless.ui.state.kindOf
import com.swpp.team5.frameless.ui.state.two
import com.swpp.team5.frameless.ui.theme.Caption
import com.swpp.team5.frameless.ui.theme.Fl
import com.swpp.team5.frameless.ui.theme.SourceLink
import com.swpp.team5.frameless.ui.theme.TagPill

/**
 * 출처 근거 카드: 번호 · 기사 제목 · 원문 링크 · 인용문.
 * 같은 내용은 청록, 다른 해석/표현은 라일락 배경으로 인용문을 칠한다.
 * 다른 해석/표현일 때는 서버가 보낸 관계 값을 작게 남겨 추적할 수 있게 한다.
 */
@Composable
internal fun SourceEvidenceCard(number: Int, clip: Clip) {
    val uriHandler = LocalUriHandler.current
    val kind = kindOf(clip.relationRaw)
    val tint = when (kind) {
        MatchKind.SAME -> Fl.TintSame
        MatchKind.DIFF -> Fl.TintDiff
        MatchKind.OTHER -> null
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Fl.Inset, RoundedCornerShape(10.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Caption(two(number), modifier = Modifier.padding(end = 8.dp))
            Text(
                text = clip.article.title,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = Fl.Ink,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            val url = clip.article.url
            if (url != null) SourceLink(onClick = { uriHandler.openUri(url) })
            else Caption("링크 없음")
        }
        when (kind) {
            MatchKind.DIFF -> Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                TagPill(Relation.from(clip.relationRaw).label, Fl.TintDiff, Fl.InkDiff)
                // 서버 원래 값(opposes 등)은 개발 빌드에서만 작게 남긴다
                if (BuildConfig.DEBUG) Text(clip.relationRaw, fontSize = 10.sp, color = Fl.Line)
            }
            MatchKind.OTHER -> if (clip.relationRaw.isNotBlank()) {
                val relation = Relation.from(clip.relationRaw)
                Caption("관계: ${if (relation == Relation.OTHER) "분류 확인 필요" else relation.label}")
            }
            MatchKind.SAME -> Unit
        }
        QuoteText(clip.quote, tint)
        provenanceLines(clip).forEach { Caption(it) }
        if (clip.alsoIn.isNotEmpty()) {
            Caption("같은 문장이 주장 " + clip.alsoIn.joinToString(", ") { two(it + 1) } + "에도 연결돼 있어요")
        }
    }
}

/** 서버가 확정한 발화 귀속만 설명한다. 메타데이터가 없거나 불확실하면 매체 입장으로 승격하지 않는다. */
internal fun provenanceLines(clip: Clip): List<String> = buildList {
    when (clip.relationOwnerRaw) {
        "reporter" -> add("관계 판정 주체: 기자 서술")
        "attributed_speaker" -> {
            val speaker = clip.speaker.ifBlank { "발언자 미확인" }
            val kind = when (clip.speechKindRaw) {
                "direct" -> "직접 인용"
                "indirect" -> "간접 인용"
                else -> "인용 발언"
            }
            add("관계 판정 주체: $speaker · $kind")
            add("인용된 발언과 비교한 결과예요. 매체의 입장을 뜻하지 않아요.")
        }
        else -> add("발화 주체 확인 필요 · 이 문장을 매체의 입장으로 해석하지 마세요.")
    }
    if (clip.hasMultipleVoices) {
        add("근거에 여러 발화 주체가 섞여 있어요. 원문에서 각각의 발언을 확인해 주세요.")
    }
    clip.relationWarning?.takeIf { it.isNotBlank() }?.let { add("관계 판정 보류: $it") }
}

/** 명조 인용문. tint가 있으면 문장 배경을 칠한다 */
@Composable
internal fun QuoteText(text: String, tint: Color?) {
    Text(
        text = tinted(text, tint, emptyList()),
        fontFamily = Fl.Serif,
        fontSize = 15.sp,
        lineHeight = 27.sp,
        color = Fl.Ink
    )
}

/** 같은 내용을 보도한 출처 이름을 한 줄씩 */
@Composable
internal fun OutletList(articles: List<ArticleRef>, dot: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        articles.forEach { a ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(8.dp).background(dot, CircleShape))
                Spacer(modifier = Modifier.width(8.dp))
                Text(a.title, fontSize = 12.sp, color = Fl.Ink2, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** 점선 상자: 비어 있는 상태 (예: 뚜렷한 차이를 찾지 못했어요.) */
@Composable
internal fun EmptyState(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .drawBehind {
                drawRoundRect(
                    color = Fl.Line,
                    cornerRadius = CornerRadius(10.dp.toPx()),
                    style = Stroke(width = 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)))
                )
            }
            .padding(14.dp)
    ) {
        Text(text, fontSize = 13.sp, lineHeight = 19.sp, color = Fl.Muted)
    }
}

/* ==================================================================
 * 판정 기준 v0.2.1 카드 — 서버가 rubric을 보낸 경우에만 쓰인다.
 * 값은 서버가 보낸 그대로 옮기고, 앱이 새 범주나 유표성을 만들지 않는다.
 * ================================================================== */

internal object RubricLabels {
    /** 표현 범주 짧은 이름 (스펙 §3) */
    val expression = mapOf(
        "A" to "보도 동사",
        "B" to "발언 명명",
        "C" to "행위 서술어",
        "D" to "정도 수식어",
        "E" to "평가 수식어",
        "F" to "명명",
        "G" to "양태·확신"
    )

    /** 원문에서 관찰한 정보 구성 요소. 항목이 곧 차이나 의도를 뜻하지 않는다. */
    val information = mapOf(
        "I1" to "주장 포함·대응 여부",
        "I2" to "제목·리드·본문 배치",
        "I3" to "배경 정보",
        "I4" to "추측·전망",
        "I5" to "직접/간접 인용 및 인용 주체"
    )

    /** 질문에 대한 문장이 없을 때 서버가 붙인 사유. 의도를 암시하지 않는 말로만 옮긴다. */
    fun absence(reason: String?): String? = when (reason) {
        "timing_impossible" -> "보도 시점상 다룰 수 없었던 내용이에요."
        "format" -> "기사 형식상 다루지 않는 범위예요."
        "editorial" -> "서버 사유 분류: editorial (의도 판정 아님)"
        else -> null
    }

    /** AI-generated display guard: null은 보류이며 0은 절대 중립이 아니다. */
    fun expressionTitle(item: ExpressionItem): String {
        val e = item.e
        val cats = e.categories.joinToString(" · ") { "${expression[it.code] ?: it.code}(${it.code})" }
        val mark = if (e.held || e.maxMarkedness == null) "유표성 보류"
            else "잠정 상대 유표성 ${e.maxMarkedness}"
        return "표현 차이: $cats · $mark"
    }
}

/** 카드 머리: 기사 제목 + 원문 링크 */
@Composable
private fun ArticleLine(article: ArticleRef?, fallbackTitle: String) {
    val uriHandler = LocalUriHandler.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = article?.title ?: fallbackTitle,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = Fl.Ink2,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        val url = article?.url
        if (url != null) SourceLink(onClick = { uriHandler.openUri(url) }) else Caption("링크 없음")
    }
}

/** 문장 안의 span만 밑줄로 강조해 보여준다 */
@Composable
private fun SentenceWithSpan(sentence: String, span: String) {
    val text = buildAnnotatedString {
        val start = sentence.indexOf(span)
        if (start < 0 || span.isBlank()) {
            append(sentence)
        } else {
            append(sentence.substring(0, start))
            withStyle(SpanStyle(textDecoration = TextDecoration.Underline, fontWeight = FontWeight.Bold)) {
                append(span)
            }
            append(sentence.substring(start + span.length))
        }
    }
    Text(text = text, fontFamily = Fl.Serif, fontSize = 15.sp, lineHeight = 27.sp, color = Fl.Ink)
}

@Composable
private fun RubricCardBox(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Fl.Inset, RoundedCornerShape(10.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) { content() }
}

/**
 * 표현 차이 카드. 반드시 원문 표현(span)·문장·출처 기사·원문 링크를 함께 보여준다.
 * baselineArticle: 기준 표현의 원문 기사와 링크
 */
@Composable
internal fun ExpressionEvidenceCard(item: ExpressionItem, baselineArticle: ArticleRef?) {
    val e = item.e
    val uriHandler = LocalUriHandler.current
    RubricCardBox {
        TagPill(RubricLabels.expressionTitle(item), Fl.TintDiff, Fl.InkDiff)
        ArticleLine(item.article, "기사 ${e.articleIndex}")
        SentenceWithSpan(cleanQuote(e.sentence), e.span)
        Text(
            text = "원문 표현 “${e.span}”",
            fontSize = 12.sp,
            color = Fl.Ink2
        )
        if (e.baselineText.isNotBlank()) {
            val from = "비교 기사" + (baselineArticle?.title?.let { " · $it" } ?: "")
            Text("비교 기준 “${e.baselineText}” ($from)", fontSize = 12.sp, color = Fl.Ink2)
            baselineArticle?.url?.let { url -> SourceLink(onClick = { uriHandler.openUri(url) }) }
        }
        e.target?.let { Caption("대상: $it") }
        if (e.held) Caption("비교 근거 또는 의미 판정을 더 확인해야 해 유표성을 보류했어요.")
        else Caption("실제 비교 원문에 대한 모델의 잠정 상대 판정이에요. 0도 절대 중립을 뜻하지 않아요.")
        if (e.note.isNotBlank()) Caption("설명(모델 정리): ${e.note}")
    }
}

/** 정보 구성 관찰 카드 (I1~I5) */
@Composable
internal fun InformationEvidenceCard(item: InformationItem) {
    val i = item.i
    RubricCardBox {
        TagPill("${i.code} · ${RubricLabels.information[i.code] ?: ""}", Fl.TintNone, Fl.Ink2)
        ArticleLine(item.article, "기사 ${i.articleIndex}")
        i.sentence?.let { QuoteText(cleanQuote(it), tint = null) }
        Text(i.value, fontSize = 12.sp, lineHeight = 18.sp, color = Fl.Ink2)
        i.target?.let { Caption("대상: $it") }
    }
}

/**
 * 직접 인용 안의 표현 (D1). 인용된 사람의 말이라 매체의 표현 차이로 세지 않고
 * I5 인용 정보로만 보여준다. 범주·유표성은 표시하지 않는다.
 */
@Composable
internal fun QuotedExpressionCard(item: ExpressionItem) {
    val e = item.e
    RubricCardBox {
        TagPill("I5 · 직접 인용 안의 표현", Fl.TintNone, Fl.Ink2)
        ArticleLine(item.article, "기사 ${e.articleIndex}")
        SentenceWithSpan(cleanQuote(e.sentence), e.span)
        Caption("인용된 발언 속 표현이라 매체의 표현 차이로 표시하지 않았어요.")
    }
}

/** 질문 단위 쟁점에서 기사 하나의 답 */
@Composable
internal fun AnswerCard(answer: AnswerView) {
    val a = answer.answer
    RubricCardBox {
        ArticleLine(answer.article, "기사 ${answer.articleIndex}")
        if (a.answered) {
            if (a.summary.isNotBlank()) Text(a.summary, fontSize = 13.sp, lineHeight = 19.sp, color = Fl.Ink)
            a.evidenceQuotes.forEach { QuoteText(cleanQuote(it), tint = null) }
            if (a.evidenceQuotes.isEmpty()) Caption("근거 인용문이 응답에 없어요.", color = Fl.Danger)
        } else {
            Text("이 기사에서 이 질문에 해당하는 문장을 찾지 못했어요.", fontSize = 13.sp, color = Fl.Muted)
            RubricLabels.absence(a.absenceReason)?.let { Caption(it) }
            if (a.relevance == "peripheral") Caption("쟁점과 거리가 있는 내용으로 분류됐어요.")
        }
    }
}
