package com.swpp.team5.frameless.ui.screens

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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material3.Icon
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swpp.team5.frameless.R
import com.swpp.team5.frameless.ui.components.PublisherLogo
import com.swpp.team5.frameless.ui.components.BrandRow
import com.swpp.team5.frameless.data.AppSession
import com.swpp.team5.frameless.data.AppearanceMode
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

internal enum class ProfilePage { OVERVIEW, SOURCES, ANALYSIS, APPEARANCE }

private val PortalAccent: Color @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.primary
private val PortalPage: Color @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.background
private val PortalSurface: Color @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.surface
private val PortalLine: Color @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.outline
private val PortalSoft: Color @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.surfaceVariant

@Composable
internal fun PortalBottomBar(selected: PortalTab, onSelect: (PortalTab) -> Unit) {
    Column(Modifier.fillMaxWidth().background(PortalSurface)) {
        HorizontalDivider(color = PortalLine.copy(alpha = 0.65f), thickness = 0.5.dp)
        Row(
            modifier = Modifier.fillMaxWidth().height(60.dp).selectableGroup()
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PortalTab.entries.forEach { tab ->
                val active = selected == tab
                val color = if (active) Fl.Ink else Fl.Muted
                Column(
                    modifier = Modifier.weight(1f).fillMaxHeight()
                        .selectable(selected = active, role = Role.Tab, onClick = { onSelect(tab) })
                        .semantics { contentDescription = "${tab.label} 탭" },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        painter = painterResource(when (tab) {
                            PortalTab.HOME -> R.drawable.ic_portal_home
                            PortalTab.NEWS -> R.drawable.ic_portal_news
                            PortalTab.SAVED -> R.drawable.ic_portal_bookmark
                            PortalTab.MY -> R.drawable.ic_portal_profile
                        }),
                        contentDescription = null,
                        tint = color,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        tab.label,
                        fontSize = 11.sp,
                        lineHeight = 14.sp,
                        color = color,
                        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun PortalHomeScreen(
    stories: List<StorySummary>,
    subscribedPublishers: List<PublisherItem>,
    isLoading: Boolean,
    error: String?,
    listState: LazyListState,
    onSearch: () -> Unit,
    onArticle: (String) -> Unit,
    onProfile: () -> Unit,
    onSubscriptions: () -> Unit,
    onRetry: () -> Unit,
    subscribedArticles: List<ArticleSummary> = emptyList(),
    selectedPublisherCode: String? = null,
    onSelectPublisher: (String) -> Unit = {},
    isSubscribedLoading: Boolean = false,
    subscribedError: String? = null,
    onRetrySubscriptions: () -> Unit = onRetry,
    isSignedIn: Boolean = false,
    trends: com.swpp.team5.frameless.ui.viewmodel.TrendsState = com.swpp.team5.frameless.ui.viewmodel.TrendsState(),
    onRetryTrends: () -> Unit = {},
    isRefreshing: Boolean = false,
    highlights: com.swpp.team5.frameless.ui.viewmodel.HomeHighlightsState = com.swpp.team5.frameless.ui.viewmodel.HomeHighlightsState(),
    isOffline: Boolean = false
) {
    val breaking = highlights.page?.breaking?.takeUnless { isOffline }
    val issue = highlights.page?.issue?.takeUnless { isOffline }
    var trendsExpanded by rememberSaveable { mutableStateOf(false) }
    val trendCount = if (trendsExpanded) 5 else 2
    val trending = trends.page?.items.orEmpty().take(trendCount)
    val latest = stories.take(7)
    PullToRefreshBox(isRefreshing = isRefreshing, onRefresh = onRetry,
        modifier = Modifier.fillMaxSize().testTag("home-feed")) {
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
                    BrandRow(compact = true)
                    Spacer(Modifier.weight(1f))
                    Box(
                        Modifier.size(48.dp)
                            .clickable(onClick = onProfile)
                            .semantics { role = Role.Button; contentDescription = "내 정보 열기" },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(painterResource(R.drawable.ic_portal_profile),
                            contentDescription = null, tint = Fl.Ink, modifier = Modifier.size(22.dp))
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

        if (!isOffline) item {
            Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("실시간 트렌드", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Fl.Ink)
                    Spacer(Modifier.weight(1f))
                    if (trends.page?.items.orEmpty().size > 2) {
                    Row(
                        Modifier.heightIn(min = 48.dp).clickable { trendsExpanded = !trendsExpanded }
                            .semantics {
                                role = Role.Button
                                contentDescription = if (trendsExpanded) "실시간 트렌드 접기" else "실시간 트렌드 펼치기"
                                stateDescription = if (trendsExpanded) "펼쳐짐" else "접힘"
                            }
                            .padding(start = 12.dp, top = 6.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(if (trendsExpanded) "접기" else "펼치기", fontSize = 11.sp, color = Fl.Muted)
                        Icon(painterResource(if (trendsExpanded) R.drawable.ic_portal_chevron_up
                            else R.drawable.ic_portal_chevron_down), contentDescription = null,
                            tint = Fl.Muted, modifier = Modifier.padding(start = 5.dp).size(14.dp))
                    }
                    }
                }
                if (trends.loading && trending.isEmpty()) {
                    Text(
                        "트렌드를 불러오고 있어요",
                        color = Fl.Muted,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(vertical = 18.dp)
                    )
                } else if (trending.isEmpty()) {
                    Text(
                        trends.error ?: "표시할 뉴스가 없어요",
                        color = Fl.Muted,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                    if (trends.error != null) Text("다시 시도", color = Fl.Accent, fontSize = 12.sp,
                        modifier = Modifier.clickable(onClick = onRetryTrends)
                            .semantics { role = Role.Button; contentDescription = "트렌드 다시 시도" }
                            .padding(vertical = 10.dp))
                } else {
                    trending.forEach { item ->
                        CompactTrendRow(item) { onArticle(item.story.representativeArticle.id) }
                    }
                }
            }
        }
        item { HomeBriefingStrip() }
        if (breaking != null || issue != null) item {
            Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp)) {
                breaking?.let { BreakingNewsRow("속보", it.article) { onArticle(it.article.id) } }
                if (breaking != null && issue != null) Spacer(Modifier.height(10.dp))
                issue?.let {
                    val article = it.story.representativeArticle
                    BreakingNewsRow("이슈", article) { onArticle(article.id) }
                }
            }
        }
        item { Box(Modifier.fillMaxWidth().height(7.dp).background(Fl.Bg)) }
        item { PortalSectionHeader(if (isOffline) "저장한 기사" else "주요 뉴스") }
        if (error != null && latest.isNotEmpty()) {
            item { Text(error, color = Fl.Muted, fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp)) }
        }
        if (latest.isEmpty()) {
            item { EmptyPortalSection(when {
                isLoading -> "뉴스를 불러오는 중이에요"
                error != null -> error
                else -> "표시할 최신 기사가 없어요"
            }) }
        } else {
            items(latest, key = { "major:${it.storyKey}" }) { story ->
                LatestNewsRow(story.representativeArticle, story.publisherCount) {
                    onArticle(story.representativeArticle.id)
                }
            }
        }
        item {
            Spacer(Modifier.fillMaxWidth().height(8.dp).background(PortalSoft))
            Row(
                Modifier.fillMaxWidth().padding(start = 18.dp, end = 6.dp, top = 12.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("구독한 언론사", fontSize = 16.sp, fontWeight = FontWeight.Bold,
                    color = Fl.Ink, modifier = Modifier.weight(1f))
                androidx.compose.material3.TextButton(onClick = onSubscriptions) {
                    Text("구독 관리", fontSize = 11.5.sp, color = Fl.Muted)
                    Icon(painterResource(R.drawable.ic_portal_chevron_right), contentDescription = null,
                        tint = Fl.Muted, modifier = Modifier.size(15.dp))
                }
            }
        }
        if (subscribedPublishers.isNotEmpty()) {
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth().selectableGroup()
                ) {
                    items(subscribedPublishers, key = PublisherItem::code) { publisher ->
                        val active = publisher.code == (selectedPublisherCode ?: subscribedPublishers.first().code)
                        Column(
                            modifier = Modifier.width(68.dp)
                                .selectable(selected = active, role = Role.Tab,
                                    onClick = { onSelectPublisher(publisher.code) })
                                .semantics { contentDescription = "${publisher.name} 구독 뉴스" }
                                .padding(vertical = 3.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier.size(54.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                PublisherLogo(publisher, Modifier.size(44.dp))
                            }
                            Text(publisher.name, fontSize = 11.sp,
                                fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                                color = if (active) Fl.Ink else Fl.Muted,
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 7.dp))
                        }
                    }
                }
            }
        }
        when {
            subscribedPublishers.isEmpty() -> item {
                Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 18.dp)) {
                    Text(
                        if (isSignedIn) "자주 읽는 언론사를 구독해 보세요"
                        else "관심 언론사의 소식을 한곳에서",
                        fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Fl.Ink
                    )
                    Text(
                        if (isSignedIn) "구독한 언론사의 최신 기사가 여기에 모여요."
                        else "로그인하고 언론사를 구독하면 최신 기사를 모아 볼 수 있어요.",
                        fontSize = 12.sp, lineHeight = 18.sp, color = Fl.Muted,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                    androidx.compose.material3.TextButton(onClick = onSubscriptions) {
                        Text("언론사 선택하기", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            isSubscribedLoading && subscribedArticles.isEmpty() -> item {
                Box(Modifier.fillMaxWidth().height(90.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(22.dp), color = Fl.Accent, strokeWidth = 2.dp)
                }
            }
            subscribedError != null && subscribedArticles.isEmpty() -> item {
                Column(Modifier.padding(horizontal = 18.dp, vertical = 12.dp)) {
                    Text("구독 뉴스를 불러오지 못했어요", fontSize = 13.sp, color = Fl.Muted)
                    androidx.compose.material3.TextButton(onClick = onRetrySubscriptions) { Text("다시 시도") }
                }
            }
            subscribedArticles.isEmpty() -> item { EmptyPortalSection("구독한 언론사의 새 기사를 기다리고 있어요") }
            else -> items(subscribedArticles.take(7), key = { "subscribed:${it.id}" }) { article ->
                LatestNewsRow(article, 1) {
                    onArticle(article.id)
                }
            }
        }
    }
    }
}

@Composable
private fun PortalSectionHeader(title: String) {
    Row(
        Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 8.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Fl.Ink)
    }
}

@Composable
private fun CompactTrendRow(
    item: com.swpp.team5.frameless.data.TrendItem,
    onClick: () -> Unit
) {
    val article = item.story.representativeArticle
    val label = if (item.kind == "LATEST") "최신" else "${item.rank}위"
    Row(
        Modifier.fillMaxWidth().height(40.dp)
            .clickable(onClick = onClick)
            .semantics { role = Role.Button; contentDescription = "실시간 트렌드 $label, ${article.title}" },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(if (item.kind == "LATEST") "최신" else item.rank.toString(),
            fontSize = if (item.kind == "LATEST") 10.sp else 13.sp,
            fontWeight = FontWeight.Bold, color = if (item.kind == "LATEST") Fl.Muted else Fl.Ink,
            modifier = Modifier.width(30.dp))
        Text(
            article.title,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = Fl.Ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        val previous = item.previousRank
        if (item.movement == "NEW") Text("NEW", color = Fl.Muted, fontSize = 9.sp,
            modifier = Modifier.padding(start = 8.dp))
        else if (item.movement in setOf("UP", "DOWN") && previous != null && item.rank != null) {
            Icon(painterResource(if (item.movement == "UP") R.drawable.ic_portal_chevron_up else R.drawable.ic_portal_chevron_down),
                contentDescription = if (item.movement == "UP") "순위 상승" else "순위 하락",
                tint = Fl.Muted, modifier = Modifier.padding(start = 7.dp).size(13.dp))
            Text(kotlin.math.abs(previous - item.rank).toString(), color = Fl.Muted, fontSize = 10.sp)
        }
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
private fun LatestNewsRow(article: ArticleSummary, publisherCount: Int, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 18.dp)
            .clickable(onClick = onClick)
            .padding(vertical = 13.dp)
    ) {
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
            Text(article.category.label, fontSize = 10.sp, color = Fl.Muted, fontWeight = FontWeight.Bold)
            Text(" · ${article.publisher.name}", fontSize = 10.sp, color = Fl.Muted)
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
    Icon(painterResource(R.drawable.ic_portal_search), contentDescription = null,
        tint = Fl.Ink, modifier = Modifier.size(22.dp))
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
    onAnalysis: () -> Unit,
    appearanceMode: AppearanceMode,
    onAppearance: () -> Unit
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
        item {
            ProfileAction(
                "화면 설정",
                when (appearanceMode) {
                    AppearanceMode.SYSTEM -> "기기 설정 사용"
                    AppearanceMode.LIGHT -> "라이트 모드"
                    AppearanceMode.DARK -> "다크 모드"
                },
                onAppearance
            )
        }
        item { ProfileAction("튜토리얼 다시 보기", "FrameLESS 읽는 법", onTutorial) }
        if (session != null) {
            item { ProfileAction("로그아웃", null, onLogout, destructive = true) }
        }
        item { Spacer(Modifier.height(30.dp)) }
    }
}

@Composable
internal fun PortalAppearanceSettingsScreen(
    mode: AppearanceMode,
    onMode: (AppearanceMode) -> Unit,
    onBack: () -> Unit
) {
    val options = listOf(
        AppearanceMode.SYSTEM to ("기기 설정 사용" to "휴대폰의 라이트·다크 모드 설정을 따라요."),
        AppearanceMode.LIGHT to ("라이트 모드" to "항상 밝은 화면으로 표시해요."),
        AppearanceMode.DARK to ("다크 모드" to "항상 차분한 어두운 화면으로 표시해요.")
    )
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(PortalPage),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        item {
            ProfileDetailHeader(
                title = "화면 설정",
                subtitle = "읽기 환경에 맞는 화면 모드를 선택하세요.",
                onBack = onBack
            )
        }
        items(options, key = { it.first.name }) { (option, copy) ->
            val selected = mode == option
            Row(
                modifier = Modifier.fillMaxWidth()
                    .clickable { onMode(option) }
                    .padding(horizontal = 18.dp, vertical = 15.dp)
                    .semantics {
                        role = Role.RadioButton
                        this.selected = selected
                        contentDescription = "${copy.first}, ${if (selected) "선택됨" else "선택 안 됨"}"
                    },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(copy.first, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Fl.Ink)
                    Text(
                        copy.second,
                        fontSize = 11.5.sp,
                        lineHeight = 17.sp,
                        color = Fl.Muted,
                        modifier = Modifier.padding(top = 4.dp, end = 16.dp)
                    )
                }
                Box(
                    Modifier.size(22.dp)
                        .border(1.5.dp, if (selected) PortalAccent else Fl.Muted, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    if (selected) Box(Modifier.size(12.dp).background(PortalAccent, CircleShape))
                }
            }
            HorizontalDivider(color = PortalLine, modifier = Modifier.padding(start = 18.dp))
        }
    }
}

@Composable
internal fun PortalFollowedPublishersScreen(
    publishers: List<PublisherItem>,
    onBack: () -> Unit,
    onToggle: (PublisherItem, Boolean) -> Unit,
    isGuest: Boolean = false,
    pendingPublisherCodes: Set<String> = emptySet(),
    errors: Map<String, String> = emptyMap()
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(PortalPage).testTag("subscription-management"),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        item {
            ProfileDetailHeader(
                title = "구독 관리",
                subtitle = if (isGuest) "로그인하면 구독 설정을 저장할 수 있어요."
                    else "관심 언론사를 구독하거나 해지할 수 있어요.",
                onBack = onBack
            )
        }
        if (publishers.isEmpty()) {
            item { EmptyPortalSection("표시할 언론사가 없어요") }
        } else {
            items(publishers, key = PublisherItem::code) { publisher ->
                val pending = publisher.code in pendingPublisherCodes
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .toggleable(
                            value = publisher.isFollowed,
                            enabled = !pending,
                            role = Role.Switch,
                            onValueChange = { onToggle(publisher, it) }
                        )
                        .testTag("subscription-toggle-${publisher.code}")
                        .padding(horizontal = 20.dp, vertical = 15.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PublisherLogo(publisher, Modifier.size(36.dp))
                    Column(Modifier.padding(start = 14.dp).weight(1f)) {
                        Text(publisher.name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Fl.Ink)
                        Text(
                            if (publisher.isFollowed) "구독 중" else "구독 안 함",
                            fontSize = 11.5.sp,
                            color = Fl.Muted,
                            modifier = Modifier.padding(top = 3.dp)
                        )
                    }
                    Box(Modifier.width(28.dp), contentAlignment = Alignment.Center) {
                        if (pending) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 1.5.dp,
                                color = PortalAccent
                            )
                        }
                    }
                    Switch(
                        checked = publisher.isFollowed,
                        onCheckedChange = null,
                        enabled = !pending,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Fl.OnAccent,
                            checkedTrackColor = PortalAccent
                        )
                    )
                }
                errors[publisher.code]?.let { message ->
                    Text(message, color = Fl.Danger, fontSize = 12.sp,
                        modifier = Modifier.padding(start = 70.dp, end = 20.dp, bottom = 12.dp)
                            .testTag("subscription-error-${publisher.code}"))
                }
                HorizontalDivider(
                    color = PortalLine.copy(alpha = 0.6f),
                    modifier = Modifier.padding(start = 76.dp, end = 20.dp)
                )
            }
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
                        checkedThumbColor = Fl.OnAccent,
                        checkedTrackColor = PortalAccent,
                        uncheckedThumbColor = Fl.Muted,
                        uncheckedTrackColor = PortalSoft,
                        uncheckedBorderColor = PortalLine
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
            androidx.compose.material3.IconButton(onClick = onBack) {
                Icon(painterResource(R.drawable.ic_portal_arrow_back),
                    contentDescription = "MY로 돌아가기", tint = Fl.Ink)
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
        if (!destructive) Icon(painterResource(R.drawable.ic_portal_chevron_right),
            contentDescription = null, tint = Fl.Muted, modifier = Modifier.size(20.dp))
    }
    HorizontalDivider(color = PortalLine, modifier = Modifier.padding(start = 18.dp))
}
