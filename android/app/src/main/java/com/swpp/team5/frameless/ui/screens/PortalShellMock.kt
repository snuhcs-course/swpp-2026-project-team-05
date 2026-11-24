package com.swpp.team5.frameless.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swpp.team5.frameless.data.AppSession
import com.swpp.team5.frameless.data.ArticleSummary
import com.swpp.team5.frameless.data.PublisherItem
import com.swpp.team5.frameless.data.StorySummary
import com.swpp.team5.frameless.ui.theme.Fl

internal enum class PortalTab(val label: String) {
    HOME("홈"),
    NEWS("뉴스"),
    SAVED("저장"),
    MY("MY")
}

internal enum class ProfilePage { OVERVIEW, SOURCES, ANALYSIS }

private val PortalAccent: Color @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.primary
private val PortalPage: Color @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.background
private val PortalSurface: Color @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.surface
private val PortalLine: Color @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.outline
private val PortalSoft: Color @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.surfaceVariant

@Composable
internal fun PortalBottomBar(selected: PortalTab, onSelect: (PortalTab) -> Unit) {
    Column(Modifier.fillMaxWidth().background(PortalSurface)) {
        HorizontalDivider(color = PortalLine)
        Row(
            modifier = Modifier.fillMaxWidth().height(62.dp).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PortalTab.entries.forEach { tab ->
                val active = selected == tab
                Column(
                    modifier = Modifier.weight(1f).clickable { onSelect(tab) }
                        .padding(top = 5.dp, bottom = 4.dp)
                        .semantics {
                        role = Role.Tab
                        this.selected = active
                        contentDescription = "${tab.label} 탭"
                    },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier.size(width = 36.dp, height = 27.dp)
                            .background(if (active) PortalSoft else Color.Transparent, RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        PortalTabIcon(tab, active)
                    }
                    Spacer(Modifier.height(1.dp))
                    Text(
                        tab.label,
                        fontSize = 10.sp,
                        lineHeight = 11.sp,
                        color = if (active) Fl.Ink else Color(0xFF858A95),
                        fontWeight = if (active) FontWeight.Bold else FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
private fun PortalTabIcon(tab: PortalTab, selected: Boolean) {
    val color = if (selected) PortalAccent else Color(0xFF858A95)
    Canvas(Modifier.size(20.dp)) {
        val stroke = Stroke(if (selected) 2.dp.toPx() else 1.7.dp.toPx(), cap = StrokeCap.Round)
        val w = size.width
        when (tab) {
            PortalTab.HOME -> {
                val roof = Path().apply {
                    moveTo(w * 0.12f, w * 0.48f)
                    lineTo(w * 0.5f, w * 0.16f)
                    lineTo(w * 0.88f, w * 0.48f)
                }
                drawPath(roof, color, style = stroke)
                drawRoundRect(
                    color,
                    topLeft = Offset(w * 0.22f, w * 0.43f),
                    size = Size(w * 0.56f, w * 0.43f),
                    cornerRadius = CornerRadius(w * 0.08f),
                    style = stroke
                )
                drawLine(color, Offset(w * 0.48f, w * 0.86f), Offset(w * 0.48f, w * 0.65f), stroke.width)
            }
            PortalTab.NEWS -> {
                drawRoundRect(
                    color,
                    topLeft = Offset(w * 0.14f, w * 0.12f),
                    size = Size(w * 0.72f, w * 0.76f),
                    cornerRadius = CornerRadius(w * 0.08f),
                    style = stroke
                )
                drawLine(color, Offset(w * 0.27f, w * 0.34f), Offset(w * 0.73f, w * 0.34f), stroke.width)
                drawLine(color, Offset(w * 0.27f, w * 0.52f), Offset(w * 0.73f, w * 0.52f), stroke.width)
                drawLine(color, Offset(w * 0.27f, w * 0.70f), Offset(w * 0.60f, w * 0.70f), stroke.width)
            }
            PortalTab.SAVED -> {
                val bookmark = Path().apply {
                    moveTo(w * 0.25f, w * 0.14f)
                    lineTo(w * 0.75f, w * 0.14f)
                    lineTo(w * 0.75f, w * 0.88f)
                    lineTo(w * 0.5f, w * 0.70f)
                    lineTo(w * 0.25f, w * 0.88f)
                    close()
                }
                drawPath(bookmark, color, style = stroke)
            }
            PortalTab.MY -> {
                drawCircle(color, radius = w * 0.17f, center = Offset(w * 0.5f, w * 0.33f), style = stroke)
                drawArc(
                    color,
                    startAngle = 202f,
                    sweepAngle = 136f,
                    useCenter = false,
                    topLeft = Offset(w * 0.18f, w * 0.48f),
                    size = Size(w * 0.64f, w * 0.48f),
                    style = stroke
                )
            }
        }
    }
}

private enum class BriefingIcon { WEATHER, MARKET, CURRENCY }

private data class BriefingMetric(
    val label: String,
    val value: String,
    val detail: String,
    val context: String,
    val accent: Color,
    val icon: BriefingIcon
)

@Composable
internal fun PortalHomeScreen(
    stories: List<StorySummary>,
    followedPublisherCount: Int,
    isLoading: Boolean,
    error: String?,
    listState: LazyListState,
    onSearch: () -> Unit,
    onArticle: (String) -> Unit,
    onProfile: () -> Unit,
    onSubscriptions: () -> Unit,
    onRetry: () -> Unit
) {
    val articles = stories.map(StorySummary::representativeArticle)
    var trendsExpanded by rememberSaveable { mutableStateOf(false) }
    val trendCount = if (trendsExpanded) 5 else 2
    val trending = articles.take(trendCount)
    val latest = stories.drop(trendCount).ifEmpty { stories }.take(7)
    val briefing = listOf(
        BriefingMetric("서울", "8°", "맑음", "최고 12° · 최저 2°", Color(0xFF4E76B7), BriefingIcon.WEATHER),
        BriefingMetric("KOSPI", "2,591.05", "+0.32%", "최근 종가", Color(0xFFB84D51), BriefingIcon.MARKET),
        BriefingMetric("원·달러", "1,397.40", "+0.12%", "최근 고시", Color(0xFF3D6FA8), BriefingIcon.CURRENCY),
        BriefingMetric("NASDAQ", "19,003.65", "-0.60%", "최근 종가", Color(0xFF47775E), BriefingIcon.MARKET),
        BriefingMetric("S&P 500", "5,948.71", "-0.29%", "최근 종가", Color(0xFF5D5A91), BriefingIcon.MARKET)
    )

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().background(PortalPage),
        contentPadding = PaddingValues(bottom = 18.dp)
    ) {
        item {
            Column(
                Modifier.fillMaxWidth().background(PortalSurface)
                    .padding(start = 18.dp, end = 18.dp, top = 9.dp, bottom = 11.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(25.dp).background(
                            Brush.linearGradient(listOf(Color(0xFF7194E4), Color(0xFF9B79CC), Color(0xFFD77DA7))),
                            RoundedCornerShape(8.dp)
                        ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("F", color = Color.White, fontFamily = Fl.Serif, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "FrameLESS",
                        fontFamily = Fl.Serif,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = Fl.Ink,
                        letterSpacing = (-0.3).sp
                    )
                    Spacer(Modifier.weight(1f))
                    Box(
                        Modifier.size(30.dp)
                            .clickable(onClick = onProfile)
                            .semantics { role = Role.Button; contentDescription = "내 정보 열기" },
                        contentAlignment = Alignment.Center
                    ) {
                        ProfileGlyph(Fl.Ink)
                    }
                }
                Spacer(Modifier.height(10.dp))
                Box(
                    Modifier.fillMaxWidth()
                        .background(
                            Brush.horizontalGradient(
                                listOf(Color(0xFF6F99E5), Color(0xFFA07BCB), Color(0xFFD679A5))
                            ),
                            RoundedCornerShape(22.dp)
                        )
                        .padding(1.dp)
                ) {
                    Row(
                        Modifier.fillMaxWidth().height(44.dp)
                            .background(PortalSurface, RoundedCornerShape(21.dp))
                            .clickable(onClick = onSearch)
                            .padding(horizontal = 14.dp)
                            .semantics { role = Role.Button; contentDescription = "기사 검색 열기" },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "뉴스를 검색해 보세요",
                            color = Color(0xFF757A85),
                            fontSize = 13.sp,
                            modifier = Modifier.weight(1f)
                        )
                        SearchGlyph()
                    }
                }
            }
        }

        item {
            Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("실시간 트렌드", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Fl.Ink)
                    Spacer(Modifier.width(6.dp))
                    Box(
                        Modifier.size(15.dp).border(1.dp, Color(0xFFADB1BA), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("i", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFF8C919B))
                    }
                    Spacer(Modifier.weight(1f))
                    Row(
                        Modifier.clickable { trendsExpanded = !trendsExpanded }
                            .padding(start = 12.dp, top = 6.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(if (trendsExpanded) "접기" else "펼치기", fontSize = 11.sp, color = Fl.Muted)
                        Text(if (trendsExpanded) " ︿" else " ﹀", fontSize = 12.sp, color = Fl.Muted)
                    }
                }
                if (isLoading && trending.isEmpty()) {
                    Text(
                        "새 기사를 불러오고 있어요",
                        color = Fl.Muted,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(vertical = 18.dp)
                    )
                } else if (trending.isEmpty()) {
                    Text(
                        error ?: "표시할 주요 뉴스가 없어요",
                        color = Fl.Muted,
                        fontSize = 13.sp,
                        modifier = Modifier.clickable(onClick = onRetry).padding(vertical = 18.dp)
                    )
                } else {
                    trending.forEachIndexed { index, article ->
                        CompactTrendRow(index + 1, article, index % 2 == 0) { onArticle(article.id) }
                    }
                }
            }
        }
        item {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 11.dp)
                    .background(PortalSoft, RoundedCornerShape(13.dp))
                    .clickable(onClick = onSubscriptions)
                    .padding(horizontal = 13.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        if (followedPublisherCount > 0) "구독한 언론사 소식" else "자주 읽는 언론사를 구독해 보세요",
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Fl.Ink
                    )
                    Text(
                        if (followedPublisherCount > 0) "${followedPublisherCount}곳의 기사를 홈에 먼저 보여드려요"
                        else "구독하면 해당 언론사의 최신 기사를 홈에서 볼 수 있어요",
                        fontSize = 10.5.sp,
                        color = Fl.Muted,
                        modifier = Modifier.padding(top = 3.dp)
                    )
                }
                Text("›", fontSize = 22.sp, color = Fl.Muted)
            }
        }
        item {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                contentPadding = PaddingValues(horizontal = 18.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(briefing, key = { it.label }) { metric -> BriefingCard(metric) }
            }
        }
        item {
            Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp)) {
                articles.drop(2).take(2).forEachIndexed { index, article ->
                    BreakingNewsRow(if (index == 0) "속보" else "이슈", article) { onArticle(article.id) }
                    if (index == 0) Spacer(Modifier.height(10.dp))
                }
            }
        }
        item { Box(Modifier.fillMaxWidth().height(7.dp).background(Fl.Bg)) }
        item { PortalSectionHeader("주요 뉴스", "최신 기사") }
        if (latest.isEmpty()) {
            item { EmptyPortalSection("표시할 최신 기사가 없어요") }
        } else {
            items(latest, key = StorySummary::storyKey) { story ->
                LatestNewsRow(story.representativeArticle, story.publisherCount) {
                    onArticle(story.representativeArticle.id)
                }
            }
        }
    }
}

