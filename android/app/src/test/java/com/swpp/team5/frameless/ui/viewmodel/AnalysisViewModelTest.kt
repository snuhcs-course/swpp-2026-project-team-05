package com.swpp.team5.frameless.ui.viewmodel

import android.os.Looper
import com.swpp.team5.frameless.data.AnalysisApi
import com.swpp.team5.frameless.data.AnalysisApiTest
import com.swpp.team5.frameless.data.AnalysisResult
import com.swpp.team5.frameless.ui.state.AnalysisUiState
import com.swpp.team5.frameless.ui.state.ComparisonSelection
import com.swpp.team5.frameless.ui.state.NarrowTab
import com.swpp.team5.frameless.ui.state.OTHERS_GROUP
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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
class AnalysisViewModelTest {
    private val mainLooper get() = shadowOf(Looper.getMainLooper())

    private fun result(withMatches: Boolean = true): AnalysisResult = AnalysisApi.parseAnalysis(
        if (withMatches) AnalysisApiTest.responseWithMatches() else AnalysisApiTest.responseWithoutMatches(),
        "https://requested.example/article"
    )

    @Test
    fun rejectsInvalidUrlsWithoutCallingAnalyzerAndTrimsValidInput() {
        var requested: String? = null
        val pending = CompletableDeferred<AnalysisResult>()
        val viewModel = AnalysisViewModel { url -> requested = url; pending.await() }

        for (invalid in listOf("", "ftp://example.com/article", "https://")) {
            viewModel.onUrlChange(invalid)
            viewModel.analyze()
            assertTrue(viewModel.uiState is AnalysisUiState.Idle)
            assertNotNull(viewModel.urlError)
            assertEquals(null, requested)
        }

        viewModel.onUrlChange("  https://requested.example/article  ")
        assertEquals(null, viewModel.urlError)
        viewModel.analyze()
        assertEquals("https://requested.example/article", viewModel.url)
        assertEquals(AnalysisUiState.Loading(viewModel.url, 0), viewModel.uiState)
        mainLooper.idle()
        assertEquals(viewModel.url, requested)
        viewModel.startOver()
    }

    @Test
    fun loadingProgressEndsInSuccessAndSelectsFirstGroup() {
        val pending = CompletableDeferred<AnalysisResult>()
        val viewModel = AnalysisViewModel { pending.await() }
        viewModel.onUrlChange("https://requested.example/article")

        viewModel.analyze()
        mainLooper.idle()
        assertEquals(AnalysisUiState.Loading(viewModel.url, 0), viewModel.uiState)
        mainLooper.idleFor(4, TimeUnit.SECONDS)
        assertEquals(AnalysisUiState.Loading(viewModel.url, 1), viewModel.uiState)
        mainLooper.idleFor(8, TimeUnit.SECONDS)
        assertEquals(AnalysisUiState.Loading(viewModel.url, 2), viewModel.uiState)

        pending.complete(result())
        mainLooper.idle()
        val success = viewModel.uiState as AnalysisUiState.Success
        assertEquals(2, success.model.groupCount)
        assertEquals(0, viewModel.selection.group)
        assertFalse(viewModel.selection.narrowDetailOpen)
    }

    @Test
    fun failedRequestShowsMessageAndRetryCanSucceed() {
        var calls = 0
        val viewModel = AnalysisViewModel {
            if (++calls == 1) throw IllegalStateException("서버 연결 실패")
            result()
        }
        viewModel.onUrlChange("https://requested.example/article")

        viewModel.analyze()
        mainLooper.idle()
        assertEquals(AnalysisUiState.Error("서버 연결 실패"), viewModel.uiState)
        assertEquals(1, calls)

        viewModel.analyze()
        mainLooper.idle()
        assertTrue(viewModel.uiState is AnalysisUiState.Success)
        assertEquals(2, calls)
    }

    @Test
    fun changingInputClearsPreviousErrorAndMissingMessageUsesFallback() {
        val viewModel = AnalysisViewModel { throw IllegalStateException() }
        viewModel.onUrlChange("https://requested.example/article")
        viewModel.analyze()
        mainLooper.idle()
        assertEquals(
            AnalysisUiState.Error("기사를 분석하지 못했어요. 다시 시도해 주세요."),
            viewModel.uiState
        )

        viewModel.onUrlChange("https://requested.example/another")
        assertTrue(viewModel.uiState is AnalysisUiState.Idle)
        assertEquals(null, viewModel.urlError)
    }

    @Test
    fun startOverCancelsPendingAnalysisAndResetsInputAndSelection() {
        val pending = CompletableDeferred<AnalysisResult>()
        val viewModel = AnalysisViewModel { pending.await() }
        viewModel.onUrlChange("https://requested.example/article")
        viewModel.analyze()
        mainLooper.idle()
        viewModel.openNarrowDetail(4)
        assertTrue(viewModel.selection.narrowDetailOpen)

        viewModel.startOver()
        assertTrue(viewModel.uiState is AnalysisUiState.Idle)
        assertEquals("", viewModel.url)
        assertEquals(null, viewModel.urlError)
        assertEquals(ComparisonSelection(), viewModel.selection)

        pending.complete(result())
        mainLooper.idle()
        assertTrue(viewModel.uiState is AnalysisUiState.Idle)
    }

    @Test
    fun secondRequestIgnoresLateResultFromCancelledFirstRequest() {
        val first = CompletableDeferred<AnalysisResult>()
        val second = CompletableDeferred<AnalysisResult>()
        val viewModel = AnalysisViewModel { url ->
            if (url.endsWith("/first")) first.await() else second.await()
        }
        viewModel.onUrlChange("https://requested.example/first")
        viewModel.analyze()
        mainLooper.idle()
        viewModel.onUrlChange("https://requested.example/second")
        viewModel.analyze()
        mainLooper.idle()

        first.complete(result())
        mainLooper.idle()
        assertEquals(AnalysisUiState.Loading("https://requested.example/second", 0), viewModel.uiState)

        second.complete(result(withMatches = false))
        mainLooper.idle()
        val success = viewModel.uiState as AnalysisUiState.Success
        assertEquals(0, success.model.groupCount)
        assertEquals(OTHERS_GROUP, viewModel.selection.group)
    }

    @Test
    fun comparisonSelectionsFollowInputSentenceAndResetForNewArticle() {
        val viewModel = AnalysisViewModel { result() }
        viewModel.onUrlChange("https://requested.example/article")
        viewModel.analyze()
        mainLooper.idle()
        val model = (viewModel.uiState as AnalysisUiState.Success).model
        val sentence = model.inputSentences.first()

        viewModel.focusSentence(sentence)
        assertEquals(0, viewModel.selection.group)
        assertEquals(sentence.quote, viewModel.selection.focusedQuote)
        viewModel.openSentenceDetail(sentence)
        assertTrue(viewModel.selection.narrowDetailOpen)
        viewModel.setNarrowTab(NarrowTab.GROUPS)
        assertEquals(NarrowTab.GROUPS, viewModel.selection.narrowTab)
        viewModel.closeNarrowDetail()
        assertFalse(viewModel.selection.narrowDetailOpen)
        viewModel.openNarrowDetail(1)
        assertEquals(1, viewModel.selection.group)
        assertTrue(viewModel.selection.narrowDetailOpen)
        viewModel.selectGroup(0)
        assertEquals(null, viewModel.selection.focusedQuote)
        viewModel.showDespiteNoMatch()
        assertTrue(viewModel.selection.showDespiteNoMatch)

        viewModel.startOver()
        assertEquals(ComparisonSelection(), viewModel.selection)
    }
}
