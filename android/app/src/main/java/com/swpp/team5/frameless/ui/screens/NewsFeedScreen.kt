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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swpp.team5.frameless.data.AppSession
import com.swpp.team5.frameless.data.ArticleSummary
import com.swpp.team5.frameless.data.FeedScope
import com.swpp.team5.frameless.data.NewsCategory
import com.swpp.team5.frameless.data.PublisherItem
import com.swpp.team5.frameless.ui.components.ArticleBookmarkIcon
import com.swpp.team5.frameless.ui.theme.Caption
import com.swpp.team5.frameless.ui.theme.FilledPill
import com.swpp.team5.frameless.ui.theme.Fl
import com.swpp.team5.frameless.ui.theme.OutlinedPill
import com.swpp.team5.frameless.ui.viewmodel.NewsFeedState
import com.swpp.team5.frameless.ui.viewmodel.SearchSectionState
import java.time.Duration
import java.time.OffsetDateTime

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun NewsFeedScreen(
    state: NewsFeedState,
    session: AppSession,
    listState: LazyListState,
    onCategory: (NewsCategory) -> Unit,
    onScope: (FeedScope) -> Unit,
    onSources: (Set<String>, () -> Unit) -> Unit,
    onRefresh: () -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    onLoadMoreSearch: (NewsCategory) -> Unit,
    onRetrySearch: (NewsCategory) -> Unit,
    onSearchOpen: () -> Unit,
    onSearchInput: (String) -> Unit,
    onSearchSubmit: () -> Unit,
    onSearchClose: () -> Unit,
    onSavedOpen: () -> Unit,
    onArticle: (String) -> Unit,
    onLogout: () -> Unit
) {
    var showFeedScope by remember { mutableStateOf(false) }
    var showSources by remember { mutableStateOf(false) }
    var showAccount by remember { mutableStateOf(false) }
    var showUrlAnalysisMock by remember { mutableStateOf(false) }
    var savedFeedIndex by remember { mutableIntStateOf(0) }
    var savedFeedOffset by remember { mutableIntStateOf(0) }

    LaunchedEffect(state.category, state.scope) {
        listState.scrollToItem(0)
    }

    LaunchedEffect(state.activeSearchQuery, state.isLoading) {
        showUrlAnalysisMock = false
        if (state.activeSearchQuery.isNotBlank() && !state.isLoading &&
            state.searchSections.values.any { it.items.isNotEmpty() }) {
            listState.scrollToItem(0)
        }
    }

    LaunchedEffect(state.isSearchOpen) {
        if (!state.isSearchOpen && listState.layoutInfo.totalItemsCount > 0) {
            listState.scrollToItem(savedFeedIndex, savedFeedOffset)
        }
    }

    LaunchedEffect(listState.firstVisibleItemIndex, state.items.size, state.nextCursor) {
        if (state.activeSearchQuery.isBlank() && state.items.isNotEmpty() &&
            listState.firstVisibleItemIndex >= state.items.lastIndex - 3) {
            onLoadMore()
        }
    }

    Column(Modifier.fillMaxSize().background(Fl.Card)) {
        Column(Modifier.fillMaxWidth().padding(top = 12.dp)) {
            if (state.isSearchOpen) {
                SearchHeader(
                    value = state.searchInput,
                    onValue = onSearchInput,
                    onSubmit = onSearchSubmit,
                    onClose = onSearchClose
                )
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.padding(start = 18.dp)) { BrandRow() }
                    Spacer(Modifier.weight(1f))
                    IconButton(
                        onClick = {
                            savedFeedIndex = listState.firstVisibleItemIndex
                            savedFeedOffset = listState.firstVisibleItemScrollOffset
                            onSearchOpen()
                        },
                        modifier = Modifier.size(40.dp).semantics {
                            contentDescription = "기사 검색"
                        }
                    ) {
                        Icon(Icons.Default.Search, contentDescription = null, tint = Fl.Ink2)
                    }
                    IconButton(
                        onClick = onSavedOpen,
                        modifier = Modifier.size(40.dp).semantics {
                            contentDescription = "저장한 기사"
                        }
                    ) {
                        ArticleBookmarkIcon(true, Fl.Ink2, Modifier.size(19.dp))
                    }
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
            }
            Spacer(Modifier.height(if (state.isSearchOpen) 8.dp else 12.dp))
            Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    NewsCategory.entries.forEach { category ->
                        val section = state.searchSections[category]
                        CategoryTab(
                            label = category.label,
                            selected = if (state.isSearchOpen) {
                                state.searchCategory == category
                            } else {
                                state.category == category
                            },
                            count = if (state.isSearchOpen && state.activeSearchQuery.isNotBlank() &&
                                section != null && !section.isLoading) section.totalCount else null,
                            onClick = { onCategory(category) }
                        )
                    }
                }
                Spacer(Modifier.weight(1f))
                if (!state.isSearchOpen) {
                    Text(
                        if (state.scope == FeedScope.ALL) "최신  ⌄"
                        else "팔로잉 · ${state.publishers.count { it.isFollowed }}곳  ⌄",
                        color = Fl.Ink2,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.clickable { showFeedScope = true }
                            .padding(horizontal = 4.dp, vertical = 12.dp)
                            .semantics { role = Role.Button }
                    )
                }
            }
        }
        HorizontalDivider(color = Fl.Line)
        val normalizedSearch = state.searchInput.trim().replace(Regex("\\s+"), " ")
        when {
            state.isSearchOpen && normalizedSearch.length == 1 -> FeedMessage(
                title = "검색어를 조금 더 입력해 주세요",
                detail = "두 글자 이상부터 전체 기사에서 검색해요.",
                action = "다시 입력하기",
                onAction = { onSearchInput("") }
            )
            state.isSearchPending -> LoadingFeed()
            state.activeSearchQuery.isNotBlank() -> SearchResultsFeed(
                query = state.activeSearchQuery,
                sections = state.searchSections,
                selectedCategory = state.searchCategory,
                listState = listState,
                onRefresh = onRefresh,
                onRetry = onRetry,
                onRetrySection = onRetrySearch,
                onLoadMore = onLoadMoreSearch,
                onArticle = onArticle,
                onAnalyzeLink = { showUrlAnalysisMock = true },
                onClearSearch = { onSearchInput("") }
            )
            state.isLoading && state.items.isEmpty() -> LoadingFeed()
            state.error != null && state.items.isEmpty() -> FeedMessage(
                title = "기사를 불러오지 못했어요",
                detail = state.error,
                action = "다시 시도",
                onAction = onRetry
            )
            state.items.isEmpty() && state.activeSearchQuery.isNotBlank() -> SearchNoResults(
                query = state.activeSearchQuery,
                onAnalyzeLink = { showUrlAnalysisMock = true },
                onClearSearch = { onSearchInput("") }
            )
            state.items.isEmpty() -> FeedMessage(
                title = if (state.scope == FeedScope.FOLLOWING && state.publishers.none { it.isFollowed })
                    "팔로우한 언론사가 없어요" else "새 기사가 없어요",
                detail = when {
                    state.scope == FeedScope.FOLLOWING && state.publishers.none { it.isFollowed } ->
                        "자주 읽는 언론사를 선택해 팔로잉 피드를 만들어 보세요."
                    state.scope == FeedScope.FOLLOWING -> "이 분야에는 팔로우한 언론사의 새 기사가 없어요."
                    else -> "잠시 후 새로고침해 주세요."
                },
                action = when {
                    state.scope == FeedScope.FOLLOWING && state.publishers.none { it.isFollowed } -> "언론사 관리"
                    state.scope == FeedScope.FOLLOWING -> "최신 기사 보기"
                    else -> "새로고침"
                },
                onAction = {
                    if (state.scope == FeedScope.FOLLOWING && state.publishers.none { it.isFollowed })
                        showSources = true
                    else if (state.scope == FeedScope.FOLLOWING) onScope(FeedScope.ALL)
                    else onRefresh()
                }
            )
            else -> LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                item {
                    Row(Modifier.fillMaxWidth().background(Fl.Inset)
                        .padding(horizontal = 18.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (state.isRefreshing) "새 기사를 확인하는 중…"
                            else if (state.activeSearchQuery.isNotBlank())
                                "‘${state.activeSearchQuery}’ · 전체 언론사 검색"
                            else if (state.scope == FeedScope.FOLLOWING) "팔로잉 언론사 · 최신순"
                            else "전체 언론사 · 최신순",
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
                    ArticleFeedRow(
                        article = article,
                        onClick = { onArticle(article.id) }
                    )
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

    if (showFeedScope) {
        FeedScopeSheet(
            scope = state.scope,
            followedCount = state.publishers.count { it.isFollowed },
            onDismiss = { showFeedScope = false },
            onScope = { onScope(it); showFeedScope = false },
            onManage = { showFeedScope = false; showSources = true }
        )
    }
    if (showSources) {
        PublisherFollowSheet(
            publishers = state.publishers,
            isSaving = state.isSavingSources,
            error = state.sourceSaveError,
            onDismiss = { if (!state.isSavingSources) showSources = false },
            onApply = { codes -> onSources(codes) { showSources = false } }
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
    if (showUrlAnalysisMock) {
        UrlAnalysisMockSheet(onDismiss = { showUrlAnalysisMock = false })
    }
}

@Composable
private fun SearchResultsFeed(
    query: String,
    sections: Map<NewsCategory, SearchSectionState>,
    selectedCategory: NewsCategory,
    listState: LazyListState,
    onRefresh: () -> Unit,
    onRetry: () -> Unit,
    onRetrySection: (NewsCategory) -> Unit,
    onLoadMore: (NewsCategory) -> Unit,
    onArticle: (String) -> Unit,
    onAnalyzeLink: () -> Unit,
    onClearSearch: () -> Unit
) {
    val ordered = NewsCategory.entries.map { category ->
        sections[category] ?: SearchSectionState(category = category, isLoading = true)
    }
    val selected = sections[selectedCategory] ?:
        SearchSectionState(category = selectedCategory, isLoading = true)
    val initialLoading = ordered.all { it.isLoading && it.items.isEmpty() }
    val allFailed = ordered.all { it.error != null }
    val hasResults = ordered.any { it.totalCount > 0 || it.items.isNotEmpty() }

    when {
        initialLoading -> LoadingFeed()
        allFailed -> FeedMessage(
            title = "검색 결과를 불러오지 못했어요",
            detail = "잠시 후 다시 시도해 주세요.",
            action = "다시 시도",
            onAction = onRetry
        )
        else -> LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            item {
                Row(
                    Modifier.fillMaxWidth().background(Fl.Inset)
                        .padding(horizontal = 18.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        if (ordered.any { it.isLoading }) "‘$query’ 검색 중…" else "‘$query’ · 전체 기사 검색",
                        color = Fl.Muted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        "새로고침",
                        fontSize = 11.sp,
                        color = Fl.Accent,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clickable(
                            enabled = ordered.none { it.isLoading },
                            onClick = onRefresh
                        ).padding(horizontal = 6.dp, vertical = 4.dp)
                    )
                }
            }
            when {
                selected.isLoading && selected.items.isEmpty() -> {
                    item(key = "loading-${selected.category.wire}") {
                        Box(
                            Modifier.fillMaxWidth().padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                Modifier.size(20.dp), color = Fl.Accent, strokeWidth = 2.dp
                            )
                        }
                    }
                }
                selected.error != null -> {
                    item(key = "error-${selected.category.wire}") {
                        FeedMessage(
                            "${selected.category.label} 검색 결과를 불러오지 못했어요",
                            selected.error,
                            "다시 시도"
                        ) { onRetrySection(selected.category) }
                    }
                }
                selected.items.isEmpty() && hasResults -> {
                    item(key = "empty-${selected.category.wire}") {
                        Text(
                            "${selected.category.label} 분야에는 일치하는 기사가 없어요.",
                            color = Fl.Muted,
                            fontSize = 13.sp,
                            modifier = Modifier.fillMaxWidth()
                                .padding(horizontal = 18.dp, vertical = 24.dp)
                        )
                    }
                }
            }
            items(selected.items, key = { "${selected.category.wire}-${it.id}" }) { article ->
                ArticleFeedRow(article = article, onClick = { onArticle(article.id) })
                HorizontalDivider(color = Fl.Line, modifier = Modifier.padding(horizontal = 18.dp))
            }
            when {
                selected.isLoadingMore -> {
                    item(key = "more-loading-${selected.category.wire}") {
                        Box(
                            Modifier.fillMaxWidth().padding(18.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                Modifier.size(20.dp), color = Fl.Accent, strokeWidth = 2.dp
                            )
                        }
                    }
                }
                selected.nextPageError != null -> {
                    item(key = "more-error-${selected.category.wire}") {
                        FeedMessage(
                            "다음 검색 결과를 불러오지 못했어요",
                            selected.nextPageError,
                            "다시 시도"
                        ) { onLoadMore(selected.category) }
                    }
                }
                selected.nextCursor != null -> {
                    item(key = "more-${selected.category.wire}") {
                        Text(
                            "${selected.category.label} 기사 더 보기",
                            color = Fl.Accent,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.fillMaxWidth()
                                .clickable { onLoadMore(selected.category) }
                                .padding(horizontal = 18.dp, vertical = 18.dp)
                        )
                    }
                }
            }
            if (!hasResults && ordered.none { it.isLoading || it.error != null }) {
                item(key = "global-empty-search") {
                    SearchNoResults(
                        query = query,
                        onAnalyzeLink = onAnalyzeLink,
                        onClearSearch = onClearSearch
                    )
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
internal fun ArticleFeedRow(
    article: ArticleSummary,
    onClick: () -> Unit
) {
    Column(
        Modifier.fillMaxWidth().clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Caption("${article.publisher.name} · ${relativeTime(article.publishedAt)}")
        }
        Text(article.title, maxLines = 2, overflow = TextOverflow.Ellipsis,
            fontFamily = Fl.Serif, fontSize = 18.sp, lineHeight = 25.sp,
            fontWeight = FontWeight.SemiBold, color = Fl.Ink)
        article.searchExcerpt?.let { excerpt ->
            Text(excerpt, maxLines = 2, overflow = TextOverflow.Ellipsis,
                fontSize = 13.sp, lineHeight = 19.sp, color = Fl.Muted)
        }
    }
}

@Composable
private fun SearchHeader(
    value: String,
    onValue: (String) -> Unit,
    onSubmit: () -> Unit,
    onClose: () -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onClose, modifier = Modifier.semantics {
            contentDescription = "검색 닫기"
        }) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = Fl.Ink)
        }
        BasicTextField(
            value = value,
            onValueChange = onValue,
            singleLine = true,
            textStyle = androidx.compose.ui.text.TextStyle(
                color = Fl.Ink, fontSize = 15.sp, fontWeight = FontWeight.Medium
            ),
            cursorBrush = SolidColor(Fl.Accent),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
            modifier = Modifier.weight(1f).height(44.dp)
                .background(Fl.Inset, RoundedCornerShape(8.dp))
                .border(1.dp, Fl.Line, RoundedCornerShape(8.dp))
                .focusRequester(focusRequester)
                .semantics { contentDescription = "기사 검색어" },
            decorationBox = { input ->
                Row(
                    Modifier.fillMaxSize().padding(horizontal = 13.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Search, contentDescription = null,
                        tint = Fl.Muted, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(9.dp))
                    Box(Modifier.weight(1f)) {
                        if (value.isEmpty()) Text(
                            "기사, 언론사, 본문 검색",
                            color = Fl.Muted,
                            fontSize = 14.sp
                        )
                        input()
                    }
                }
            }
        )
        if (value.isNotEmpty()) {
            IconButton(onClick = { onValue("") }, modifier = Modifier.semantics {
                contentDescription = "검색어 지우기"
            }) {
                Icon(Icons.Default.Close, contentDescription = null, tint = Fl.Ink2)
            }
        } else {
            Spacer(Modifier.width(48.dp))
        }
    }
}

@Composable
private fun CategoryTab(
    label: String,
    selected: Boolean,
    count: Int? = null,
    onClick: () -> Unit
) {
    Column(
        Modifier.width(if (count == null) 44.dp else 78.dp)
            .height(44.dp).clickable(onClick = onClick)
            .semantics {
                role = Role.Tab
                if (count != null) {
                    contentDescription = "$label 검색 결과 ${count}개"
                }
            },
        verticalArrangement = Arrangement.Bottom,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                label,
                fontSize = 15.sp,
                color = if (selected) Fl.Ink else Fl.Muted,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
            )
            count?.let {
                Box(
                    Modifier.background(Fl.Inset, RoundedCornerShape(999.dp))
                        .padding(horizontal = 7.dp, vertical = 2.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        it.toString(),
                        color = if (selected) Fl.Ink2 else Fl.Muted,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
        Box(Modifier.height(2.dp).fillMaxWidth()
            .background(if (selected) Fl.Accent else androidx.compose.ui.graphics.Color.Transparent))
    }
}

@Composable
internal fun LoadingFeed() {
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
internal fun FeedMessage(title: String, detail: String, action: String, onAction: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, fontWeight = FontWeight.SemiBold, color = Fl.Ink)
        Text(detail, fontSize = 13.sp, lineHeight = 20.sp, color = Fl.Muted)
        OutlinedPill(action, onAction)
    }
}

@Composable
private fun SearchNoResults(
    query: String,
    onAnalyzeLink: () -> Unit,
    onClearSearch: () -> Unit
) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 44.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("검색 결과가 없어요", fontWeight = FontWeight.SemiBold, color = Fl.Ink)
        Text(
            "‘$query’와 일치하는 기사를 찾지 못했어요. 찾고 있는 기사의 링크가 있다면 분석 흐름을 미리 확인할 수 있어요.",
            fontSize = 13.sp,
            lineHeight = 20.sp,
            color = Fl.Muted
        )
        Spacer(Modifier.height(4.dp))
        FilledPill("기사 링크로 분석하기", onAnalyzeLink)
        Text(
            "전체 기사 보기",
            fontSize = 13.sp,
            color = Fl.Muted,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.clickable(onClick = onClearSearch).padding(8.dp)
                .semantics { role = Role.Button }
        )
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun FeedScopeSheet(
    scope: FeedScope,
    followedCount: Int,
    onDismiss: () -> Unit,
    onScope: (FeedScope) -> Unit,
    onManage: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Fl.Card) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
            Text("피드", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Fl.Ink)
            Spacer(Modifier.height(8.dp))
            Column(Modifier.selectableGroup()) {
                FeedScopeOption(
                    label = "최신",
                    detail = "모든 언론사",
                    selected = scope == FeedScope.ALL,
                    onClick = { onScope(FeedScope.ALL) }
                )
                FeedScopeOption(
                    label = "팔로잉",
                    detail = if (followedCount == 0) "선택한 언론사 없음" else "${followedCount}개 언론사",
                    selected = scope == FeedScope.FOLLOWING,
                    onClick = { onScope(FeedScope.FOLLOWING) }
                )
            }
            HorizontalDivider(color = Fl.Line, modifier = Modifier.padding(vertical = 8.dp))
            Text(
                "언론사 관리",
                color = Fl.Accent,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.fillMaxWidth().clickable(onClick = onManage)
                    .padding(horizontal = 12.dp, vertical = 14.dp)
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun PublisherFollowSheet(
    publishers: List<PublisherItem>,
    isSaving: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onApply: (Set<String>) -> Unit
) {
    val selected = publishers.filter { it.isFollowed }.map { it.code }.toSet()
    var draft by remember(selected) { mutableStateOf(selected) }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Fl.Card) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("언론사 관리", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Fl.Ink)
                Spacer(Modifier.weight(1f))
                Text(if (isSaving) "저장 중…" else "저장", color = Fl.Accent,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable(enabled = !isSaving) { onApply(draft) }.padding(10.dp))
            }
            Caption("선택한 언론사는 팔로잉 피드에만 반영되며 기사 분석에는 영향을 주지 않아요.")
            HorizontalDivider(color = Fl.Line)
            publishers.forEach { publisher ->
                CheckboxRow(publisher.name, publisher.code in draft) {
                    if (!isSaving) {
                        draft = if (publisher.code in draft) draft - publisher.code else draft + publisher.code
                    }
                }
            }
            error?.let { Text(it, color = Fl.Danger, fontSize = 13.sp, modifier = Modifier.padding(12.dp)) }
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun FeedScopeOption(
    label: String,
    detail: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth()
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .semantics { contentDescription = "$label 피드" }
            .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = selected,
            onClick = null,
            colors = RadioButtonDefaults.colors(
                selectedColor = Fl.Accent,
                unselectedColor = Fl.Muted
            )
        )
        Column(Modifier.padding(start = 6.dp)) {
            Text(label, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = Fl.Ink)
            Caption(detail)
        }
    }
}

@Composable
private fun CheckboxRow(label: String, checked: Boolean, onClick: () -> Unit) {
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
