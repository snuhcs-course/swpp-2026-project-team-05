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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swpp.team5.frameless.ui.theme.Fl
import com.swpp.team5.frameless.ui.components.ArticleBookmarkIcon
import com.swpp.team5.frameless.ui.viewmodel.SavedArticlesState

@Composable
internal fun NewsSavedScreen(
    state: SavedArticlesState,
    listState: LazyListState,
    savedMutationArticleId: String?,
    saveError: String?,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    onArticle: (String) -> Unit,
    onToggleSaved: (String, Boolean) -> Unit
) {
    BackHandler(onBack = onBack)
    LaunchedEffect(listState.firstVisibleItemIndex, state.items.size, state.nextCursor) {
        if (state.items.isNotEmpty() && listState.firstVisibleItemIndex >= state.items.lastIndex - 3) {
            onLoadMore()
        }
    }

    Column(Modifier.fillMaxSize().background(Fl.Card)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "←", fontSize = 24.sp, color = Fl.Ink,
                modifier = Modifier.clickable(onClick = onBack).padding(end = 14.dp)
                    .semantics {
                        role = Role.Button
                        contentDescription = "기사 목록으로 돌아가기"
                    }
            )
            ArticleBookmarkIcon(true, Fl.Accent, Modifier.size(19.dp))
            Text("저장한 기사", fontSize = 18.sp, fontWeight = FontWeight.Bold,
                color = Fl.Ink, modifier = Modifier.padding(start = 8.dp))
            Spacer(Modifier.weight(1f))
            Text(
                "새로고침", fontSize = 12.sp, color = Fl.Accent,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable(enabled = !state.isRefreshing, onClick = onRefresh)
                    .padding(horizontal = 4.dp, vertical = 8.dp)
            )
        }
        HorizontalDivider(color = Fl.Line)
        if (saveError != null) {
            Text(saveError, color = Fl.Danger, fontSize = 12.sp,
                modifier = Modifier.fillMaxWidth().background(Fl.Inset)
                    .padding(horizontal = 18.dp, vertical = 9.dp))
        }
        when {
            state.isLoading && state.items.isEmpty() -> LoadingFeed()
            state.error != null && state.items.isEmpty() -> FeedMessage(
                "저장한 기사를 불러오지 못했어요", state.error, "다시 시도", onRetry
            )
            state.items.isEmpty() -> FeedMessage(
                "아직 저장한 기사가 없어요",
                "기사의 북마크 버튼을 누르면 이곳에서 다시 읽을 수 있어요.",
                "기사 둘러보기",
                onBack
            )
            else -> LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                item {
                    Row(
                        Modifier.fillMaxWidth().background(Fl.Inset)
                            .padding(horizontal = 18.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            if (state.isRefreshing) "저장 목록을 갱신하는 중…" else "최근 저장순",
                            color = Fl.Muted, fontSize = 11.sp, fontWeight = FontWeight.Medium
                        )
                    }
                }
                items(state.items, key = { it.id }) { article ->
                    ArticleFeedRow(
                        article = article,
                        onClick = { onArticle(article.id) },
                        onToggleSaved = { onToggleSaved(article.id, article.isSaved) },
                        savedPending = savedMutationArticleId == article.id
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
