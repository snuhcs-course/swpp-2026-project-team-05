package com.swpp.team5.frameless.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.swpp.team5.frameless.ui.screens.ComparisonScreen
import com.swpp.team5.frameless.ui.screens.ImportScreen
import com.swpp.team5.frameless.ui.screens.LoadingScreen
import com.swpp.team5.frameless.ui.state.AnalysisUiState
import com.swpp.team5.frameless.ui.theme.Fl
import com.swpp.team5.frameless.ui.viewmodel.AnalysisViewModel

/** 전체 화면 전환: 입력 → 분석 중 → 비교 */
@Composable
internal fun FrameLESSApp(vm: AnalysisViewModel) {
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = Fl.Bg) {
            Box(modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                when (val state = vm.uiState) {
                    AnalysisUiState.Idle, is AnalysisUiState.Error -> ImportScreen(
                        url = vm.url,
                        urlError = vm.urlError,
                        requestError = (state as? AnalysisUiState.Error)?.message,
                        onUrlChange = vm::onUrlChange,
                        onAnalyze = vm::analyze
                    )

                    is AnalysisUiState.Loading -> {
                        BackHandler { vm.startOver() }
                        LoadingScreen(url = state.url, step = state.step)
                    }

                    is AnalysisUiState.Success -> ComparisonScreen(
                        model = state.model,
                        selection = vm.selection,
                        onNewArticle = vm::startOver,
                        onSelectIssue = vm::selectIssue,
                        onFocusSentence = vm::focusSentence,
                        onNarrowTab = vm::setNarrowTab,
                        onOpenNarrowDetail = vm::openNarrowDetail,
                        onCloseNarrowDetail = vm::closeNarrowDetail
                    )
                }
            }
        }
    }
}
