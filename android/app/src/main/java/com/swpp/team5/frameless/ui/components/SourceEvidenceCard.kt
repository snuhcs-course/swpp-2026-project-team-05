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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swpp.team5.frameless.data.ArticleRef
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
                TagPill("다른 해석/표현", Fl.TintDiff, Fl.InkDiff)
                Caption(clip.relationRaw)
            }
            MatchKind.OTHER -> if (clip.relationRaw.isNotBlank()) Caption("관계: ${clip.relationRaw}")
            MatchKind.SAME -> Unit
        }
        QuoteText(clip.quote, tint)
        if (clip.alsoIn.isNotEmpty()) {
            Caption("같은 문장이 쟁점 " + clip.alsoIn.joinToString(", ") { two(it + 1) } + "에도 연결돼 있어요")
        }
    }
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
