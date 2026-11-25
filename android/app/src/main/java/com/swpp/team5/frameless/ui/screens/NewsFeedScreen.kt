package com.swpp.team5.frameless.ui.screens

import com.swpp.team5.frameless.ui.components.BrandRow

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
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
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
import androidx.compose.ui.res.painterResource
import com.swpp.team5.frameless.R
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swpp.team5.frameless.data.AppSession
import com.swpp.team5.frameless.data.ArticleSummary
import com.swpp.team5.frameless.data.FeedScope
import com.swpp.team5.frameless.data.NewsCategory
import com.swpp.team5.frameless.data.PublisherItem
import com.swpp.team5.frameless.ui.components.ArticleBookmarkIcon
import com.swpp.team5.frameless.ui.components.dismissKeyboardOnTap
import com.swpp.team5.frameless.ui.theme.Caption
import com.swpp.team5.frameless.ui.theme.Fl
import com.swpp.team5.frameless.ui.theme.OutlinedPill
import com.swpp.team5.frameless.ui.viewmodel.NewsFeedState
import com.swpp.team5.frameless.ui.viewmodel.SearchFilter
import com.swpp.team5.frameless.ui.viewmodel.SearchSectionState

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun NewsFeedScreen(
    state: NewsFeedState,
    session: AppSession?,
    listState: LazyListState,
    onCategory: (NewsCategory) -> Unit,
    onSearchFilter: (SearchFilter) -> Unit,
    onScope: (FeedScope) -> Unit,
    onSources: (Set<String>, () -> Unit) -> Unit,
    onRefresh: () -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    onLoadMoreSearch: (SearchFilter) -> Unit,
    onRetrySearch: (SearchFilter) -> Unit,
    onSearchOpen: () -> Unit,
    onSearchInput: (String) -> Unit,
    onSearchSubmit: () -> Unit,
    onSearchClose: () -> Unit,
    onSavedOpen: () -> Unit,
    onArticle: (String) -> Unit,
    onLogin: () -> Unit,
    onLogout: () -> Unit,
    onTutorialOpen: () -> Unit = {},
    rootShell: Boolean = false,
) {
    var showFeedScope by remember { mutableStateOf(false) }
    var showSources by remember { mutableStateOf(false) }
    var showAccount by remember { mutableStateOf(false) }
    var savedFeedIndex by remember { mutableIntStateOf(0) }
    var savedFeedOffset by remember { mutableIntStateOf(0) }
    val pullRefreshState = rememberPullToRefreshState()

    LaunchedEffect(state.category, state.scope) {
        listState.scrollToItem(0)
    }

    LaunchedEffect(state.activeSearchQuery, state.isLoading) {
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
        if (!state.isSearchOpen && state.items.isNotEmpty() &&
            listState.firstVisibleItemIndex >= state.items.lastIndex - 3) {
            onLoadMore()
        }
    }

    val normalizedSearch = state.searchInput.trim().replace(Regex("\\s+"), " ")
    Column(Modifier.fillMaxSize().background(HomeTokens.OffWhite).dismissKeyboardOnTap()) {
        Column(
            Modifier.fillMaxWidth().background(HomeTokens.OffWhite)
                .padding(top = if (state.isSearchOpen) 12.dp else 0.dp)
        ) {
            if (state.isSearchOpen) {
                SearchHeader(
                    value = state.searchInput,
                    onValue = onSearchInput,
                    onSubmit = onSearchSubmit,
                    onClose = onSearchClose
                )
            } else {
                if (rootShell) {
                    Row(
                        Modifier.fillMaxWidth().height(58.dp).padding(horizontal = 18.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("뉴스", fontSize = 25.sp, fontWeight = FontWeight.Bold, color = Fl.Ink)
                        Spacer(Modifier.weight(1f))
                        Text(
                            "FRAMELESS",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                            color = Fl.Muted
                        )
                    }
                } else {
                    Row(
                        Modifier.fillMaxWidth().height(56.dp).padding(start = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FeedMenuButton(onTutorialOpen = onTutorialOpen)
                        BrandRow()
                        Spacer(Modifier.weight(1f))
                        IconButton(
                            onClick = {
                                savedFeedIndex = listState.firstVisibleItemIndex
                                savedFeedOffset = listState.firstVisibleItemScrollOffset
                                onSearchOpen()
                            },
                            modifier = Modifier.size(44.dp).semantics {
                                contentDescription = "기사 검색"
                            }
                        ) {
                            Icon(painterResource(R.drawable.ic_portal_search), contentDescription = null, tint = Fl.Ink,
                                modifier = Modifier.size(22.dp))
                        }
                        IconButton(
                            onClick = onSavedOpen,
                            modifier = Modifier.size(44.dp).semantics {
                                contentDescription = "저장한 기사"
                            }
                        ) {
                            ArticleBookmarkIcon(true, Fl.Ink, Modifier.size(19.dp))
                        }
                        Box(
                            Modifier.padding(start = 4.dp, end = 14.dp).size(32.dp)
                                .background(HomeTokens.Lavender, CircleShape)
                                .clickable { showAccount = true }
                                .semantics {
                                    role = Role.Button
                                    contentDescription = "계정 메뉴"
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            if (session != null) {
                                Text(session.user.username.take(1).uppercase(), color = HomeTokens.LavenderInk,
                                    fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            } else {
                                Icon(painterResource(R.drawable.ic_portal_profile), contentDescription = null,
                                    tint = HomeTokens.LavenderInk, modifier = Modifier.size(19.dp))
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(if (state.isSearchOpen) 8.dp else 0.dp))
            Row(
                Modifier.fillMaxWidth().padding(horizontal = if (state.isSearchOpen) 18.dp else 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (state.isSearchOpen) {
                    if (normalizedSearch.isNotEmpty()) SearchFilter.entries.forEach { filter ->
                        SearchFilterTab(
                            label = filter.label,
                            selected = state.searchFilter == filter,
                            onClick = { onSearchFilter(filter) }
                        )
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                        if (state.scope == FeedScope.ALL) "최신  ⌄"
                        else "구독 · ${state.publishers.count { it.isFollowed }}곳  ⌄",
                        color = Fl.Ink2,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.clickable { showFeedScope = true }
                            .padding(horizontal = 4.dp, vertical = 12.dp)
                            .semantics { role = Role.Button }
                    )
                }
            }
        }
        HorizontalDivider(color = HomeTokens.Hairline)
        when {
            state.isSearchOpen && normalizedSearch.isEmpty() -> SearchPrompt()
            state.isSearchPending -> LoadingFeed()
            state.activeSearchQuery.isNotBlank() -> SearchResultsFeed(
                query = state.activeSearchQuery,
                sections = state.searchSections,
                selectedFilter = state.searchFilter,
                listState = listState,
                onRetrySection = onRetrySearch,
                onLoadMore = onLoadMoreSearch,
                onArticle = onArticle,
                onShowAllResults = { onSearchFilter(SearchFilter.ALL) }
            )
            state.isLoading && state.items.isEmpty() -> LoadingFeed()
            state.error != null && state.items.isEmpty() -> FeedMessage(
                title = "기사를 불러오지 못했어요",
                detail = state.error,
                action = "다시 시도",
                onAction = onRetry
            )
            state.items.isEmpty() -> FeedMessage(
                title = if (state.isOffline) "오프라인에서 읽을 저장 기사가 없어요"
                else if (state.scope == FeedScope.FOLLOWING && state.publishers.none { it.isFollowed })
                    "구독한 언론사가 없어요" else "새 기사가 없어요",
                detail = when {
                    state.isOffline -> "온라인에서 기사를 저장하면 본문과 분석을 이 기기에서 다시 볼 수 있어요."
                    state.scope == FeedScope.FOLLOWING && state.publishers.none { it.isFollowed } ->
                        "자주 읽는 언론사를 선택해 구독 피드를 만들어 보세요."
                    state.scope == FeedScope.FOLLOWING -> "이 분야에는 구독한 언론사의 새 기사가 없어요."
                    else -> "잠시 후 새로고침해 주세요."
                },
                action = when {
                    state.isOffline -> "다시 연결"
                    state.scope == FeedScope.FOLLOWING && state.publishers.none { it.isFollowed } -> "언론사 관리"
                    state.scope == FeedScope.FOLLOWING -> "최신 기사 보기"
                    else -> "새로고침"
                },
                onAction = {
                    if (state.isOffline) onRefresh()
                    else if (state.scope == FeedScope.FOLLOWING && state.publishers.none { it.isFollowed })
                        showSources = true
                    else if (state.scope == FeedScope.FOLLOWING) onScope(FeedScope.ALL)
                    else onRefresh()
                }
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
                        containerColor = HomeTokens.OffWhite,
                        color = HomeTokens.Navy
                    )
                }
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().background(HomeTokens.OffWhite)
                        .testTag("feed-list")
                ) {
                    // The newest article leads the normal online feed; it is the same stored data, shown larger.
                    val split = splitFeaturedArticle(
                        items = state.items,
                        enabled = !state.isOffline && !state.isSearchOpen && state.activeSearchQuery.isBlank()
                    )
                    split.featured?.let { lead ->
                        // The reading guide shares the lead item so list positions (and load-more) are unchanged.
                        item(key = "featured-${lead.id}") {
                            Column {
                                ReadingGuideRail(onClick = onTutorialOpen)
                                FeaturedArticleCard(article = lead, onClick = { onArticle(lead.id) })
                            }
                        }
                    }
                    item {
                        FeedSectionHeader(
                            title = when {
                                state.isOffline -> "이 기기의 기사"
                                split.featured != null && split.rest.isEmpty() -> null
                                else -> "최신 기사"
                            },
                            caption = if (state.isOffline) "이 기기에 저장한 기사 · 오프라인"
                                else if (state.isRefreshing) "새 기사를 확인하는 중…"
                                else if (state.activeSearchQuery.isNotBlank())
                                    "‘${state.activeSearchQuery}’ · 전체 언론사 검색"
                                else if (state.scope == FeedScope.FOLLOWING) "구독 언론사 · 최신순"
                                else "전체 언론사 · 최신순"
                        )
                        if (split.rest.isNotEmpty()) FeedRowDivider()
                    }
                    itemsIndexed(split.rest, key = { _, article -> article.id }) { index, article ->
                        ArticleFeedRow(
                            article = article,
                            onClick = { onArticle(article.id) }
                        )
                        if (index < split.rest.lastIndex) FeedRowDivider()
                    }
                    if (state.isLoadingMore) {
                        item { Box(Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(Modifier.size(22.dp), color = Fl.Accent, strokeWidth = 2.dp)
                        } }
                    }
                    state.nextPageError?.let { message ->
                        item { FeedMessage("다음 기사를 불러오지 못했어요", message, "다시 시도", onLoadMore) }
                    }
                    // On a tall screen the scroll rule above never reaches the last rows of a 20-article page, so the
                    // next page also has a button (as search results do).
                    if (state.nextCursor != null && !state.isLoadingMore && state.nextPageError == null) {
                        item(key = "feed-more") {
                            Text(
                                "기사 더 보기",
                                color = HomeTokens.Navy,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.fillMaxWidth()
                                    .clickable(onClick = onLoadMore)
                                    .padding(vertical = 18.dp)
                                    .semantics { role = Role.Button },
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                    // Leave enough trailing room for the last row to clear the screen edge.
                    item { Spacer(Modifier.height(48.dp)) }
                }
            }
        }
    }

    if (showFeedScope) {
        FeedScopeSheet(
            scope = state.scope,
            followedCount = state.publishers.count { it.isFollowed },
            onDismiss = { showFeedScope = false },
            onScope = { onScope(it); showFeedScope = false },
            onManage = {
                showFeedScope = false
                if (session == null) onLogin() else showSources = true
            }
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
                Text(if (session == null) "게스트로 읽는 중" else "계정",
                    fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Fl.Ink)
                if (session == null) {
                    Caption("저장과 구독은 로그인 후 사용할 수 있어요.")
                    HorizontalDivider(color = Fl.Line, modifier = Modifier.padding(vertical = 8.dp))
                    Text("로그인", color = Fl.Accent, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.fillMaxWidth().clickable {
                            showAccount = false
                            onLogin()
                        }.padding(vertical = 14.dp))
                } else {
                    Text(session.user.username, fontSize = 15.sp, color = Fl.Ink)
                    session.user.email?.let { Caption(it) }
                    HorizontalDivider(color = Fl.Line, modifier = Modifier.padding(vertical = 8.dp))
                    Text("로그아웃", color = Fl.Danger, fontWeight = FontWeight.Medium,
                        modifier = Modifier.fillMaxWidth().clickable {
                            showAccount = false
                            onLogout()
                        }.padding(vertical = 14.dp))
                }
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun SearchResultsFeed(
    query: String,
    sections: Map<SearchFilter, SearchSectionState>,
    selectedFilter: SearchFilter,
    listState: LazyListState,
    onRetrySection: (SearchFilter) -> Unit,
    onLoadMore: (SearchFilter) -> Unit,
    onArticle: (String) -> Unit,
    onShowAllResults: () -> Unit
) {
    val selected = sections[selectedFilter] ?:
        SearchSectionState(filter = selectedFilter, isLoading = true)

    when {
        selected.isLoading && selected.items.isEmpty() -> LoadingFeed()
        selected.error != null && selected.items.isEmpty() -> FeedMessage(
            title = "검색 결과를 불러오지 못했어요",
            detail = selected.error,
            action = "다시 시도",
            onAction = { onRetrySection(selectedFilter) }
        )
        else -> LazyColumn(state = listState, modifier = Modifier.fillMaxSize().background(HomeTokens.OffWhite)) {
            item {
                Row(
                    Modifier.fillMaxWidth().background(Fl.Inset)
                        .padding(horizontal = 18.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "‘$query’ · ${selectedFilter.label} 기사 검색",
                        color = Fl.Muted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
            when {
                selected.error != null -> {
                    item(key = "error-${selected.filter.name}") {
                        FeedMessage(
                            "${selected.filter.label} 검색 결과를 불러오지 못했어요",
                            selected.error,
                            "다시 시도"
                        ) { onRetrySection(selected.filter) }
                    }
                }
                selected.items.isEmpty() && selected.filter != SearchFilter.ALL -> {
                    item(key = "empty-${selected.filter.name}") {
                        Column(
                            Modifier.fillMaxWidth()
                                .padding(horizontal = 18.dp, vertical = 24.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                "${selected.filter.label} 분야에는 일치하는 기사가 없어요.",
                                color = Fl.Muted,
                                fontSize = 13.sp
                            )
                            Text(
                                "전체 검색 결과 보기",
                                color = Fl.Accent,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.clickable(onClick = onShowAllResults)
                                    .padding(vertical = 4.dp)
                                    .semantics { role = Role.Button }
                            )
                        }
                    }
                }
            }
            items(selected.items, key = { "${selected.filter.name}-${it.id}" }) { article ->
                ArticleFeedRow(article = article, onClick = { onArticle(article.id) })
                FeedRowDivider()
            }
            when {
                selected.isLoadingMore -> {
                    item(key = "more-loading-${selected.filter.name}") {
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
                    item(key = "more-error-${selected.filter.name}") {
                        FeedMessage(
                            "다음 검색 결과를 불러오지 못했어요",
                            selected.nextPageError,
                            "다시 시도"
                        ) { onLoadMore(selected.filter) }
                    }
                }
                selected.nextCursor != null -> {
                    item(key = "more-${selected.filter.name}") {
                        Text(
                            "기사 더 보기",
                            color = Fl.Accent,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.fillMaxWidth()
                                .clickable { onLoadMore(selected.filter) }
                                .padding(horizontal = 18.dp, vertical = 18.dp)
                        )
                    }
                }
            }
            if (selected.filter == SearchFilter.ALL && selected.items.isEmpty() &&
                !selected.isLoading && selected.error == null) {
                item(key = "global-empty-search") {
                    SearchNoResults(query = query)
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
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
            Icon(painterResource(R.drawable.ic_portal_arrow_back), contentDescription = null,
                tint = Fl.Ink, modifier = Modifier.size(23.dp))
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
                    Icon(painterResource(R.drawable.ic_portal_search), contentDescription = null,
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
                Icon(painterResource(R.drawable.ic_portal_close), contentDescription = null,
                    tint = Fl.Ink2, modifier = Modifier.size(21.dp))
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
    onClick: () -> Unit
) {
    Column(
        Modifier.width(52.dp).height(44.dp).clickable(onClick = onClick)
            .semantics {
                role = Role.Tab
                contentDescription = "$label 섹션"
            },
        verticalArrangement = Arrangement.Bottom,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 3.dp, vertical = 8.dp),
            fontSize = 15.5.sp,
            color = if (selected) HomeTokens.Navy else Fl.Muted,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            letterSpacing = if (selected) (-0.2).sp else 0.sp
        )
        Box(Modifier.width(22.dp).height(3.dp)
            .background(
                if (selected) HomeTokens.Navy else androidx.compose.ui.graphics.Color.Transparent,
                RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp)
            ))
    }
}

@Composable
private fun SearchFilterTab(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Column(
        Modifier.padding(end = 12.dp)
            .width(52.dp)
            .height(44.dp)
            .clickable(onClick = onClick)
            .semantics {
                role = Role.Tab
                contentDescription = "$label 검색 필터"
            },
        verticalArrangement = Arrangement.Bottom,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            label,
            modifier = Modifier.padding(vertical = 8.dp),
            fontSize = 14.sp,
            color = if (selected) Fl.Ink else Fl.Muted,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
        )
        Box(
            Modifier.fillMaxWidth().height(2.dp).background(
                if (selected) Fl.Accent else androidx.compose.ui.graphics.Color.Transparent
            )
        )
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

/** Search with an empty field: no article list, only the prompt. */
@Composable
private fun SearchPrompt() {
    Box(Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 48.dp), contentAlignment = Alignment.Center) {
        Text("검색어를 입력하세요", fontSize = 15.sp, color = Fl.Muted)
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
private fun SearchNoResults(query: String) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 44.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("검색 결과가 없어요", fontWeight = FontWeight.SemiBold, color = Fl.Ink)
        Text(
            "‘$query’와 일치하는 기사를 찾지 못했어요. 다른 검색어나 맞춤법을 확인해 보세요.",
            fontSize = 13.sp,
            lineHeight = 20.sp,
            color = Fl.Muted
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
                    label = "구독",
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
            Caption("선택한 언론사는 구독 피드에만 반영되며 기사 분석에는 영향을 주지 않아요.")
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