@Composable
private fun PortalSectionHeader(title: String, caption: String) {
    Row(
        Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 8.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Fl.Ink)
        Spacer(Modifier.width(7.dp))
        Text(caption, fontSize = 10.5.sp, color = Fl.Muted, modifier = Modifier.padding(bottom = 1.dp))
    }
}

@Composable
private fun CompactTrendRow(
    rank: Int,
    article: ArticleSummary,
    rising: Boolean,
    onClick: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().height(32.dp)
            .clickable(onClick = onClick)
            .semantics { role = Role.Button; contentDescription = "실시간 트렌드 $rank, ${article.title}" },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(rank.toString(), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Fl.Ink, modifier = Modifier.width(24.dp))
        Text(
            article.title,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = Fl.Ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Text(if (rising) "▲" else "▼", fontSize = 9.sp, color = if (rising) Color(0xFFE35A45) else Color(0xFF4C83D1))
    }
}

@Composable
private fun BreakingNewsRow(label: String, article: ArticleSummary, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            color = if (label == "속보") Color(0xFFE75A3B) else Color(0xFF4E83C9),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(36.dp)
        )
        Text(
            article.title,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontSize = 13.5.sp,
            color = Fl.Ink,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun BriefingCard(metric: BriefingMetric) {
    Column(
        Modifier.width(126.dp).height(84.dp)
            .background(PortalSurface, RoundedCornerShape(11.dp))
            .border(1.dp, PortalLine, RoundedCornerShape(11.dp))
            .padding(horizontal = 11.dp, vertical = 9.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BriefingGlyph(metric.icon, metric.accent)
            Spacer(Modifier.width(7.dp))
            Text(metric.label, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Fl.Ink2)
        }
        Text(
            metric.value,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = Fl.Ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(metric.detail, fontSize = 9.5.sp, fontWeight = FontWeight.Bold, color = metric.accent)
            Spacer(Modifier.weight(1f))
            Text(metric.context, fontSize = 8.sp, color = Fl.Muted)
        }
    }
}

@Composable
private fun BriefingGlyph(icon: BriefingIcon, color: Color) {
    Canvas(Modifier.size(18.dp)) {
        val w = size.width
        val stroke = Stroke(1.6.dp.toPx(), cap = StrokeCap.Round)
        when (icon) {
            BriefingIcon.WEATHER -> {
                drawCircle(color, w * 0.20f, Offset(w * 0.50f, w * 0.50f), style = stroke)
                repeat(8) { i ->
                    val a = Math.toRadians((i * 45).toDouble())
                    val cosine = kotlin.math.cos(a).toFloat()
                    val sine = kotlin.math.sin(a).toFloat()
                    drawLine(
                        color,
                        Offset(w * 0.5f + cosine * w * 0.31f, w * 0.5f + sine * w * 0.31f),
                        Offset(w * 0.5f + cosine * w * 0.43f, w * 0.5f + sine * w * 0.43f),
                        stroke.width,
                        StrokeCap.Round
                    )
                }
            }
            BriefingIcon.MARKET -> {
                val path = Path().apply {
                    moveTo(w * 0.10f, w * 0.70f)
                    lineTo(w * 0.35f, w * 0.47f)
                    lineTo(w * 0.55f, w * 0.58f)
                    lineTo(w * 0.87f, w * 0.25f)
                }
                drawPath(path, color, style = stroke)
            }
            BriefingIcon.CURRENCY -> {
                drawCircle(color, w * 0.42f, Offset(w * 0.50f, w * 0.50f), style = stroke)
                drawLine(color, Offset(w * 0.27f, w * 0.39f), Offset(w * 0.73f, w * 0.39f), stroke.width)
                drawLine(color, Offset(w * 0.32f, w * 0.55f), Offset(w * 0.68f, w * 0.55f), stroke.width)
            }
        }
    }
}

@Composable
private fun LatestNewsRow(article: ArticleSummary, publisherCount: Int, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 18.dp)
            .clickable(onClick = onClick)
            .padding(vertical = 13.dp)
    ) {
        val palette = categoryPalette(article.category)
        Text(
            article.title,
            fontSize = 14.5.sp,
            lineHeight = 20.sp,
            fontWeight = FontWeight.Medium,
            color = Fl.Ink,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
            Text(article.category.label, fontSize = 10.sp, color = palette.ink, fontWeight = FontWeight.Bold)
            Text(" · ${article.publisher.name} · ${feedRelativeTime(article.publishedAt)}", fontSize = 10.sp, color = Fl.Muted)
            if (publisherCount > 1) {
                Text(" · ${publisherCount}개 언론사", fontSize = 10.sp, color = Fl.Accent, fontWeight = FontWeight.Bold)
            }
        }
    }
    HorizontalDivider(color = PortalLine, modifier = Modifier.padding(horizontal = 18.dp))
}

