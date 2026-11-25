package com.swpp.team5.frameless.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swpp.team5.frameless.R
import com.swpp.team5.frameless.ui.components.PublisherLogo
import com.swpp.team5.frameless.data.AppSession
import com.swpp.team5.frameless.data.ArticleSummary
import com.swpp.team5.frameless.data.PublisherItem
import com.swpp.team5.frameless.data.StorySummary
import com.swpp.team5.frameless.ui.theme.Fl
import com.swpp.team5.frameless.ui.viewmodel.NewsSection
import com.swpp.team5.frameless.ui.viewmodel.PortalDiscoveryState

private val PortalCardShape = RoundedCornerShape(15.dp)

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun PortalNewsScreen(
    state: PortalDiscoveryState,
    publishers: List<PublisherItem>,
    session: AppSession?,
    listState: LazyListState,
    onSection: (NewsSection) -> Unit,
    onSearch: () -> Unit,
    onTutorial: () -> Unit,
    onRefresh: () -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    onArticle: (String) -> Unit,
    onPublisher: (PublisherItem) -> Unit,
    onPublisherBack: () -> Unit,
    onPublisherRetry: () -> Unit,
    onPublisherLoadMore: () -> Unit,
    onSubscription: (PublisherItem, Boolean) -> Unit,
    pendingSubscriptionCodes: Set<String> = emptySet(),
    subscriptionErrors: Map<String, String> = emptyMap(),
    isLoadingPublishers: Boolean = false,
    publisherDirectoryError: String? = null,
    onPublisherDirectoryRetry: () -> Unit = onRetry
) {
    state.activePublisher?.let { publisher ->
        PublisherPage(
            publisher = publisher,
            articles = state.publisherArticles,
            hasMore = state.publisherNextCursor != null,
            isLoading = state.isPublisherLoading,
            error = state.publisherError,
            onBack = onPublisherBack,
            onArticle = onArticle,
            onRetry = onPublisherRetry,
            onLoadMore = onPublisherLoadMore,
            onSubscription = { onSubscription(publisher, !publisher.isFollowed) },
            isSavingSubscription = publisher.code in pendingSubscriptionCodes,
            subscriptionError = subscriptionErrors[publisher.code]
        )
        return
    }

    Column(Modifier.fillMaxSize().background(Fl.Bg)) {
        PortalNewsHeader(onSearch)
        NewsSectionTabs(state.newsSection, onSection)
        HorizontalDivider(color = Fl.Line)
        when (state.newsSection) {
            NewsSection.PUBLISHERS -> PublisherDirectory(
                publishers = publishers,
                onPublisher = onPublisher,
                onArticle = onArticle,
                onSubscription = onSubscription,
                pendingSubscriptionCodes = pendingSubscriptionCodes,
                subscriptionErrors = subscriptionErrors,
                isLoading = isLoadingPublishers,
                error = publisherDirectoryError,
                onRetry = onPublisherDirectoryRetry
            )
            else -> StoryFeed(
                state = state,
                listState = listState,
                onRefresh = onRefresh,
                onRetry = onRetry,
                onLoadMore = onLoadMore,
                onArticle = onArticle,
                onTutorial = if (state.newsSection == NewsSection.NEWS) onTutorial else null
            )
        }
    }
}

@Composable
private fun PortalNewsHeader(onSearch: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(Fl.Card)
            .padding(start = 18.dp, end = 10.dp, top = 13.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("뉴스", fontSize = 25.sp, fontWeight = FontWeight.ExtraBold, color = Fl.Ink)
        Spacer(Modifier.weight(1f))
        Box(
            Modifier.size(42.dp).clickable(onClick = onSearch)
                .semantics { role = Role.Button; contentDescription = "기사 검색" },
            contentAlignment = Alignment.Center
        ) { SearchGlyphSimple() }
    }
}

@Composable
private fun SearchGlyphSimple() {
    Icon(painterResource(R.drawable.ic_portal_search), contentDescription = null,
        tint = Fl.Ink, modifier = Modifier.size(22.dp))
}

