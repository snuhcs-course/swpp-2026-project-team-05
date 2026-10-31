package com.swpp.team5.frameless.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.swpp.team5.frameless.ui.screens.LoginScreen
import com.swpp.team5.frameless.ui.screens.NewsFeedScreen
import com.swpp.team5.frameless.ui.screens.NewsReaderScreen
import com.swpp.team5.frameless.ui.screens.RegistrationScreen
import com.swpp.team5.frameless.ui.theme.Fl
import com.swpp.team5.frameless.ui.viewmodel.AuthViewModel
import com.swpp.team5.frameless.ui.viewmodel.NewsRoute
import com.swpp.team5.frameless.ui.viewmodel.NewsViewModel

/** Normal product navigation: authentication → feed → Reader. */
@Composable
internal fun FrameLESSApp(news: NewsViewModel, auth: AuthViewModel) {
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = Fl.Bg) {
            Box(modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                if (auth.isRestoring) {
                    CircularProgressIndicator(color = Fl.Accent, modifier = Modifier.align(Alignment.Center))
                    return@Box
                }
                if (!auth.isLoggedIn) {
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
                        LoginScreen(
                            identifier = auth.identifier,
                            password = auth.password,
                            loading = auth.isLoading,
                            error = auth.error,
                            onIdentifier = { auth.identifier = it },
                            onPassword = { auth.password = it },
                            onLogin = auth::login,
                            onRegister = auth::openRegistration
                        )
                    }
                    return@Box
                }

                val session = auth.session ?: return@Box
                val feedListState = rememberLazyListState()
                LaunchedEffect(session.token) { news.initialize() }
                LaunchedEffect(news.authExpired) {
                    if (news.authExpired) {
                        news.consumeAuthExpired()
                        news.reset()
                        auth.sessionExpired()
                    }
                }
                when (news.route) {
                    NewsRoute.FEED -> NewsFeedScreen(
                        state = news.feed,
                        session = session,
                        listState = feedListState,
                        onCategory = news::selectCategory,
                        onPublishers = news::applyPublishers,
                        onRefresh = news::refresh,
                        onRetry = news::retryFeed,
                        onLoadMore = news::loadMore,
                        onArticle = news::openArticle,
                        onLogout = { news.reset(); auth.logout() }
                    )
                    NewsRoute.READER -> NewsReaderScreen(
                        state = news.reader,
                        onBack = news::closeReader,
                        onRetry = news::retryReader,
                        onAnalysisEnabled = news::setAnalysisEnabled
                    )
                }
            }
        }
    }
}
