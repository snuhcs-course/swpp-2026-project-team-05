package com.swpp.team5.frameless.ui.viewmodel

import android.os.Looper
import com.swpp.team5.frameless.data.AppSession
import com.swpp.team5.frameless.data.AuthDataSource
import com.swpp.team5.frameless.data.NewsApiException
import com.swpp.team5.frameless.data.NewsUser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@LooperMode(LooperMode.Mode.PAUSED)
class AuthViewModelTest {
    @Test
    fun rateLimitWaitIsBoundedSeparatePerFormAndNeverReplaysCredentials() {
        var now = 0L
        val repository = FakeAuthDataSource().apply {
            loginFailure = NewsApiException("RATE_LIMITED", "limited", retryAfterSeconds = 2)
        }
        val vm = AuthViewModel(repository) { now }
        vm.openLogin()
        vm.identifier = "reader"
        vm.password = "private-password"
        vm.login()
        mainLooper.idle()
        assertTrue(vm.loginCoolingDown)
        assertFalse(vm.isLoading)
        vm.login()
        assertEquals(1, repository.loginCalls)
        assertEquals("private-password", vm.password)
        vm.closeLogin()
        vm.openRegistration()
        assertFalse(vm.registrationCoolingDown)
        vm.closeRegistration()
        assertTrue(vm.loginCoolingDown)
        now = 2000
        mainLooper.idleFor(2, java.util.concurrent.TimeUnit.SECONDS)
        assertFalse(vm.loginCoolingDown)
        assertEquals(1, repository.loginCalls)
        repository.loginFailure = NewsApiException("AUTH_UNAVAILABLE", "try later")
        vm.login()
        mainLooper.idle()
        assertFalse(vm.loginCoolingDown)
        assertFalse(vm.isLoading)
        assertEquals("private-password", vm.password)
    }

    @Test
    fun cooldownRefreshHandlesSleepAndErrorsNeverClearAnExistingSession() {
        var now = 1000L
        val repository = FakeAuthDataSource().apply {
            current = session
            loginFailure = NewsApiException("RATE_LIMITED", "limited", retryAfterSeconds = Long.MAX_VALUE)
        }
        val vm = AuthViewModel(repository) { now }
        mainLooper.idle()
        vm.identifier = "reader"; vm.password = "password"
        vm.login(); mainLooper.idle()
        assertEquals(session, vm.session)
        assertTrue(vm.loginCoolingDown)
        now += 3_600_000
        vm.refreshCooldowns()
        assertFalse(vm.loginCoolingDown)
        assertEquals(1, repository.loginCalls)
        repository.loginFailure = NewsApiException("AUTH_UNAVAILABLE", "try later")
        vm.login(); mainLooper.idle()
        assertEquals(session, vm.session)
        assertFalse(vm.isLoading)
        assertFalse(vm.loginCoolingDown)
    }

    @Test
    fun registrationCooldownPreservesTheDraftAndDoesNotBlockGuestOrLoginNavigation() {
        var now = 0L
        val repository = FakeAuthDataSource().apply {
            registerFailure = NewsApiException("RATE_LIMITED", "limited", retryAfterSeconds = -1)
        }
        val vm = AuthViewModel(repository) { now }
        vm.openRegistration()
        vm.registrationEmail = "reader@example.com"; vm.registrationUsername = "reader"
        vm.registrationPassword = "private-password"; vm.registrationConfirmation = "private-password"
        vm.register(); mainLooper.idle()
        assertTrue(vm.registrationCoolingDown)
        assertEquals("reader@example.com", vm.registrationEmail)
        vm.register()
        assertEquals(1, repository.registerCalls)
        vm.closeRegistration()
        assertFalse(vm.loginCoolingDown)
        vm.closeLogin()
        assertFalse(vm.showLogin)
        now = 1000L
        vm.refreshCooldowns()
        assertFalse(vm.registrationCoolingDown)
        assertEquals(1, repository.registerCalls)
    }
    private val mainLooper get() = shadowOf(Looper.getMainLooper())
    private val session = AppSession(
        NewsUser("user-1", "reader@example.com", "reader", false),
        "token", "2099-11-30T00:00:00+09:00"
    )

    @Test
    fun cleanInstallStartsAsGuestWithoutOpeningLogin() {
        val viewModel = AuthViewModel(FakeAuthDataSource())

        assertNull(viewModel.session)
        assertFalse(viewModel.isRestoring)
        assertFalse(viewModel.showLogin)
    }