@Composable
private fun NewsSectionTabs(selected: NewsSection, onSection: (NewsSection) -> Unit) {
    LazyRow(
        modifier = Modifier.fillMaxWidth().background(Fl.Card).selectableGroup(),
        contentPadding = PaddingValues(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        items(NewsSection.entries, key = NewsSection::name) { section ->
            val active = section == selected
            Column(
                Modifier.height(50.dp)
                    .selectable(selected = active, role = Role.Tab, onClick = { onSection(section) })
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .semantics { contentDescription = "${section.label} 탭" },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    section.label,
                    fontSize = 15.sp,
                    fontWeight = if (active) FontWeight.ExtraBold else FontWeight.SemiBold,
                    color = if (active) Fl.Ink else Fl.Muted
                )
                Spacer(Modifier.height(7.dp))
                Box(
                    Modifier.width(if (active) 24.dp else 1.dp).height(2.dp)
                        .background(if (active) Fl.Ink else androidx.compose.ui.graphics.Color.Transparent)
                )
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun StoryFeed(
    state: PortalDiscoveryState,
    listState: LazyListState,
    onRefresh: () -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    onArticle: (String) -> Unit,
    onTutorial: (() -> Unit)?
) {
    val pullState = rememberPullToRefreshState()
    LaunchedEffect(listState.firstVisibleItemIndex, state.newsStories.size, state.newsNextCursor) {
        if (state.newsStories.isNotEmpty() && listState.firstVisibleItemIndex >= state.newsStories.lastIndex - 4) {
            onLoadMore()
        }
    }
    when {
        state.isNewsLoading && state.newsStories.isEmpty() -> PortalLoading()
        state.newsError != null && state.newsStories.isEmpty() -> PortalEmpty(
            title = "뉴스를 불러오지 못했어요",
            detail = state.newsError,
            action = "다시 시도",
            onAction = onRetry
        )
        state.newsStories.isEmpty() -> PortalEmpty("표시할 뉴스가 없어요", "다른 분야의 뉴스를 둘러보세요")
        else -> PullToRefreshBox(
            isRefreshing = state.isNewsRefreshing,
            onRefresh = onRefresh,
            state = pullState,
            modifier = Modifier.fillMaxSize(),
            indicator = {
                PullToRefreshDefaults.Indicator(
                    state = pullState,
                    isRefreshing = state.isNewsRefreshing,
                    modifier = Modifier.align(Alignment.TopCenter),
                    containerColor = Fl.Card,
                    color = Fl.Accent
                )
            }
        ) {
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                state.newsError?.let { message ->
                    item { Text(message, color = Fl.Muted, fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp)) }
                }
                if (onTutorial != null) {
                    item { AnalysisGuideCard(onTutorial) }
                }
                items(state.newsStories, key = StorySummary::storyKey) { story ->
                    StoryRow(story) { onArticle(story.representativeArticle.id) }
                }
                if (state.isLoadingMore) item { PortalInlineLoading() }
                state.nextPageError?.let { error ->
                    item { PortalInlineRetry(error, onLoadMore) }
                }
                item { Spacer(Modifier.height(18.dp)) }
            }
        }
    }
}

@Composable
private fun AnalysisGuideCard(onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)
            .background(Fl.Inset, PortalCardShape).clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(32.dp).background(Fl.Accent.copy(alpha = 0.14f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(painterResource(R.drawable.ic_portal_book_open), contentDescription = null,
                tint = Fl.Accent, modifier = Modifier.size(19.dp))
        }
        Column(Modifier.padding(start = 11.dp).weight(1f)) {
            Text("FrameLESS로 기사 읽기", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = Fl.Ink)
            Text("표현을 누르면 다른 언론사의 보도와 비교할 수 있어요", fontSize = 10.5.sp, color = Fl.Muted)
        }
        Icon(painterResource(R.drawable.ic_portal_chevron_right), contentDescription = null,
            tint = Fl.Muted, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun StoryRow(story: StorySummary, onClick: () -> Unit) {
    val article = story.representativeArticle
    Column(
        Modifier.fillMaxWidth().background(Fl.Card).clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 15.dp)
    ) {
        Text(
            article.title,
            fontSize = 16.sp,
            lineHeight = 22.sp,
            fontWeight = FontWeight.SemiBold,
            color = Fl.Ink,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(article.publisher.name, fontSize = 10.5.sp, color = Fl.Muted)
            if (story.publisherCount > 1) {
                Spacer(Modifier.width(7.dp))
                Text(
                    "${story.publisherCount}개 언론사",
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = Fl.Accent,
                    modifier = Modifier.background(Fl.Accent.copy(alpha = 0.11f), RoundedCornerShape(20.dp))
                        .padding(horizontal = 7.dp, vertical = 3.dp)
                )
            }
        }
    }
    HorizontalDivider(color = Fl.Line, modifier = Modifier.padding(horizontal = 18.dp))
}

@Composable
private fun PublisherDirectory(
    publishers: List<PublisherItem>,
    onPublisher: (PublisherItem) -> Unit,
    onArticle: (String) -> Unit,
    onSubscription: (PublisherItem, Boolean) -> Unit,
    pendingSubscriptionCodes: Set<String>,
    subscriptionErrors: Map<String, String>,
    isLoading: Boolean,
    error: String?,
    onRetry: () -> Unit
) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 20.dp)) {
        item { Spacer(Modifier.height(10.dp).background(Fl.Inset).fillMaxWidth()) }
        if (publishers.isEmpty()) {
            when {
                isLoading -> item { PortalInlineLoading() }
                error != null -> item { PortalEmpty("언론사를 불러오지 못했어요", error, "다시 시도", onRetry) }
                else -> item { PortalEmpty("표시할 언론사가 없어요", "새 기사가 들어오면 이곳에서 볼 수 있어요") }
            }
        } else {
            items(publishers, key = PublisherItem::code) { publisher ->
                PublisherBlock(publisher, onPublisher, onArticle, onSubscription,
                    publisher.code in pendingSubscriptionCodes, subscriptionErrors[publisher.code])
            }
        }
    }
}

