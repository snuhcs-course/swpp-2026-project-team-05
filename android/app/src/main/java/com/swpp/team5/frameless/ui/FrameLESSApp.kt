package com.swpp.team5.frameless.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.swpp.team5.frameless.ui.screens.LoginScreen
import com.swpp.team5.frameless.ui.components.ReadingPresence
import com.swpp.team5.frameless.ui.components.HomePresence
import com.swpp.team5.frameless.ui.screens.ContextualAuthSheet
import com.swpp.team5.frameless.ui.screens.GuestSavedScreen
import com.swpp.team5.frameless.ui.screens.NewsFeedScreen
import com.swpp.team5.frameless.ui.screens.NewsReaderScreen
import com.swpp.team5.frameless.ui.screens.NewsSavedScreen
import com.swpp.team5.frameless.ui.screens.OnboardingScreen
import com.swpp.team5.frameless.ui.screens.PortalBottomBar
import com.swpp.team5.frameless.ui.screens.PortalAnalysisSettingsScreen
import com.swpp.team5.frameless.ui.screens.PortalAppearanceSettingsScreen
import com.swpp.team5.frameless.ui.screens.PortalFollowedPublishersScreen
import com.swpp.team5.frameless.ui.screens.PortalHomeScreen
import com.swpp.team5.frameless.ui.screens.PortalNewsScreen
import com.swpp.team5.frameless.ui.screens.ProfilePage
import com.swpp.team5.frameless.ui.screens.PortalProfileScreen
import com.swpp.team5.frameless.ui.screens.PortalTab
import com.swpp.team5.frameless.ui.screens.ReaderCoachUi
import com.swpp.team5.frameless.ui.screens.RegistrationScreen
import com.swpp.team5.frameless.ui.theme.Fl
import com.swpp.team5.frameless.ui.theme.FrameLESSTheme
import com.swpp.team5.frameless.ui.viewmodel.AuthViewModel
import com.swpp.team5.frameless.ui.viewmodel.AppearanceViewModel
import com.swpp.team5.frameless.ui.viewmodel.NewsRoute
import com.swpp.team5.frameless.ui.viewmodel.NewsViewModel
import com.swpp.team5.frameless.ui.viewmodel.TutorialViewModel

private enum class AuthDestination { SAVED }
private enum class AuthPrompt {
    BOOKMARK, SUBSCRIPTION, REACTION;

    val title: String get() = when (this) {
        BOOKMARK -> "이 기사를 저장할까요?"
        SUBSCRIPTION -> "관심 언론사를 구독할까요?"
        REACTION -> "이 기사에 반응을 남길까요?"
    }
    val detail: String get() = when (this) {
        BOOKMARK -> "로그인하면 저장한 기사와 분석을 여러 기기에서 이어 볼 수 있어요."
        SUBSCRIPTION -> "로그인하면 구독한 언론사의 최신 기사를 홈에서 먼저 볼 수 있어요."
        REACTION -> "로그인하면 기사에 반응을 남기거나 바꿀 수 있어요."
    }
}

