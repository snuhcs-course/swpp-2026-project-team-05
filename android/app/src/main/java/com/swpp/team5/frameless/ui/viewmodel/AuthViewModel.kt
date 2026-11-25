package com.swpp.team5.frameless.ui.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.swpp.team5.frameless.data.AppSession
import com.swpp.team5.frameless.data.AuthDataSource
import com.swpp.team5.frameless.data.NewsApiException
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.CancellationException

internal class AuthViewModel(
    private val repository: AuthDataSource,
    private val clock: () -> Long = android.os.SystemClock::elapsedRealtime
) : ViewModel() {
    private var loginUntil = 0L
    private var registrationUntil = 0L
    private var loginWait: Job? = null
    private var registrationWait: Job? = null
    var loginCoolingDown by mutableStateOf(false)
        private set
    var registrationCoolingDown by mutableStateOf(false)
        private set
    var session by mutableStateOf(repository.currentSession())
        private set
    var isRestoring by mutableStateOf(session != null)
        private set
    var isLoading by mutableStateOf(false)
        private set
    var showRegistration by mutableStateOf(false)
        private set
    var showLogin by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var fieldErrors by mutableStateOf<Map<String, String>>(emptyMap())
        private set

    var identifier by mutableStateOf("")
    var password by mutableStateOf("")
    var registrationEmail by mutableStateOf("")
    var registrationUsername by mutableStateOf("")
    var registrationPassword by mutableStateOf("")
    var registrationConfirmation by mutableStateOf("")

    val isLoggedIn: Boolean get() = session != null

    init {
        if (session != null) {
            viewModelScope.launch {
                try {
                    session = repository.restore()
                } catch (failure: NewsApiException) {
                    if (failure.code == "AUTH_REQUIRED" || failure.code == "SECURE_STORAGE_UNAVAILABLE") {
                        session = null
                        error = if (failure.code == "AUTH_REQUIRED")
                            "로그인이 만료됐어요. 다시 로그인해 주세요." else failure.message
                    }
                } finally {
                    isRestoring = false
                }
            }
        }
    }

    fun openRegistration() {
        refreshCooldowns()
        showLogin = true
        showRegistration = true
        error = cooldownMessage(registrationCoolingDown)
        fieldErrors = emptyMap()
    }

    fun closeRegistration() {
        showRegistration = false
        error = cooldownMessage(loginCoolingDown)
        fieldErrors = emptyMap()
    }

    fun openLogin() {
        refreshCooldowns()
        showLogin = true
        showRegistration = false
        error = cooldownMessage(loginCoolingDown)
        fieldErrors = emptyMap()
    }

    fun closeLogin() {
        showLogin = false
        showRegistration = false
        error = null
        fieldErrors = emptyMap()
    }

    fun login() {
        refreshCooldowns()
        if (loginCoolingDown || isLoading) return
        if (identifier.isBlank() || password.isBlank()) {
            error = "이메일 또는 사용자 이름과 비밀번호를 입력해 주세요."
            return
        }
        submit(false) { repository.login(identifier.trim(), password) }
    }

    fun register() {
        refreshCooldowns()
        if (registrationCoolingDown || isLoading) return
        if (registrationEmail.isBlank() || registrationUsername.isBlank() ||
            registrationPassword.isBlank() || registrationConfirmation.isBlank()) {
            error = "모든 항목을 입력해 주세요."
            return
        }
        submit(true) {
            repository.register(
                registrationEmail.trim(), registrationUsername.trim(),
                registrationPassword, registrationConfirmation
            )
        }
    }

    private fun cooldownMessage(blocked: Boolean) = if (blocked) "요청이 많아요. 잠시 후 다시 시도해 주세요." else null

    fun refreshCooldowns() {
        loginCoolingDown = clock() < loginUntil
        registrationCoolingDown = clock() < registrationUntil
    }

    private fun waitAfterLimit(registration: Boolean, seconds: Long?) {
        val wait = (seconds ?: 60L).coerceIn(1L, 3600L) * 1000
        if (registration) {
            registrationUntil = clock() + wait
            registrationWait?.cancel()
            registrationWait = viewModelScope.launch { delay(wait); refreshCooldowns() }
        } else {
            loginUntil = clock() + wait
            loginWait?.cancel()
            loginWait = viewModelScope.launch { delay(wait); refreshCooldowns() }
        }
        refreshCooldowns()
    }

    private fun submit(registration: Boolean, block: suspend () -> AppSession) {
        if (isLoading) return
        isLoading = true
        error = null
        fieldErrors = emptyMap()
        viewModelScope.launch {
            try {
                session = block()
                password = ""
                registrationPassword = ""
                registrationConfirmation = ""
                showRegistration = false
                showLogin = false
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: NewsApiException) {
                if (failure.code == "RATE_LIMITED") waitAfterLimit(registration, failure.retryAfterSeconds)
                error = failure.message
                fieldErrors = failure.fields
            } catch (failure: Exception) {
                error = failure.message ?: "요청을 처리하지 못했어요."
            } finally {
                isLoading = false
            }
        }
    }

    fun logout() {
        if (isLoading) return
        isLoading = true
        viewModelScope.launch {
            try {
                repository.logout()
            } catch (_: Exception) {
                // Local logout must still complete when the server is unreachable.
            } finally {
                session = null
                password = ""
                isLoading = false
            }
        }
    }

    fun sessionExpired() {
        repository.clear()
        session = null
        error = "로그인이 만료됐어요. 다시 로그인해 주세요."
    }
}