    @Test
    fun blankLoginIsRejectedAndSuccessfulLoginClearsPassword() {
        val repository = FakeAuthDataSource()
        val viewModel = AuthViewModel(repository)
        viewModel.login()
        assertTrue(viewModel.error.orEmpty().contains("비밀번호"))
        assertEquals(0, repository.loginCalls)

        viewModel.identifier = "  reader  "
        viewModel.password = "password"
        viewModel.login()
        mainLooper.idle()

        assertEquals("reader", repository.lastIdentifier)
        assertEquals(session, viewModel.session)
        assertEquals("", viewModel.password)
        assertFalse(viewModel.isLoading)
    }

    @Test
    fun registrationValidatesFieldsAndSurfacesServerFieldErrors() {
        val repository = FakeAuthDataSource()
        val viewModel = AuthViewModel(repository)
        viewModel.openRegistration()
        assertTrue(viewModel.showRegistration)
        viewModel.register()
        assertEquals("모든 항목을 입력해 주세요.", viewModel.error)

        repository.registerFailure = NewsApiException(
            "VALIDATION_ERROR", "입력 내용을 확인해 주세요.", mapOf("email" to "이미 사용 중입니다.")
        )
        viewModel.registrationEmail = " reader@example.com "
        viewModel.registrationUsername = " reader2 "
        viewModel.registrationPassword = "password"
        viewModel.registrationConfirmation = "password"
        viewModel.register()
        mainLooper.idle()
        assertEquals("이미 사용 중입니다.", viewModel.fieldErrors["email"])
        assertTrue(viewModel.showRegistration)

        repository.registerFailure = null
        viewModel.register()
        mainLooper.idle()
        assertEquals(session, viewModel.session)
        assertFalse(viewModel.showRegistration)
        assertEquals("", viewModel.registrationPassword)
        assertEquals("", viewModel.registrationConfirmation)
    }

    @Test
    fun savedSessionRestoresAndExpiredRestoreReturnsToLogin() {
        val repository = FakeAuthDataSource().apply { current = session }
        val restored = AuthViewModel(repository)
        mainLooper.idle()
        assertEquals(session, restored.session)
        assertFalse(restored.isRestoring)

        val expiredRepository = FakeAuthDataSource().apply {
            current = session
            restoreFailure = NewsApiException("AUTH_REQUIRED", "expired")
        }
        val expired = AuthViewModel(expiredRepository)
        mainLooper.idle()
        assertNull(expired.session)
        assertTrue(expired.error.orEmpty().contains("만료"))
    }

    @Test
    fun temporaryRestoreFailureKeepsTheUnexpiredLocalSession() {
        val repository = FakeAuthDataSource().apply {
            current = session
            restoreFailure = NewsApiException("NETWORK_ERROR", "offline")
        }

        val viewModel = AuthViewModel(repository)
        mainLooper.idle()

        assertEquals(session, viewModel.session)
        assertFalse(viewModel.isRestoring)
    }

    @Test
    fun genericFailureLogoutAndSessionExpiredAreHandledConservatively() {
        val repository = FakeAuthDataSource().apply {
            loginFailure = IllegalStateException()
        }
        val viewModel = AuthViewModel(repository)
        viewModel.identifier = "reader"
        viewModel.password = "password"
        viewModel.login()
        mainLooper.idle()
        assertEquals("요청을 처리하지 못했어요.", viewModel.error)

        repository.loginFailure = null
        viewModel.login()
        mainLooper.idle()
        viewModel.logout()
        mainLooper.idle()
        assertNull(viewModel.session)
        assertEquals(1, repository.logoutCalls)

        viewModel.sessionExpired()
        assertEquals(1, repository.clearCalls)
        assertTrue(viewModel.error.orEmpty().contains("만료"))
        viewModel.closeRegistration()
        assertFalse(viewModel.showRegistration)
        assertTrue(viewModel.fieldErrors.isEmpty())
    }

    private inner class FakeAuthDataSource : AuthDataSource {
        var current: AppSession? = null
        var loginFailure: Exception? = null
        var registerFailure: Exception? = null
        var restoreFailure: Exception? = null
        var loginCalls = 0
        var registerCalls = 0
        var logoutCalls = 0
        var clearCalls = 0
        var lastIdentifier: String? = null

        override fun currentSession() = current
        override suspend fun login(identifier: String, password: String): AppSession {
            loginCalls += 1
            lastIdentifier = identifier
            loginFailure?.let { throw it }
            current = session
            return session
        }
        override suspend fun register(email: String, username: String, password: String,
                                      confirmation: String): AppSession {
            registerCalls++
            registerFailure?.let { throw it }
            current = session
            return session
        }
        override suspend fun restore(): AppSession? {
            restoreFailure?.let { throw it }
            return current
        }
        override suspend fun logout() {
            logoutCalls += 1
            current = null
        }
        override fun clear() {
            clearCalls += 1
            current = null
        }
    }
}
