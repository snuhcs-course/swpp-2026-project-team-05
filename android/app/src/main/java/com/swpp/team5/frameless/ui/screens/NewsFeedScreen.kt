package com.swpp.team5.frameless.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swpp.team5.frameless.data.AppSession
import com.swpp.team5.frameless.data.ArticleSummary
import com.swpp.team5.frameless.data.NewsCategory
import com.swpp.team5.frameless.data.PublisherItem
import com.swpp.team5.frameless.data.StoredAnalysisState
import com.swpp.team5.frameless.ui.theme.Caption
import com.swpp.team5.frameless.ui.theme.Fl
import com.swpp.team5.frameless.ui.theme.OutlinedPill
import com.swpp.team5.frameless.ui.viewmodel.NewsFeedState
import java.time.Duration
import java.time.OffsetDateTime

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun NewsFeedScreen(
    state: NewsFeedState,
    session: AppSession,
    listState: LazyListState,
    onCategory: (NewsCategory) -> Unit,
    onPublishers: (Set<String>) -> Unit,
    onRefresh: () -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    onArticle: (String) -> Unit,
    onLogout: () -> Unit
) {
    var showPublishers by remember { mutableStateOf(false) }
    var showAccount by remember { mutableStateOf(false) }

    LaunchedEffect(state.category, state.selectedPublishers) {
        listState.scrollToItem(0)
    }

    LaunchedEffect(listState.firstVisibleItemIndex, state.items.size, state.nextCursor) {
        if (state.items.isNotEmpty() && listState.firstVisibleItemIndex >= state.items.lastIndex - 3) {
            onLoadMore()
        }
    }

    Column(Modifier.fillMaxSize().background(Fl.Card)) {
        Column(Modifier.fillMaxWidth().padding(top = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.padding(start = 18.dp)) { BrandRow() }
                Spacer(Modifier.weight(1f))
                Box(
                    Modifier.padding(end = 18.dp).size(36.dp)
                        .background(Fl.Inset, CircleShape)
                        .border(1.dp, Fl.Line, CircleShape)
                        .clickable { showAccount = true }
                        .semantics {
                            role = Role.Button
                            contentDescription = "계정 메뉴"
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(session.user.username.take(1).uppercase(), color = Fl.Accent,
                        fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    NewsCategory.entries.forEach { category ->
                        CategoryTab(
                            label = category.label,
                            selected = state.category == category,
                            onClick = { onCategory(category) }
                        )
                    }
                }
                Spacer(Modifier.weight(1f))
                Text(
                    if (state.selectedPublishers.isEmpty()) "언론사 · 전체  ⌄"
                    else "언론사 · ${state.selectedPublishers.size}곳  ⌄",
                    color = Fl.Ink2,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.clickable { showPublishers = true }
                        .padding(horizontal = 4.dp, vertical = 12.dp)
                        .semantics { role = Role.Button }
                )
            }
            if (state.selectedPublishers.isNotEmpty()) {
                val names = state.publishers.filter { it.code in state.selectedPublishers }
                    .joinToString(" · ") { it.name }
                Caption(names, modifier = Modifier.padding(start = 18.dp, end = 18.dp, bottom = 9.dp))
            }
        }
        HorizontalDivider(color = Fl.Line)
        when {
            state.isLoading && state.items.isEmpty() -> LoadingFeed()
            state.error != null && state.items.isEmpty() -> FeedMessage(
                title = "기사를 불러오지 못했어요",
                detail = state.error,
                action = "다시 시도",
                onAction = onRetry
            )
            state.items.isEmpty() -> FeedMessage(
                title = "조건에 맞는 기사가 없어요",
                detail = "다른 분야나 언론사를 선택해 보세요.",
                action = if (state.selectedPublishers.isNotEmpty()) "모든 언론사 보기" else "새로고침",
                onAction = { if (state.selectedPublishers.isNotEmpty()) onPublishers(emptySet()) else onRefresh() }
            )
            else -> LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                item {
                    Row(Modifier.fillMaxWidth().background(Fl.Inset)
                        .padding(horizontal = 18.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (state.isRefreshing) "새 기사를 확인하는 중…"
                            else "${state.category.label} · 최신순",
                            color = Fl.Muted,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(Modifier.weight(1f))
                        Text("새로고침", fontSize = 11.sp, color = Fl.Accent,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.clickable(enabled = !state.isRefreshing, onClick = onRefresh)
                                .padding(horizontal = 6.dp, vertical = 4.dp))
                    }
                }
                items(state.items, key = { it.id }) { article ->
                    ArticleFeedRow(article, onClick = { onArticle(article.id) })
                    HorizontalDivider(color = Fl.Line, modifier = Modifier.padding(horizontal = 18.dp))
                }
                if (state.isLoadingMore) {
                    item { Box(Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(Modifier.size(22.dp), color = Fl.Accent, strokeWidth = 2.dp)
                    } }
                }
                state.nextPageError?.let { message ->
                    item { FeedMessage("다음 기사를 불러오지 못했어요", message, "다시 시도", onLoadMore) }
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }

    if (showPublishers) {
        PublisherFilterSheet(
            publishers = state.publishers,
            selected = state.selectedPublishers,
            onDismiss = { showPublishers = false },
            onApply = { onPublishers(it); showPublishers = false }
        )
    }
    if (showAccount) {
        ModalBottomSheet(onDismissRequest = { showAccount = false }, containerColor = Fl.Card) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("계정", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Fl.Ink)
                Text(session.user.username, fontSize = 15.sp, color = Fl.Ink)
                session.user.email?.let { Caption(it) }
                HorizontalDivider(color = Fl.Line, modifier = Modifier.padding(vertical = 8.dp))
                Text("로그아웃", color = Fl.Danger, fontWeight = FontWeight.Medium,
                    modifier = Modifier.fillMaxWidth().clickable { showAccount = false; onLogout() }
                        .padding(vertical = 14.dp))
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun ArticleFeedRow(article: ArticleSummary, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Caption("${article.publisher.name} · ${article.category.label} · ${relativeTime(article.publishedAt)}")
            Spacer(Modifier.weight(1f))
            AnalysisStateLabel(article.analysisState)
        }
        Text(article.title, maxLines = 2, overflow = TextOverflow.Ellipsis,
            fontFamily = Fl.Serif, fontSize = 18.sp, lineHeight = 25.sp,
            fontWeight = FontWeight.SemiBold, color = Fl.Ink)
    }
}

@Composable
private fun CategoryTab(label: String, selected: Boolean, onClick: () -> Unit) {
    Column(
        Modifier.width(44.dp).height(44.dp).clickable(onClick = onClick)
            .semantics { role = Role.Tab },
        verticalArrangement = Arrangement.Bottom,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(label, fontSize = 15.sp, color = if (selected) Fl.Ink else Fl.Muted,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 8.dp))
        Box(Modifier.height(2.dp).fillMaxWidth()
            .background(if (selected) Fl.Accent else androidx.compose.ui.graphics.Color.Transparent))
    }
}

@Composable
private fun AnalysisStateLabel(state: StoredAnalysisState) {
    val treatment = when (state) {
        StoredAnalysisState.UPDATING -> Triple(Fl.Muted, "분석 중", Fl.Muted)
        StoredAnalysisState.READY -> Triple(Fl.InkDiff, "분석 완료", Fl.InkDiff)
        StoredAnalysisState.PARTIAL -> Triple(Fl.InkDiff, "일부 분석", Fl.InkDiff)
        else -> null
    } ?: return
    Row(verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Box(Modifier.size(6.dp).background(treatment.first, CircleShape))
        Text(treatment.second, fontSize = 10.sp, color = treatment.third,
            fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun LoadingFeed() {
    Column(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        repeat(5) {
            Column(Modifier.fillMaxWidth().background(Fl.Inset, RoundedCornerShape(10.dp)).padding(16.dp)) {
                Box(Modifier.fillMaxWidth(0.35f).height(10.dp).background(Fl.Line, RoundedCornerShape(5.dp)))
                Spacer(Modifier.height(12.dp))
                Box(Modifier.fillMaxWidth().height(20.dp).background(Fl.Line, RoundedCornerShape(5.dp)))
                Spacer(Modifier.height(7.dp))
                Box(Modifier.fillMaxWidth(0.72f).height(20.dp).background(Fl.Line, RoundedCornerShape(5.dp)))
            }
        }
    }
}

@Composable
private fun FeedMessage(title: String, detail: String, action: String, onAction: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, fontWeight = FontWeight.SemiBold, color = Fl.Ink)
        Text(detail, fontSize = 13.sp, lineHeight = 20.sp, color = Fl.Muted)
        OutlinedPill(action, onAction)
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun PublisherFilterSheet(
    publishers: List<PublisherItem>,
    selected: Set<String>,
    onDismiss: () -> Unit,
    onApply: (Set<String>) -> Unit
) {
    var draft by remember(selected) { mutableStateOf(selected) }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Fl.Card) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("언론사 선택", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Fl.Ink)
                Spacer(Modifier.weight(1f))
                Text("완료", color = Fl.Accent, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable { onApply(draft) }.padding(10.dp))
            }
            FilterRow("전체 언론사", draft.isEmpty()) { draft = emptySet() }
            HorizontalDivider(color = Fl.Line)
            publishers.forEach { publisher ->
                FilterRow(publisher.name, publisher.code in draft) {
                    draft = if (publisher.code in draft) draft - publisher.code else draft + publisher.code
                }
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun FilterRow(label: String, checked: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = { onClick() })
        Text(label, fontSize = 15.sp, color = Fl.Ink, modifier = Modifier.padding(start = 6.dp))
    }
}

private fun relativeTime(raw: String): String = try {
    val then = OffsetDateTime.parse(raw)
    val now = OffsetDateTime.now(then.offset)
    val minutes = Duration.between(then, now).toMinutes()
    when {
        minutes < 1 -> "방금"
        minutes < 60 -> "${minutes}분 전"
        minutes < 24 * 60 -> "${minutes / 60}시간 전"
        else -> formatNewsTimestamp(raw)
    }
} catch (_: Exception) { raw }
