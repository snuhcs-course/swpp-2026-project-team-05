package com.swpp.team5.frameless.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.ui.res.painterResource
import com.swpp.team5.frameless.R
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swpp.team5.frameless.ui.theme.Fl
import com.swpp.team5.frameless.ui.components.ArticleBookmarkIcon
import com.swpp.team5.frameless.ui.components.edgeSwipeBack
import com.swpp.team5.frameless.ui.viewmodel.SavedArticlesState

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun NewsSavedScreen(
    state: SavedArticlesState,
    listState: LazyListState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    onArticle: (String) -> Unit,
    rootShell: Boolean = false
) {
    BackHandler(enabled = !rootShell, onBack = onBack)
    val pullRefreshState = rememberPullToRefreshState()
    LaunchedEffect(listState.firstVisibleItemIndex, state.items.size, state.nextCursor) {
        if (state.items.isNotEmpty() && listState.firstVisibleItemIndex >= state.items.lastIndex - 3) {
            onLoadMore()
        }
    }

    Column(
        Modifier.fillMaxSize().edgeSwipeBack(enabled = !rootShell, onBack = onBack)
            .background(Fl.Card)
            .testTag("saved-screen")
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (!rootShell) {
                IconButton(onClick = onBack, modifier = Modifier.semantics {
                    contentDescription = "기사 목록으로 돌아가기"
                }) {
                    Icon(painterResource(R.drawable.ic_portal_arrow_back), contentDescription = null,
                        tint = Fl.Ink, modifier = Modifier.size(23.dp))
                }
                ArticleBookmarkIcon(true, Fl.Accent, Modifier.size(19.dp))
                Text("저장한 기사", fontSize = 18.sp, fontWeight = FontWeight.Bold,
                    color = Fl.Ink, modifier = Modifier.padding(start = 8.dp))
            } else {
                Text("저장", fontSize = 25.sp, fontWeight = FontWeight.Bold, color = Fl.Ink)
                Spacer(Modifier.weight(1f))
                Text("최근 저장순", fontSize = 11.sp, color = Fl.Muted)
            }
        }
        HorizontalDivider(color = Fl.Line)
        when {
            state.isLoading && state.items.isEmpty() -> LoadingFeed()
            state.error != null && state.items.isEmpty() -> FeedMessage(
                "저장한 기사를 불러오지 못했어요", state.error, "다시 시도", onRetry
            )
            state.items.isEmpty() -> FeedMessage(
                if (state.isOffline) "오프라인에서 읽을 저장 기사가 없어요"
                else "아직 저장한 기사가 없어요",
                if (state.isOffline) "온라인에서 기사를 저장하면 본문과 분석을 이 기기에서 다시 볼 수 있어요."
                else "기사의 북마크 버튼을 누르면 이곳에서 다시 읽을 수 있어요.",
                if (state.isOffline) "다시 연결" else "기사 둘러보기",
                if (state.isOffline) onRefresh else onBack
            )
            else -> PullToRefreshBox(
                isRefreshing = state.isRefreshing,
                onRefresh = { if (!state.isRefreshing) onRefresh() },
                state = pullRefreshState,
                modifier = Modifier.weight(1f),
                indicator = {
                    PullToRefreshDefaults.Indicator(
                        state = pullRefreshState,
                        isRefreshing = state.isRefreshing,
                        modifier = Modifier.align(Alignment.TopCenter),
                        containerColor = Fl.Card,
                        color = Fl.Accent
                    )
                }
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().testTag("saved-list")
                ) {
                    item {
                        Row(
                            Modifier.fillMaxWidth().background(Fl.Inset)
                                .padding(horizontal = 18.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                if (state.isOffline) "저장된 목록 · 오프라인"
                                else if (state.isRefreshing) "저장 목록을 갱신하는 중…"
                                else "최근 저장순",
                                color = Fl.Muted, fontSize = 11.sp, fontWeight = FontWeight.Medium
                            )
                        }
                    }
                    items(state.items, key = { it.id }) { article ->
                        ArticleFeedRow(
                            article = article,
                            onClick = { onArticle(article.id) }
                        )
                        HorizontalDivider(color = Fl.Line, modifier = Modifier.padding(horizontal = 18.dp))
                    }
                    if (state.isLoadingMore) {
                        item {
                            Box(
                                Modifier.fillMaxWidth().padding(20.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(
                                    Modifier.size(22.dp), color = Fl.Accent, strokeWidth = 2.dp
                                )
                            }
                        }
                    }
                    state.nextPageError?.let { message ->
                        item { FeedMessage("다음 기사를 불러오지 못했어요", message, "다시 시도", onLoadMore) }
                    }
                    item { Spacer(Modifier.size(24.dp)) }
                }
            }
        }
    }
}
