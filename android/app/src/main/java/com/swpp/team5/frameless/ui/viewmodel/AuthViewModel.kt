package com.swpp.team5.frameless.ui.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.swpp.team5.frameless.data.AnalysisHistoryIdentity
import com.swpp.team5.frameless.data.AuthRepository
import kotlinx.coroutines.launch

internal class AuthViewModel(private val repository: AuthRepository, private val clientId: String) : ViewModel() {
    var username by mutableStateOf(repository.currentSession()?.username.orEmpty())
    var password by mutableStateOf("")
    var isLoggedIn by mutableStateOf(repository.currentSession() != null)
    var isLoading by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)

    fun login() {
        if (username.isBlank() || password.isBlank()) { error = "아이디와 비밀번호를 입력해 주세요."; return }
        isLoading = true; error = null
        viewModelScope.launch {
            try { repository.login(username.trim(), password, clientId); password = ""; isLoggedIn = true }
            catch (e: Exception) { error = e.message ?: "로그인하지 못했어요." }
            finally { isLoading = false }
        }
    }
    fun logout() { repository.logout(); password = ""; isLoggedIn = false; error = null }
}
