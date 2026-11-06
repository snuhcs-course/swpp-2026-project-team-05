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

internal class AuthViewModel(private val repository: AuthDataSource) : ViewModel() {
    var session by mutableStateOf(repository.currentSession())
        private set
    var isRestoring by mutableStateOf(session != null)
        private set
    var isLoading by mutableStateOf(false)
        private set
    var showRegistration by mutableStateOf(false)
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
                    if (failure.code == "AUTH_REQUIRED") {
                        session = null
                        error = "로그인이 만료됐어요. 다시 로그인해 주세요."
                    }
                } finally {
                    isRestoring = false
                }
            }
        }
    }

    fun openRegistration() {
        showRegistration = true
        error = null
        fieldErrors = emptyMap()
    }

    fun closeRegistration() {
        showRegistration = false
        error = null
        fieldErrors = emptyMap()
    }

    fun login() {
        if (identifier.isBlank() || password.isBlank()) {
            error = "이메일 또는 사용자 이름과 비밀번호를 입력해 주세요."
            return
        }
        submit { repository.login(identifier.trim(), password) }
    }

    fun register() {
        if (registrationEmail.isBlank() || registrationUsername.isBlank() ||
            registrationPassword.isBlank() || registrationConfirmation.isBlank()) {
            error = "모든 항목을 입력해 주세요."
            return
        }
        submit {
            repository.register(
                registrationEmail.trim(), registrationUsername.trim(),
                registrationPassword, registrationConfirmation
            )
        }
    }

    private fun submit(block: suspend () -> AppSession) {
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
            } catch (failure: NewsApiException) {
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