@Composable
private fun EmptyPortalSection(message: String) {
    Box(
        Modifier.fillMaxWidth().padding(horizontal = 18.dp)
            .background(PortalSurface, RoundedCornerShape(16.dp))
            .border(1.dp, PortalLine, RoundedCornerShape(16.dp))
            .padding(vertical = 28.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(message, color = Fl.Muted, fontSize = 13.sp)
    }
}

@Composable
private fun SearchGlyph() {
    val accent = PortalAccent
    Canvas(Modifier.size(19.dp)) {
        val stroke = 1.8.dp.toPx()
        drawCircle(accent, size.width * 0.31f, Offset(size.width * 0.42f, size.height * 0.42f), style = Stroke(stroke))
        drawLine(
            accent,
            Offset(size.width * 0.65f, size.height * 0.65f),
            Offset(size.width * 0.88f, size.height * 0.88f),
            stroke,
            StrokeCap.Round
        )
    }
}

@Composable
private fun ProfileGlyph(color: Color) {
    Canvas(Modifier.size(19.dp)) {
        drawCircle(color, size.width * 0.18f, Offset(size.width * 0.5f, size.height * 0.35f), style = Stroke(1.7.dp.toPx()))
        drawArc(
            color,
            202f,
            136f,
            false,
            Offset(size.width * 0.17f, size.height * 0.50f),
            Size(size.width * 0.66f, size.height * 0.45f),
            style = Stroke(1.7.dp.toPx(), cap = StrokeCap.Round)
        )
    }
}

@Composable
internal fun PortalProfileScreen(
    session: AppSession?,
    analysisEnabled: Boolean,
    followedCount: Int = 0,
    onLogin: () -> Unit,
    onTutorial: () -> Unit,
    onLogout: () -> Unit,
    onSources: () -> Unit,
    onAnalysis: () -> Unit
) {
    LazyColumn(Modifier.fillMaxSize().background(PortalPage)) {
        item {
            Text(
                "MY",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = Fl.Ink,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp)
            )
        }
        item {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 4.dp)
                    .background(PortalSoft, RoundedCornerShape(16.dp))
                    .padding(horizontal = 15.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier.size(46.dp).background(PortalSurface, CircleShape)
                        .border(1.dp, PortalLine, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        session?.user?.username?.take(1)?.uppercase() ?: "F",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = PortalAccent
                    )
                }
                Column(Modifier.padding(start = 14.dp)) {
                    Text(
                        session?.user?.username ?: "FrameLESS 게스트",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Fl.Ink
                    )
                    Text(
                        session?.user?.email ?: "로그인하면 구독과 저장 목록을 동기화할 수 있어요.",
                        fontSize = 11.5.sp,
                        color = Fl.Muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
        if (session == null) {
            item { ProfileAction("로그인 또는 회원가입", "저장·구독 동기화", onLogin) }
        }
        item { ProfileSectionLabel("뉴스 설정") }
        item {
            ProfileAction(
                "구독한 언론사 관리",
                if (session == null) "로그인 후 설정할 수 있어요" else "${followedCount}개 언론사 구독 중",
                onSources
            )
        }
        item {
            ProfileAction(
                "기본 분석 표시",
                if (analysisEnabled) "기사에서 분석을 바로 표시해요" else "분석을 접은 상태로 열어요",
                onAnalysis
            )
        }
        item { ProfileSectionLabel("앱") }
        item { ProfileAction("튜토리얼 다시 보기", "FrameLESS 읽는 법", onTutorial) }
        item { ProfileStaticRow("오프라인 저장 공간", "상세 관리 준비 중") }
        if (session != null) {
            item { ProfileAction("로그아웃", null, onLogout, destructive = true) }
        }
        item { Spacer(Modifier.height(30.dp)) }
    }
}

@Composable
internal fun PortalFollowedPublishersScreen(
    publishers: List<PublisherItem>,
    isSaving: Boolean,
    error: String?,
    onBack: () -> Unit,
    onSave: (Set<String>) -> Unit
) {
    val initialCodes = publishers.filter(PublisherItem::isFollowed).map(PublisherItem::code).toSet()
    var selectedCodes by remember(publishers) { mutableStateOf(initialCodes) }
    val hasChanges = selectedCodes != initialCodes

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(PortalPage),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        item {
            ProfileDetailHeader(
                title = "구독한 언론사",
                subtitle = "홈에서 먼저 볼 언론사를 선택하세요.",
                onBack = onBack
            )
        }
        if (publishers.isEmpty()) {
            item { EmptyPortalSection("언론사 목록을 불러오는 중이에요") }
        } else {
            items(publishers, key = PublisherItem::code) { publisher ->
                val checked = publisher.code in selectedCodes
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .clickable {
                            selectedCodes = if (checked) selectedCodes - publisher.code else selectedCodes + publisher.code
                        }
                        .padding(horizontal = 18.dp, vertical = 12.dp)
                        .semantics {
                            role = Role.Checkbox
                            selected = checked
                            contentDescription = "${publisher.name} ${if (checked) "구독 중" else "구독 안 함"}"
                        },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier.size(38.dp).background(PortalSoft, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            publisher.name.take(1),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = PortalAccent
                        )
                    }
                    Text(
                        publisher.name,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = Fl.Ink,
                        modifier = Modifier.padding(start = 12.dp).weight(1f)
                    )
                    Box(
                        Modifier.size(24.dp)
                            .background(if (checked) PortalAccent else PortalSurface, CircleShape)
                            .border(1.dp, if (checked) PortalAccent else Color(0xFFC9CED8), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        if (checked) Text("✓", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
                HorizontalDivider(color = PortalLine, modifier = Modifier.padding(start = 68.dp, end = 18.dp))
            }
        }
        if (error != null) {
            item {
                Text(
                    error,
                    color = Fl.Danger,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp)
                )
            }
        }
        item {
            SettingsPrimaryButton(
                label = if (isSaving) "저장 중…" else "변경사항 저장",
                enabled = hasChanges && !isSaving,
                onClick = { onSave(selectedCodes) },
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 20.dp)
            )
        }
    }
}

@Composable
internal fun PortalAnalysisSettingsScreen(
    enabled: Boolean,
    onEnabled: (Boolean) -> Unit,
    onBack: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(PortalPage),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        item {
            ProfileDetailHeader(
                title = "기본 분석 표시",
                subtitle = "기사를 열 때 표현 분석을 어떻게 보여줄지 정합니다.",
                onBack = onBack
            )
        }
        item {
            Row(
                Modifier.fillMaxWidth().clickable { onEnabled(!enabled) }
                    .padding(horizontal = 18.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("기사 분석 표시", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Fl.Ink)
                    Text(
                        if (enabled) "분석이 있는 기사는 하이라이트와 주석을 표시해요."
                        else "본문만 먼저 보여주고 필요할 때 분석을 켤 수 있어요.",
                        fontSize = 11.5.sp,
                        lineHeight = 17.sp,
                        color = Fl.Muted,
                        modifier = Modifier.padding(top = 4.dp, end = 16.dp)
                    )
                }
                Switch(
                    checked = enabled,
                    onCheckedChange = onEnabled,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = PortalAccent,
                        uncheckedThumbColor = Color.White,
                        uncheckedTrackColor = Color(0xFFD6DAE2),
                        uncheckedBorderColor = Color(0xFFD6DAE2)
                    )
                )
            }
            HorizontalDivider(color = PortalLine, modifier = Modifier.padding(horizontal = 18.dp))
        }
        item {
            Column(
                Modifier.fillMaxWidth().padding(18.dp)
                    .background(PortalSoft, RoundedCornerShape(14.dp))
                    .padding(15.dp)
            ) {
                Text("언제든 바꿀 수 있어요", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Fl.Ink)
                Text(
                    "이 설정은 다음 기사부터 적용되며, 기사 안의 분석 토글로도 해당 기사에서 바로 변경할 수 있습니다.",
                    fontSize = 11.5.sp,
                    lineHeight = 18.sp,
                    color = Fl.Muted,
                    modifier = Modifier.padding(top = 5.dp)
                )
            }
        }
    }
}

