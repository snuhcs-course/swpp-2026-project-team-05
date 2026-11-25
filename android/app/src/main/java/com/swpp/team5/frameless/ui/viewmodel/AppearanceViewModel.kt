package com.swpp.team5.frameless.ui.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.swpp.team5.frameless.data.AppearanceMode
import com.swpp.team5.frameless.data.AppearanceStore

internal class AppearanceViewModel(private val store: AppearanceStore) : ViewModel() {
    var mode by mutableStateOf(store.mode)
        private set

    fun select(next: AppearanceMode) {
        if (next == mode) return
        store.mode = next
        mode = next
    }
}
