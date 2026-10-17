package com.swpp.team5.frameless.ui.viewmodel

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.swpp.team5.frameless.data.AnalysisApi
import com.swpp.team5.frameless.data.AnalysisResult
import com.swpp.team5.frameless.ui.state.AnalysisUiState
import com.swpp.team5.frameless.ui.state.ComparisonModel
import com.swpp.team5.frameless.ui.state.ComparisonSelection
import com.swpp.team5.frameless.ui.state.InputSentence
import com.swpp.team5.frameless.ui.state.NarrowTab
import com.swpp.team5.frameless.ui.state.OTHERS_GROUP
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * URL 입력, 분석 요청, 비교 화면의 선택 상태를 관리한다.
 * 화면 회전·폴더블 펼침에도 살아남는다.
 *
 * analyze 함수를 생성자로 받아 테스트에서 가짜 응답을 넣을 수 있다.
 */
internal class AnalysisViewModel(
    private val analyzeArticle: suspend (String) -> AnalysisResult
) : ViewModel() {

    /** viewModels() 기본 팩토리용 */
    constructor() : this({ url -> AnalysisApi.analyze(url) })

    var url by mutableStateOf("")
        private set
    var urlError by mutableStateOf<String?>(null)
        private set
    var uiState by mutableStateOf<AnalysisUiState>(AnalysisUiState.Idle)
        private set
    var selection by mutableStateOf(ComparisonSelection())
        private set

    private var job: Job? = null

    private val model: ComparisonModel? get() = (uiState as? AnalysisUiState.Success)?.model

    /* ---------------- 입력 ---------------- */

    fun onUrlChange(value: String) {
        url = value
        urlError = null
        if (uiState is AnalysisUiState.Error) uiState = AnalysisUiState.Idle
    }

    fun analyze() {
        val trimmed = url.trim()
        if (!isValidArticleUrl(trimmed)) {
            urlError = "http:// 또는 https://로 시작하는 기사 주소를 넣어 주세요."
            return
        }
        url = trimmed
        job?.cancel()
        uiState = AnalysisUiState.Loading(trimmed, step = 0)
        job = viewModelScope.launch {
            val progress = launch {
                delay(4_000); setStep(1)
                delay(8_000); setStep(2)
            }
            try {
                val model = ComparisonModel(analyzeArticle(trimmed))
                selection = ComparisonSelection(group = if (model.groupCount > 0) 0 else OTHERS_GROUP)
                uiState = AnalysisUiState.Success(model)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                uiState = AnalysisUiState.Error(error.message ?: "기사를 분석하지 못했어요. 다시 시도해 주세요.")
            } finally {
                progress.cancel()
            }
        }
    }

    private fun setStep(step: Int) {
        val s = uiState
        if (s is AnalysisUiState.Loading) uiState = s.copy(step = step)
    }

    /** '새 기사': 입력 화면으로 돌아가 새 URL을 받는다 */
    fun startOver() {
        job?.cancel()
        url = ""
        urlError = null
        selection = ComparisonSelection()
        uiState = AnalysisUiState.Idle
    }

    /* ---------------- 비교 화면 선택 ---------------- */

    fun selectGroup(index: Int) {
        selection = selection.copy(group = index, focusedQuote = null)
    }

    /** 넓은 화면: 왼쪽 문장을 누르면 오른쪽에 그 문장의 쟁점(또는 주장)을 띄운다 */
    fun focusSentence(sentence: InputSentence) {
        val groups = model?.groupsFor(sentence).orEmpty()
        selection = selection.copy(group = groups.firstOrNull() ?: OTHERS_GROUP, focusedQuote = sentence.quote)
    }

    /** 좁은 화면: 문장을 누르면 그 문장의 상세를 연다 */
    fun openSentenceDetail(sentence: InputSentence) {
        val groups = model?.groupsFor(sentence).orEmpty()
        selection = selection.copy(
            group = groups.firstOrNull() ?: OTHERS_GROUP,
            focusedQuote = sentence.quote,
            narrowDetailOpen = true
        )
    }

    fun setNarrowTab(tab: NarrowTab) {
        selection = selection.copy(narrowTab = tab)
    }

    /** 좁은 화면: 상세를 전체 화면으로 연다 */
    fun openNarrowDetail(index: Int) {
        selection = selection.copy(group = index, narrowDetailOpen = true)
    }

    fun closeNarrowDetail() {
        selection = selection.copy(narrowDetailOpen = false)
    }

    /** 대응 문장이 하나도 없을 때 안내 화면 대신 입력 기사 문장을 펼쳐 본다 */
    fun showDespiteNoMatch() {
        selection = selection.copy(showDespiteNoMatch = true)
    }
}

private fun isValidArticleUrl(url: String): Boolean {
    val uri = Uri.parse(url)
    return (uri.scheme == "http" || uri.scheme == "https") && !uri.host.isNullOrBlank()
}
