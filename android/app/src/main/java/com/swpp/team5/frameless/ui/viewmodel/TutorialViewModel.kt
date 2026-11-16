package com.swpp.team5.frameless.ui.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.swpp.team5.frameless.data.TutorialStore

/** 기사 화면 코치마크 단계. 순서대로 진행한다. */
internal enum class ReaderCoachStep { TAP_FINDING, PREVIEW_CARD, COMPARISON }

/**
 * 첫 실행 온보딩과 첫 기사 코치마크의 진행 상태.
 *
 * - 온보딩은 끝내거나 건너뛰면 다시 나오지 않는다.
 * - 코치마크는 표시할 표현 분석이 있는 기사를 처음 열 때 시작한다.
 *   분석이 없는 기사에서는 시작하지 않고 다음 기사를 기다린다.
 * - 어느 단계에서든 건너뛰거나 닫으면 완료로 기록한다.
 */
internal class TutorialViewModel(private val store: TutorialStore) : ViewModel() {
    var showIntro by mutableStateOf(!store.introSeen)
        private set
    var readerStep by mutableStateOf<ReaderCoachStep?>(null)
        private set

    fun finishIntro() {
        store.introSeen = true
        showIntro = false
    }

    /** 표시할 표현이 있는 기사가 열렸을 때 호출한다. */
    fun onReaderEligible() {
        if (showIntro || store.readerCoachSeen || readerStep != null) return
        readerStep = ReaderCoachStep.TAP_FINDING
    }

    /** 사용자가 표현을 눌러 분석 카드가 열렸다. */
    fun onFindingOpened() {
        if (readerStep == ReaderCoachStep.TAP_FINDING) readerStep = ReaderCoachStep.PREVIEW_CARD
    }

    /** 분석 카드 단계에서 다음으로. 비교 기사가 없으면 여기서 끝난다. */
    fun onPreviewNext(hasComparisons: Boolean) {
        if (readerStep != ReaderCoachStep.PREVIEW_CARD) return
        if (hasComparisons) readerStep = ReaderCoachStep.COMPARISON else finishReaderCoach()
    }

    fun finishReaderCoach() {
        if (readerStep == null) return
        store.readerCoachSeen = true
        readerStep = null
    }
}