@Composable
private fun ProfileDetailHeader(title: String, subtitle: String, onBack: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(40.dp).clickable(onClick = onBack)
                    .semantics { role = Role.Button; contentDescription = "MY로 돌아가기" },
                contentAlignment = Alignment.Center
            ) {
                Text("‹", fontSize = 32.sp, lineHeight = 32.sp, color = Fl.Ink)
            }
            Text(title, fontSize = 19.sp, fontWeight = FontWeight.Bold, color = Fl.Ink)
        }
        Text(
            subtitle,
            fontSize = 12.sp,
            lineHeight = 18.sp,
            color = Fl.Muted,
            modifier = Modifier.padding(start = 6.dp, top = 5.dp, bottom = 8.dp)
        )
    }
}

@Composable
private fun SettingsPrimaryButton(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxWidth().height(48.dp)
            .background(if (enabled) Fl.Accent else Color(0xFFE4E6EB), RoundedCornerShape(13.dp))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            fontSize = 13.5.sp,
            fontWeight = FontWeight.Bold,
            color = if (enabled) Fl.OnAccent else Fl.Muted
        )
    }
}

@Composable
private fun ProfileSectionLabel(text: String) {
    Text(
        text,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        color = Fl.Muted,
        letterSpacing = 0.6.sp,
        modifier = Modifier.fillMaxWidth().background(PortalSoft)
            .padding(horizontal = 18.dp, vertical = 10.dp)
    )
}

@Composable
private fun ProfileAction(
    title: String,
    detail: String?,
    onClick: () -> Unit,
    destructive: Boolean = false
) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, color = if (destructive) Fl.Danger else Fl.Ink)
            detail?.let { Text(it, fontSize = 11.sp, color = Fl.Muted, modifier = Modifier.padding(top = 4.dp)) }
        }
        if (!destructive) Text("›", fontSize = 22.sp, color = Color(0xFF999999))
    }
    HorizontalDivider(color = PortalLine, modifier = Modifier.padding(start = 18.dp))
}

@Composable
private fun ProfileStaticRow(title: String, detail: String) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, fontSize = 15.sp, color = Fl.Ink, modifier = Modifier.weight(1f))
        Text(detail, fontSize = 11.sp, color = Fl.Muted)
    }
    HorizontalDivider(color = PortalLine, modifier = Modifier.padding(start = 18.dp))
}