@Composable
private fun PublisherBlock(
    publisher: PublisherItem,
    onPublisher: (PublisherItem) -> Unit,
    onArticle: (String) -> Unit,
    onSubscription: (PublisherItem, Boolean) -> Unit,
    isSavingSubscription: Boolean,
    subscriptionError: String?
) {
    Column(
        Modifier.fillMaxWidth().background(Fl.Card)
            .padding(horizontal = 20.dp, vertical = 20.dp)
            .testTag("publisher-block-${publisher.code}")
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(
                modifier = Modifier.weight(1f).heightIn(min = 48.dp).clickable { onPublisher(publisher) }
                    .semantics { role = Role.Button; contentDescription = "${publisher.name} 기사 보기" },
                verticalAlignment = Alignment.CenterVertically
            ) {
                PublisherLogo(publisher, Modifier.size(40.dp))
                Column(Modifier.padding(start = 13.dp).weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            publisher.name,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = Fl.Ink,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Icon(
                            painterResource(R.drawable.ic_portal_chevron_right),
                            contentDescription = null,
                            tint = Fl.Muted,
                            modifier = Modifier.padding(start = 3.dp).size(14.dp)
                        )
                    }
                }
            }
            Spacer(Modifier.width(12.dp))
            SubscriptionButton(
                followed = publisher.isFollowed,
                onClick = { onSubscription(publisher, !publisher.isFollowed) },
                pending = isSavingSubscription,
                publisherCode = publisher.code
            )
        }
        subscriptionError?.let { message ->
            Text(message, color = Fl.Danger, fontSize = 12.sp,
                modifier = Modifier.padding(top = 8.dp).testTag("subscription-error-${publisher.code}"))
        }
        Spacer(Modifier.height(14.dp))
        val previews = publisher.recentArticles.take(3)
        if (previews.isEmpty()) {
            Text(
                "최근 기사를 준비하고 있어요",
                fontSize = 12.sp,
                color = Fl.Muted,
                modifier = Modifier.padding(top = 5.dp, bottom = 5.dp)
            )
        } else {
            previews.forEachIndexed { index, article ->
                Text(
                    article.title,
                    fontSize = 15.5.sp,
                    lineHeight = 23.sp,
                    color = Fl.Ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth().clickable { onArticle(article.id) }
                        .padding(vertical = 12.dp)
                )
                if (index < previews.lastIndex) HorizontalDivider(color = Fl.Line.copy(alpha = 0.65f))
            }
        }
    }
    Spacer(Modifier.fillMaxWidth().height(10.dp).background(Fl.Inset))
}

