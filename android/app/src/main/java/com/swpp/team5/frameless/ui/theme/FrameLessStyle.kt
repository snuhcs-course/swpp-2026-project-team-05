package com.swpp.team5.frameless.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * FrameLESS 화면 스타일. 기존 ui.theme(Color.kt, Theme.kt)와 이름이 겹치지 않게 Fl 아래에 둔다.
 *
 * 방향: 연한 회청색 바탕 위 둥근 흰 카드, 네이비 알약 버튼, 명조 본문 + 문장 배경 하이라이트.
 * 하이라이트 색은 정당 상징색(빨강·파랑)과 겹치지 않게 청록(같은 내용)과 라일락(다른 해석/표현)만 쓴다.
 */
internal object Fl {
    val Bg = Color(0xFFEDF0F4)
    val Card = Color(0xFFFBFCFD)
    val Inset = Color(0xFFF3F5F8)
    val Ink = Color(0xFF161A23)
    val Ink2 = Color(0xFF3A4150)
    val Muted = Color(0xFF5B6372)
    val Line = Color(0xFFD6DBE3)
    val Accent = Color(0xFF27324A)
    val OnAccent = Color(0xFFFFFFFF)

    /** 대응 문장이 없는 문장 배경 */
    val TintNone = Color(0xFFE1E4EA)
    /** 같은 내용 */
    val TintSame = Color(0xFFD1E7E5)
    val InkSame = Color(0xFF0F6A67)
    /** 다른 해석/표현 */
    val TintDiff = Color(0xFFE4DDF2)
    val InkDiff = Color(0xFF5E3FA0)

    val Danger = Color(0xFFA3271E)

    /** 기사 본문과 제목 */
    val Serif = FontFamily.Serif
}

internal val CardShape = RoundedCornerShape(12.dp)
internal val PillShape = RoundedCornerShape(50)

/** 흰 카드 */
@Composable
internal fun FlCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier = modifier
            .background(Fl.Card, CardShape)
            .border(1.dp, Fl.Line, CardShape)
    ) { content() }
}

/** 작은 회색 캡션 */
@Composable
internal fun Caption(text: String, modifier: Modifier = Modifier, color: Color = Fl.Muted) {
    Text(text = text, fontSize = 11.sp, color = color, modifier = modifier)
}

/** 섹션 제목 + 오른쪽 개수 */
@Composable
internal fun SectionTitle(text: String, count: Int? = null) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text(text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Fl.Ink, modifier = Modifier.weight(1f, fill = false))
        if (count != null) {
            Spacer(modifier = Modifier.width(6.dp))
            Text("${count}곳", fontSize = 12.sp, color = Fl.Muted)
        }
    }
}

@Composable
internal fun HRule() {
    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Fl.Line))
}

/** 색이 칠해진 작은 알약 라벨 (예: 같은 내용 · 3곳) */
@Composable
internal fun TagPill(text: String, bg: Color, fg: Color) {
    Text(
        text = text,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        color = fg,
        maxLines = 1,
        modifier = Modifier
            .background(bg, PillShape)
            .padding(horizontal = 9.dp, vertical = 3.dp)
    )
}

/** 네이비로 채운 알약 버튼 */
@Composable
internal fun FilledPill(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .background(Fl.Accent, PillShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Fl.OnAccent)
    }
}

/** 테두리만 있는 알약 버튼 */
@Composable
internal fun OutlinedPill(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .background(Fl.Card, PillShape)
            .border(1.dp, Fl.Line, PillShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Fl.Ink)
    }
}

/** 원문 링크. 누르면 브라우저로 연다 */
@Composable
internal fun SourceLink(onClick: () -> Unit, label: String = "원문 보기") {
    Text(
        text = label,
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        color = Fl.Accent,
        textDecoration = TextDecoration.Underline,
        modifier = Modifier.clickable(onClick = onClick).padding(horizontal = 4.dp, vertical = 6.dp)
    )
}

/** 두 개 이상 선택지를 고르는 알약 탭 (기사 보기 / 쟁점 비교) */
@Composable
internal fun SegmentedPills(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEachIndexed { i, label ->
            val mod = Modifier.weight(1f)
            if (i == selected) FilledPill(label, { onSelect(i) }, mod) else OutlinedPill(label, { onSelect(i) }, mod)
        }
    }
}