/** Normal product navigation: first-run onboarding → guest or authenticated feed → Reader. */
@Composable
internal fun FrameLESSApp(
    news: NewsViewModel,
    auth: AuthViewModel,
    tutorial: TutorialViewModel,
    appearance: AppearanceViewModel
) {
    val systemDark = isSystemInDarkTheme()
    FrameLESSTheme(darkTheme = appearance.mode.usesDarkTheme(systemDark)) {
        Surface(modifier = Modifier.fillMaxSize(), color = Fl.Bg) {
            Box(modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                if (auth.isRestoring) {
                    CircularProgressIndicator(color = Fl.Accent, modifier = Modifier.align(Alignment.Center))
                    return@Box
                }
                val session = auth.session
                var rootTab by rememberSaveable { mutableStateOf(PortalTab.HOME) }
                var profilePage by rememberSaveable { mutableStateOf(ProfilePage.OVERVIEW) }
                var searchReturnTab by rememberSaveable { mutableStateOf<PortalTab?>(null) }
                var authDestination by rememberSaveable { mutableStateOf<AuthDestination?>(null) }
                var authPrompt by rememberSaveable { mutableStateOf<AuthPrompt?>(null) }
                var pendingSubscriptionCode by rememberSaveable { mutableStateOf<String?>(null) }
                var observedSessionToken by remember { mutableStateOf(session?.token) }
                LaunchedEffect(session?.token) {
                    if (observedSessionToken == session?.token) {
                        news.initialize()
                    } else {
                        observedSessionToken = session?.token
                        news.onViewerChanged()
                    }
                    if (session != null) {
                        when (authDestination) {
                            AuthDestination.SAVED -> news.openSaved()
                            null -> Unit
                        }
                        authDestination = null
                    }
                }
                LaunchedEffect(session?.token, news.feed.publishers, pendingSubscriptionCode) {
                    val code = pendingSubscriptionCode ?: return@LaunchedEffect
                    if (session == null || news.feed.publishers.none { it.code == code }) return@LaunchedEffect
                    pendingSubscriptionCode = null
                    news.setPublisherSubscription(code, true)
                }
                if (tutorial.showIntro && !auth.showLogin) {
                    OnboardingScreen(
                        onFinish = tutorial::finishIntro,
                        onBack = tutorial::finishIntro,
                        onLogin = {
                            tutorial.finishIntro()
                            auth.openLogin()
                        },
                        showLogin = session == null
                    )
                    return@Box
                }
                if (auth.showLogin) {
                    val dismissAuth = {
                        authDestination = null
                        pendingSubscriptionCode = null
                        auth.closeLogin()
                    }
                    if (auth.showRegistration) {
                        BackHandler { auth.closeRegistration() }
                        RegistrationScreen(
                            email = auth.registrationEmail,
                            username = auth.registrationUsername,
                            password = auth.registrationPassword,
                            confirmation = auth.registrationConfirmation,
                            loading = auth.isLoading,
                            error = auth.error,
                            fieldErrors = auth.fieldErrors,
                            onEmail = { auth.registrationEmail = it },
                            onUsername = { auth.registrationUsername = it },
                            onPassword = { auth.registrationPassword = it },
                            onConfirmation = { auth.registrationConfirmation = it },
                            onBack = auth::closeRegistration,
                            onSubmit = auth::register
                        )
                    } else {
                        BackHandler { dismissAuth() }
                        LoginScreen(
                            identifier = auth.identifier,
                            password = auth.password,
                            loading = auth.isLoading,
                            error = auth.error,
                            onIdentifier = { auth.identifier = it },
                            onPassword = { auth.password = it },
                            onLogin = auth::login,
                            onRegister = auth::openRegistration,
                            onDismiss = dismissAuth
                        )
                    }
                    return@Box
                }

                val feedListState = rememberLazyListState()
                val newsListStates = com.swpp.team5.frameless.ui.viewmodel.NewsSection.entries.associateWith {
                    androidx.compose.runtime.key(it) { rememberLazyListState() }
                }
                val homeListState = rememberLazyListState()
                val savedListState = rememberLazyListState()
                LaunchedEffect(news.authExpired) {
                    if (news.authExpired) {
                        news.consumeAuthExpired()
                        news.reset()
                        auth.sessionExpired()
                    }
                }
                LaunchedEffect(news.route) { news.recordScreen(news.route) }
                if (news.route == NewsRoute.READER) {
                    val reactionArticle = news.reader.article?.id?.takeUnless { news.reader.isOffline || news.reader.isArticleLoading }
                    LaunchedEffect(reactionArticle, session?.token) { reactionArticle?.let { news.reactions.load(it) } }
                    HomePresence { reactionArticle?.let { news.reactions.load(it) } }
                    ReadingPresence(news.reading,
                        news.reader.article?.id?.takeIf { !news.reader.isOffline && !news.reader.isArticleLoading },
                        session?.user?.id)
                    NewsReaderScreen(
                        state = news.reader,
                        onBack = news::closeReader,
                        onRetry = news::retryReader,
                        onAnalysisEnabled = news::setAnalysisEnabled,
                        onOpenComparisonArticle = news::openComparisonArticle,
                        onOpenRelatedArticle = news::openRelatedArticle,
                        onComparisonShown = news::recordComparisonShown,
                        onToggleSaved = { articleId, saved ->
                            if (session == null) authPrompt = AuthPrompt.BOOKMARK
                            else news.toggleSaved(articleId, saved)
                        },
                        savedPending = news.reader.articleId in news.savedMutationArticleIds,
                        saveError = news.saveErrors[news.reader.articleId],
                        reactionState = news.reactions.state(news.reader.articleId),
                        onReaction = { kind ->
                            if (session == null) authPrompt = AuthPrompt.REACTION
                            else news.reactions.select(news.reader.articleId, kind)
                        },
                        onReactionRetry = { news.reactions.retry(news.reader.articleId) },
                        coach = ReaderCoachUi(
                            step = tutorial.readerStep,
                            onEligible = tutorial::onReaderEligible,
                            onFindingOpened = tutorial::onFindingOpened,
                            onAnnotationNext = tutorial::onAnnotationNext,
                            onFinish = tutorial::finishReaderCoach
                        )
                    )
                    authPrompt?.let { prompt ->
                        ContextualAuthSheet(
                            title = prompt.title,
                            detail = prompt.detail,
                            onDismiss = {
                                authPrompt = null
                                pendingSubscriptionCode = null
                            },
                            onLogin = {
                                authPrompt = null
                                auth.openLogin()
                            },
                            onRegister = {
                                authPrompt = null
                                auth.openLogin()
                                auth.openRegistration()
                            }
                        )
                    }
                    return@Box
                }

                val selectedTab = if (news.route == NewsRoute.SAVED) PortalTab.SAVED else rootTab
                val selectTab: (PortalTab) -> Unit = { target ->
                    when (target) {
                        PortalTab.SAVED -> {
                            if (session == null) {
                                if (news.route == NewsRoute.SAVED) news.closeSaved()
                                rootTab = PortalTab.SAVED
                            } else news.openSaved()
                        }
                        else -> {
                            if (news.route == NewsRoute.SAVED) news.closeSaved()
                            if (news.feed.isSearchOpen && target != PortalTab.NEWS) {
                                news.closeSearch()
                                searchReturnTab = null
                            }
                            if (target == PortalTab.MY) profilePage = ProfilePage.OVERVIEW
                            rootTab = target
                        }
                    }
                }

                val closeSearchToOrigin: () -> Unit = {
                    news.closeSearch()
                    rootTab = searchReturnTab ?: PortalTab.NEWS
                    searchReturnTab = null
                }

                BackHandler(enabled = news.feed.isSearchOpen && selectedTab == PortalTab.NEWS) {
                    closeSearchToOrigin()
                }
                BackHandler(enabled = selectedTab == PortalTab.MY && profilePage != ProfilePage.OVERVIEW) {
                    profilePage = ProfilePage.OVERVIEW
                }
                BackHandler(enabled = selectedTab == PortalTab.NEWS && news.discovery.activePublisher != null) {
                    news.closePublisher()
                }
                BackHandler(
                    enabled = !news.feed.isSearchOpen && selectedTab != PortalTab.HOME &&
                        !(selectedTab == PortalTab.MY && profilePage != ProfilePage.OVERVIEW) &&
                        news.discovery.activePublisher == null
                ) {
                    selectTab(PortalTab.HOME)
                }

                LaunchedEffect(selectedTab) {
                    when (selectedTab) {
                        PortalTab.HOME -> news.ensureHomeLoaded()
                        PortalTab.NEWS -> if (!news.feed.isSearchOpen) news.ensureNewsLoaded()
                        else -> Unit
                    }
                }

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = Fl.Card,
                    bottomBar = {
                        if (selectedTab != PortalTab.MY || profilePage == ProfilePage.OVERVIEW) {
                            PortalBottomBar(selectedTab, selectTab)
                        }
                    }
                ) { innerPadding ->
                    Box(Modifier.fillMaxSize().padding(innerPadding)) {
                        when (selectedTab) {
                            PortalTab.HOME -> {
                                HomePresence(news::ensureHomeLoaded)
                                PortalHomeScreen(
                                stories = news.discovery.homeStories,
                                subscribedPublishers = news.feed.publishers.filter { it.isFollowed },
                                isLoading = news.discovery.isHomeLoading,
                                error = news.discovery.homeError,
                                isOffline = news.discovery.isHomeOffline,
                                listState = homeListState,
                                onSearch = {
                                    searchReturnTab = PortalTab.HOME
                                    rootTab = PortalTab.NEWS
                                    news.openSearch()
                                },
                                onArticle = news::openArticle,
                                onProfile = { selectTab(PortalTab.MY) },
                                onSubscriptions = {
                                    rootTab = PortalTab.MY
                                    profilePage = ProfilePage.SOURCES
                                },
                                onRetry = news::refreshHome,
                                subscribedArticles = news.discovery.subscribedArticles,
                                selectedPublisherCode = news.discovery.selectedHomePublisherCode,
                                onSelectPublisher = news::selectHomePublisher,
                                isSubscribedLoading = news.discovery.isSubscribedLoading,
                                subscribedError = news.discovery.subscribedError,
                                onRetrySubscriptions = news::retrySubscribedHome,
                                isSignedIn = session != null,
                                trends = news.trends.state,
                                highlights = news.headlines.state,
                                onRetryTrends = { news.trends.load(force = true) },
                                isRefreshing = news.discovery.isHomeRefreshing ||
                                    (news.trends.state.loading && news.trends.state.page != null)
                                )
                            }
                            PortalTab.NEWS -> if (news.feed.isSearchOpen) NewsFeedScreen(
                                state = news.feed,
                                session = session,
                                listState = feedListState,
                                onCategory = news::selectCategory,
                                onSearchFilter = news::selectSearchFilter,
                                onScope = { scope ->
                                    if (scope == com.swpp.team5.frameless.data.FeedScope.FOLLOWING && session == null) {
                                        auth.openLogin()
                                    } else news.selectScope(scope)
                                },
                                onSources = { codes, onSuccess ->
                                    if (session == null) auth.openLogin()
                                    else news.replaceFollowedPublishers(codes, onSuccess)
                                },
                                onRefresh = news::refresh,
                                onRetry = news::retryFeed,
                                onLoadMore = news::loadMore,
                                onLoadMoreSearch = news::loadMoreSearch,
                                onRetrySearch = news::retrySearch,
                                onSearchOpen = {
                                    searchReturnTab = PortalTab.NEWS
                                    news.openSearch()
                                },
                                onSearchInput = news::updateSearchInput,
                                onSearchSubmit = news::submitSearch,
                                onSearchClose = closeSearchToOrigin,
                                onSavedOpen = { selectTab(PortalTab.SAVED) },
                                onArticle = news::openArticle,
                                onLogin = auth::openLogin,
                                onLogout = {
                                    session?.let { current -> news.logout(current.user.id, auth::logout) }
                                },
                                onTutorialOpen = tutorial::replay,
                                rootShell = true
                            ) else PortalNewsScreen(
                                state = news.discovery,
                                publishers = news.feed.publishers,
                                session = session,
                                pendingSubscriptionCodes = news.feed.sourceMutationPublisherCodes,
                                subscriptionErrors = news.feed.sourceSaveErrors,
                                isLoadingPublishers = news.feed.isLoadingPublishers,
                                publisherDirectoryError = news.feed.publisherDirectoryError,
                                onPublisherDirectoryRetry = news::retryFeed,
                                listState = newsListStates.getValue(news.discovery.newsSection),
                                onSection = news::selectNewsSection,
                                onSearch = {
                                    searchReturnTab = PortalTab.NEWS
                                    news.openSearch()
                                },
                                onTutorial = tutorial::replay,
                                onRefresh = news::refreshNews,
                                onRetry = news::ensureNewsLoaded,
                                onLoadMore = news::loadMoreNews,
                                onArticle = news::openArticle,
                                onPublisher = news::openPublisher,
                                onPublisherBack = news::closePublisher,
                                onPublisherRetry = news::retryPublisher,
                                onPublisherLoadMore = news::loadMorePublisher,
                                onSubscription = { publisher, follow ->
                                    if (session == null) {
                                        pendingSubscriptionCode = publisher.code
                                        authPrompt = AuthPrompt.SUBSCRIPTION
                                    } else {
                                        news.setPublisherSubscription(publisher.code, follow)
                                    }
                                }
                            )
                            PortalTab.SAVED -> if (session == null) GuestSavedScreen(
                                onLogin = {
                                    authDestination = AuthDestination.SAVED
                                    auth.openLogin()
                                },
                                onRegister = {
                                    authDestination = AuthDestination.SAVED
                                    auth.openLogin()
                                    auth.openRegistration()
                                },
                                onBrowse = { selectTab(PortalTab.NEWS) }
                            ) else NewsSavedScreen(
                                state = news.saved,
                                listState = savedListState,
                                onBack = { selectTab(PortalTab.NEWS) },
                                onRefresh = news::refreshSaved,
                                onRetry = news::retrySaved,
                                onLoadMore = news::loadMoreSaved,
                                onArticle = news::openArticle,
                                rootShell = true
                            )
                            PortalTab.MY -> when (profilePage) {
                                ProfilePage.OVERVIEW -> PortalProfileScreen(
                                    session = session,
                                    analysisEnabled = news.reader.analysisEnabled,
                                    followedCount = news.feed.publishers.count { it.isFollowed },
                                    onLogin = auth::openLogin,
                                    onTutorial = tutorial::replay,
                                    onLogout = {
                                        session?.let { current -> news.logout(current.user.id, auth::logout) }
                                    },
                                    onSources = { profilePage = ProfilePage.SOURCES },
                                    onAnalysis = { profilePage = ProfilePage.ANALYSIS },
                                    appearanceMode = appearance.mode,
                                    onAppearance = { profilePage = ProfilePage.APPEARANCE }
                                )
                                ProfilePage.SOURCES -> PortalFollowedPublishersScreen(
                                    publishers = news.feed.publishers,
                                    onBack = { profilePage = ProfilePage.OVERVIEW },
                                    isGuest = session == null,
                                    pendingPublisherCodes = news.feed.sourceMutationPublisherCodes,
                                    errors = news.feed.sourceSaveErrors,
                                    onToggle = { publisher, follow ->
                                        if (session == null) {
                                            pendingSubscriptionCode = publisher.code
                                            authPrompt = AuthPrompt.SUBSCRIPTION
                                        } else {
                                            news.setPublisherSubscription(publisher.code, follow)
                                        }
                                    }
                                )
                                ProfilePage.ANALYSIS -> PortalAnalysisSettingsScreen(
                                    enabled = news.reader.analysisEnabled,
                                    onEnabled = news::setAnalysisEnabled,
                                    onBack = { profilePage = ProfilePage.OVERVIEW }
                                )
                                ProfilePage.APPEARANCE -> PortalAppearanceSettingsScreen(
                                    mode = appearance.mode,
                                    onMode = appearance::select,
                                    onBack = { profilePage = ProfilePage.OVERVIEW }
                                )
                            }
                        }
                    }
                }
                authPrompt?.let { prompt ->
                    ContextualAuthSheet(
                        title = prompt.title,
                        detail = prompt.detail,
                        onDismiss = {
                            authPrompt = null
                            pendingSubscriptionCode = null
                        },
                        onLogin = {
                            authPrompt = null
                            auth.openLogin()
                        },
                        onRegister = {
                            authPrompt = null
                            auth.openLogin()
                            auth.openRegistration()
                        }
                    )
                }
            }
        }
    }
}