@Composable
private fun SubscriptionButton(followed: Boolean, onClick: () -> Unit, pending: Boolean, publisherCode: String) {
    OutlinedButton(
        onClick = onClick,
        enabled = !pending,
        modifier = Modifier.height(38.dp).widthIn(min = 88.dp).testTag("subscription-button-$publisherCode")
            .semantics { contentDescription = if (followed) "구독 취소" else "구독" },
        shape = RoundedCornerShape(24.dp),
        contentPadding = PaddingValues(horizontal = 13.dp, vertical = 0.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Fl.Line),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = if (followed) Fl.Inset else androidx.compose.ui.graphics.Color.Transparent,
            contentColor = if (followed) Fl.Muted else Fl.Ink
        )
    ) {
        if (pending) CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 1.5.dp, color = Fl.Muted)
        else Icon(painterResource(if (followed) R.drawable.ic_portal_check else R.drawable.ic_portal_plus),
            contentDescription = null, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(4.dp))
        Text(if (followed) "구독 중" else "구독", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun PublisherPage(
    publisher: PublisherItem,
    articles: List<ArticleSummary>,
    hasMore: Boolean,
    isLoading: Boolean,
    error: String?,
    onBack: () -> Unit,
    onArticle: (String) -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    onSubscription: () -> Unit,
    isSavingSubscription: Boolean,
    subscriptionError: String?
) {
    LazyColumn(Modifier.fillMaxSize().background(Fl.Bg)) {
        item {
            Row(
                Modifier.fillMaxWidth().background(Fl.Card).padding(horizontal = 10.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                androidx.compose.material3.IconButton(onClick = onBack) {
                    Icon(painterResource(R.drawable.ic_portal_arrow_back),
                        contentDescription = "언론사 목록으로 돌아가기", tint = Fl.Ink)
                }
                Text("언론사", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Fl.Ink)
            }
        }
        item {
            Row(
                Modifier.fillMaxWidth().background(Fl.Card).padding(horizontal = 18.dp, vertical = 19.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PublisherLogo(publisher)
                Column(Modifier.padding(start = 12.dp).weight(1f)) {
                    Text(publisher.name, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = Fl.Ink)
                }
                SubscriptionButton(publisher.isFollowed, onSubscription, isSavingSubscription, publisher.code)
            }
        }
        subscriptionError?.let { message ->
            item { Text(message, color = Fl.Danger, fontSize = 12.sp, modifier = Modifier.padding(18.dp)) }
        }
        when {
            isLoading && articles.isEmpty() -> item { PortalInlineLoading() }
            error != null && articles.isEmpty() -> item { PortalEmpty("기사를 불러오지 못했어요", error, "다시 시도", onRetry) }
            articles.isEmpty() -> item { PortalEmpty("표시할 기사가 없어요", "새 기사가 들어오면 이곳에 표시됩니다") }
            else -> {
                items(articles, key = ArticleSummary::id) { article ->
                    Column(
                        Modifier.fillMaxWidth().background(Fl.Card).clickable { onArticle(article.id) }
                            .padding(horizontal = 18.dp, vertical = 15.dp)
                    ) {
                        Text(article.title, fontSize = 15.sp, lineHeight = 21.sp, fontWeight = FontWeight.SemiBold, color = Fl.Ink)
                        Text(
                            article.category.label,
                            fontSize = 10.5.sp,
                            color = Fl.Muted,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }
                    HorizontalDivider(color = Fl.Line, modifier = Modifier.padding(horizontal = 18.dp))
                }
                if (isLoading) item { PortalInlineLoading() }
                else if (hasMore) item {
                    Text(
                        "기사 더 보기",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Fl.Accent,
                        modifier = Modifier.fillMaxWidth().clickable(onClick = onLoadMore).padding(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
internal fun GuestSavedScreen(onLogin: () -> Unit, onRegister: () -> Unit, onBrowse: () -> Unit) {
    Column(Modifier.fillMaxSize().background(Fl.Bg)) {
        Text(
            "저장",
            fontSize = 25.sp,
            fontWeight = FontWeight.ExtraBold,
            color = Fl.Ink,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 17.dp)
        )
        HorizontalDivider(color = Fl.Line)
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 28.dp)
                .background(Fl.Card, RoundedCornerShape(18.dp)).border(1.dp, Fl.Line, RoundedCornerShape(18.dp))
                .padding(horizontal = 22.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("저장한 기사를 한곳에서", fontSize = 19.sp, fontWeight = FontWeight.ExtraBold, color = Fl.Ink)
            Text(
                "로그인하면 저장 목록을 여러 기기에서 이어 보고, 오프라인에서도 기사를 읽을 수 있어요.",
                fontSize = 12.5.sp,
                lineHeight = 19.sp,
                color = Fl.Muted,
                modifier = Modifier.padding(top = 8.dp, bottom = 20.dp)
            )
            Button(
                onClick = onLogin,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Fl.Ink, contentColor = Fl.Card),
                shape = RoundedCornerShape(13.dp)
            ) { Text("로그인", fontWeight = FontWeight.Bold) }
            OutlinedButton(
                onClick = onRegister,
                modifier = Modifier.fillMaxWidth().height(48.dp).padding(top = 8.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Fl.Ink),
                shape = RoundedCornerShape(13.dp)
            ) { Text("회원가입", fontWeight = FontWeight.Bold) }
            Text(
                "로그인 없이 기사 둘러보기",
                color = Fl.Muted,
                fontSize = 11.5.sp,
                modifier = Modifier.clickable(onClick = onBrowse).padding(14.dp)
            )
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun ContextualAuthSheet(
    title: String,
    detail: String,
    onDismiss: () -> Unit,
    onLogin: () -> Unit,
    onRegister: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Fl.Card) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 6.dp)) {
            Text(title, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = Fl.Ink)
            Text(detail, fontSize = 12.5.sp, lineHeight = 19.sp, color = Fl.Muted, modifier = Modifier.padding(top = 7.dp))
            Button(
                onClick = onLogin,
                modifier = Modifier.fillMaxWidth().height(48.dp).padding(top = 18.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Fl.Ink, contentColor = Fl.Card),
                shape = RoundedCornerShape(13.dp)
            ) { Text("로그인", fontWeight = FontWeight.Bold) }
            OutlinedButton(
                onClick = onRegister,
                modifier = Modifier.fillMaxWidth().height(48.dp).padding(top = 8.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Fl.Ink),
                shape = RoundedCornerShape(13.dp)
            ) { Text("회원가입", fontWeight = FontWeight.Bold) }
            Spacer(Modifier.height(18.dp))
        }
    }
}

@Composable
private fun PortalLoading() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = Fl.Accent, strokeWidth = 2.dp, modifier = Modifier.size(28.dp))
    }
}

@Composable
private fun PortalInlineLoading() {
    Box(Modifier.fillMaxWidth().padding(22.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = Fl.Accent, strokeWidth = 2.dp, modifier = Modifier.size(23.dp))
    }
}

@Composable
private fun PortalInlineRetry(message: String, onRetry: () -> Unit) {
    Text(
        "$message · 다시 시도",
        fontSize = 11.5.sp,
        color = Fl.Accent,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onRetry).padding(18.dp)
    )
}

@Composable
private fun PortalEmpty(
    title: String,
    detail: String,
    action: String? = null,
    onAction: (() -> Unit)? = null
) {
    Column(
        Modifier.fillMaxWidth().padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Fl.Ink)
        Text(detail, fontSize = 11.5.sp, lineHeight = 18.sp, color = Fl.Muted, modifier = Modifier.padding(top = 6.dp))
        if (action != null && onAction != null) {
            Text(
                action,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = Fl.Accent,
                modifier = Modifier.clickable(onClick = onAction).padding(14.dp)
            )
        }
    }
}
