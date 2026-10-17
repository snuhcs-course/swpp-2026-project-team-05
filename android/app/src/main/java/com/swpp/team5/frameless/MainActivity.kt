package com.swpp.team5.frameless

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/* ==================================================================
 * 디자인 토큰 — "교정지 위 형광펜"
 *  · 종이: 차가운 회녹색 바탕 + 흰 지면
 *  · 잉크: 거의 검정. 선택 상태도 잉크로 표시
 *  · 형광펜 두 자루: 같은 내용 = 라임, 다른 해석/표현 = 라일락
 *    (정당 상징색과 겹치지 않는 색만 사용)
 *  · 서체: 인용문 = 명조(Serif), 메타 정보 = 고정폭(Monospace)
 * ================================================================== */

private val Paper = Color(0xFFECEEEA)
private val Sheet = Color(0xFFFFFFFF)
private val Ink = Color(0xFF121417)
private val Ink2 = Color(0xFF353A3D)
private val Muted = Color(0xFF6A7072)
private val Rule = Color(0xFFD3D7D2)
private val MarkSame = Color(0xFFDDF27A)
private val MarkSameInk = Color(0xFF4A5A00)
private val MarkDiff = Color(0xFFD4C4FF)
private val MarkDiffInk = Color(0xFF4B2E9E)
private val Danger = Color(0xFFA3271E)

private val Serif = FontFamily.Serif
private val Mono = FontFamily.Monospace

private val SplitMinWidth = 600.dp
private val CompactMaxHeight = 500.dp
private const val OTHERS = -1

private val ExpressionCategoryNames = mapOf(
    "A" to "인용·보도 동사", "B" to "인용명사·발언 명명", "C" to "행위·사건 서술어",
    "D" to "정도·수량 수식어", "E" to "평가·감정 수식어", "F" to "명명·일방적 용어",
    "G" to "양태·확신·범주화", "H" to "행위자 범위 확대 (P5 제안)"
)
private val InformationCategoryNames = mapOf(
    "I1" to "포함·누락", "I2" to "배치·비중", "I3" to "배경·맥락 덧붙이기",
    "I4" to "추측·전망", "I5" to "목소리 선택"
)
private val AbsenceReasonNames = mapOf(
    "timing_impossible" to "시점상 실을 수 없음", "format" to "형식·분량",
    "editorial" to "편집 선택", "unknown" to "이유 확인 필요"
)
private val PolarityNames = mapOf("negative" to "부정", "positive" to "긍정", "none" to "극성 없음")

internal enum class AppScreen { INPUT, LOADING, RESULT }
private enum class NarrowTab { INPUT_ARTICLE, ISSUES }

private fun two(n: Int) = n.toString().padStart(2, '0')

/* ==================================================================
 * 상태 보관 (회전·폴더블 펼침에도 결과 유지)
 * ================================================================== */

internal class AnalysisViewModel : ViewModel() {
    var screen by mutableStateOf(AppScreen.INPUT)
        private set
    var articleUrl by mutableStateOf("")
        private set
    var urlError by mutableStateOf<String?>(null)
        private set
    var requestError by mutableStateOf<String?>(null)
        private set
    /** 0 사건 파악, 1 보도 찾기, 2 문장 비교 */
    var loadingStep by mutableIntStateOf(0)
        private set
    var analysis by mutableStateOf<AnalysisResult?>(null)
        private set

    private var job: Job? = null

    fun onUrlChange(value: String) {
        articleUrl = value
        urlError = null
        requestError = null
    }

    fun analyze() {
        val trimmed = articleUrl.trim()
        requestError = null
        if (!isValidArticleUrl(trimmed)) {
            urlError = "http:// 또는 https://로 시작하는 기사 주소를 넣어 주세요."
            return
        }
        articleUrl = trimmed
        analysis = null
        screen = AppScreen.LOADING
        job?.cancel()
        job = viewModelScope.launch {
            loadingStep = 0
            val progress = launch {
                delay(4_000); loadingStep = 1
                delay(8_000); loadingStep = 2
            }
            try {
                analysis = AnalysisApi.analyze(trimmed)
                screen = AppScreen.RESULT
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                requestError = error.message ?: "기사를 분석하지 못했어요. 다시 시도해 주세요."
                screen = AppScreen.INPUT
            } finally {
                progress.cancel()
            }
        }
    }

    /** '새 기사': 입력 화면으로 돌아가 새 URL을 받는다 */
    fun startOver() {
        job?.cancel()
        articleUrl = ""
        urlError = null
        requestError = null
        analysis = null
        screen = AppScreen.INPUT
    }
}

/* ==================================================================
 * 화면용 데이터 — 서버 값에서 묶기만 한다. 판정을 새로 만들지 않는다.
 * ================================================================== */

private enum class Kind { SAME, DIFF, OTHER }

private fun kindOf(raw: String): Kind = when (Relation.from(raw)) {
    Relation.SAME -> Kind.SAME
    Relation.OPPOSES, Relation.DIFFERENT_INTERPRETATION -> Kind.DIFF
    Relation.OTHER -> Kind.OTHER
}

/** 입력 기사 문장 하나 */
private data class InputSentence(
    val quote: String,
    val issueIndexes: List<Int>,
    val sourceClaimIndexes: List<Int>
)

/** 다른 기사에서 찾은 대응 문장 하나 */
private data class Clip(
    val article: ArticleRef,
    val quote: String,
    val relationRaw: String,
    /** 이 문장이 연결된 다른 쟁점 번호 */
    val alsoIn: List<Int>
)

private data class IssueView(
    val index: Int,
    val label: String,
    val inputQuotes: List<String>,
    val same: List<Clip>,
    val diff: List<Clip>,
    val other: List<Clip>,
    /** 이 쟁점에서 대응 문장을 찾지 못한 비교 기사 */
    val notFound: List<ArticleRef>
)

private class ResultModel(val analysis: AnalysisResult) {
    val input: ArticleRef = analysis.articles.first { it.articleIndex == 0 }
    val others: List<ArticleRef> = analysis.articles.filter { it.articleIndex > 0 }

    val inputSentences: List<InputSentence> = buildInputSentences()
    val issues: List<IssueView> = analysis.issues.indices.map { buildIssue(it) }

    /** 입력 기사 주장과 연결되지 않은 다른 기사 문장 */
    val unlinkedClips: List<Clip> = analysis.issues.flatMap { it.members }
        .filter { m -> m.articleIndex > 0 && m.quote.isNotBlank() && m.sourceClaimIndex == null }
        .mapNotNull { m -> articleOf(m.articleIndex)?.let { Clip(it, cleanQuote(m.quote), m.relationRaw, emptyList()) } }
        .distinctBy { it.article.articleIndex to it.quote }

    fun articleOf(index: Int): ArticleRef? = analysis.articles.firstOrNull { it.articleIndex == index }

    /** 입력 문장과 대응하는 다른 기사 문장 (같은 기사·같은 문장은 하나로) */
    fun matchesFor(s: InputSentence): List<Clip> =
        analysis.issues.flatMap { it.members }
            .filter { it.articleIndex > 0 && it.quote.isNotBlank() && it.sourceClaimIndex in s.sourceClaimIndexes }
            .mapNotNull { m -> articleOf(m.articleIndex)?.let { Clip(it, cleanQuote(m.quote), m.relationRaw, emptyList()) } }
            .distinctBy { Triple(it.article.articleIndex, it.quote, kindOf(it.relationRaw)) }

    private fun buildInputSentences(): List<InputSentence> {
        val issues = analysis.issues
        val fromClaims = analysis.sourceClaims.filter { it.quote.isNotBlank() }.map { claim ->
            InputSentence(
                quote = cleanQuote(claim.quote),
                issueIndexes = issues.indices.filter { i -> issues[i].members.any { it.sourceClaimIndex == claim.index } },
                sourceClaimIndexes = listOf(claim.index)
            )
        }
        val extra = issues.indices.flatMap { i ->
            issues[i].members.filter { it.articleIndex == 0 && it.quote.isNotBlank() }.map { i to it }
        }.filter { (_, m) -> fromClaims.none { it.quote == cleanQuote(m.quote) } }
            .groupBy { (_, m) -> cleanQuote(m.quote) }
            .map { (q, pairs) ->
                InputSentence(q, pairs.map { it.first }.distinct(), pairs.mapNotNull { it.second.sourceClaimIndex }.distinct())
            }
        return (fromClaims + extra).distinctBy { it.quote }
    }

    private fun buildIssue(i: Int): IssueView {
        val issue = analysis.issues[i]
        val linkedClaims = issue.members.mapNotNull { it.sourceClaimIndex }.toSet()
        val inputQuotes = (
                issue.members.filter { it.articleIndex == 0 && it.quote.isNotBlank() }.map { cleanQuote(it.quote) } +
                        analysis.sourceClaims.filter { it.index in linkedClaims && it.quote.isNotBlank() }.map { cleanQuote(it.quote) }
                ).distinct()

        val clips = issue.members
            .filter { it.articleIndex > 0 && it.quote.isNotBlank() }
            .mapNotNull { m ->
                val article = articleOf(m.articleIndex) ?: return@mapNotNull null
                val q = cleanQuote(m.quote)
                val alsoIn = analysis.issues.indices.filter { j ->
                    j != i && analysis.issues[j].members.any { it.articleIndex == m.articleIndex && cleanQuote(it.quote) == q }
                }
                Clip(article, q, m.relationRaw, alsoIn)
            }
            .distinctBy { Triple(it.article.articleIndex, it.quote, kindOf(it.relationRaw)) }

        val covered = clips.map { it.article.articleIndex }.toSet()
        return IssueView(
            index = i,
            label = issue.label.ifBlank { "이름 없는 쟁점" },
            inputQuotes = inputQuotes,
            same = clips.filter { kindOf(it.relationRaw) == Kind.SAME },
            diff = clips.filter { kindOf(it.relationRaw) == Kind.DIFF },
            other = clips.filter { kindOf(it.relationRaw) == Kind.OTHER },
            notFound = others.filter { it.articleIndex !in covered }
        )
    }
}

/** 서버 문장 양끝을 감싼 따옴표 한 쌍만 걷어낸다. 안쪽 따옴표는 그대로 둔다. */
private fun cleanQuote(raw: String): String {
    val s = raw.trim()
    val pairs = listOf('“' to '”', '"' to '"', '‘' to '’', '\'' to '\'', '「' to '」', '『' to '』')
    for ((open, close) in pairs) {
        if (s.length >= 2 && s.first() == open && s.last() == close &&
            s.count { it == open || it == close } == 2
        ) return s.substring(1, s.length - 1).trim()
    }
    return s
}

/* ==================================================================
 * 앱
 * ================================================================== */

class MainActivity : ComponentActivity() {
    private val viewModel: AnalysisViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = Paper) {
                    Box(modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                        FrameLESSApp(viewModel)
                    }
                }
            }
        }
    }
}

@Composable
private fun FrameLESSApp(vm: AnalysisViewModel) {
    when (vm.screen) {
        AppScreen.INPUT -> InputScreen(
            articleUrl = vm.articleUrl,
            urlError = vm.urlError,
            requestError = vm.requestError,
            onArticleUrlChange = vm::onUrlChange,
            onAnalyze = vm::analyze
        )
        AppScreen.LOADING -> {
            BackHandler { vm.startOver() }
            LoadingScreen(step = vm.loadingStep, url = vm.articleUrl)
        }
        AppScreen.RESULT -> vm.analysis?.let { result ->
            ResultScreen(analysis = result, onNewArticle = vm::startOver)
        }
    }
}

/* ==================================================================
 * 입력
 * ================================================================== */

@Composable
private fun InputScreen(
    articleUrl: String,
    urlError: String?,
    requestError: String?,
    onArticleUrlChange: (String) -> Unit,
    onAnalyze: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 28.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.Start
    ) {
        Column(modifier = Modifier.widthIn(max = 560.dp), verticalArrangement = Arrangement.spacedBy(0.dp)) {
            Wordmark()
            Spacer(modifier = Modifier.height(56.dp))
            Text(
                text = "같은 사건,\n다른 문장.",
                fontFamily = Serif,
                fontWeight = FontWeight.Bold,
                fontSize = 40.sp,
                lineHeight = 48.sp,
                color = Ink
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "읽던 기사의 주소를 넣으면, 같은 사건을 다룬 다른 보도에서 대응하는 문장을 찾아 나란히 놓아 드려요.",
                fontSize = 15.sp,
                lineHeight = 23.sp,
                color = Ink2
            )
            Spacer(modifier = Modifier.height(40.dp))

            MonoLabel("기사 URL")
            Spacer(modifier = Modifier.height(8.dp))
            BasicTextField(
                value = articleUrl,
                onValueChange = onArticleUrlChange,
                singleLine = true,
                textStyle = TextStyle(fontFamily = Mono, fontSize = 15.sp, color = Ink),
                cursorBrush = SolidColor(Ink),
                keyboardOptions = KeyboardOptions(
                    autoCorrectEnabled = false,
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Go
                ),
                keyboardActions = KeyboardActions(onGo = { onAnalyze() }),
                modifier = Modifier.fillMaxWidth(),
                decorationBox = { inner ->
                    Column {
                        Box(modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
                            if (articleUrl.isEmpty()) {
                                Text("https://", fontFamily = Mono, fontSize = 15.sp, color = Muted)
                            }
                            inner()
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(if (urlError != null) 2.dp else 1.5.dp)
                                .background(if (urlError != null) Danger else Ink)
                        )
                    }
                }
            )
            if (urlError != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(urlError, fontSize = 13.sp, color = Danger)
            }
            Spacer(modifier = Modifier.height(24.dp))
            InkButton(text = "비교하기", onClick = onAnalyze, modifier = Modifier.fillMaxWidth())

            if (requestError != null) {
                Spacer(modifier = Modifier.height(20.dp))
                Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                    Box(modifier = Modifier.width(3.dp).fillMaxHeight().background(Danger))
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        MonoLabel("분석하지 못했어요", color = Danger)
                        Text(requestError, fontSize = 14.sp, lineHeight = 20.sp, color = Ink2)
                    }
                }
            }

            Spacer(modifier = Modifier.height(48.dp))
            HRule()
            Spacer(modifier = Modifier.height(14.dp))
            listOf(
                "어느 언론사가 옳은지 판정하지 않아요",
                "점수나 정치 성향 라벨을 붙이지 않아요",
                "모든 문장은 기사 제목과 원문 링크로 확인할 수 있어요"
            ).forEach {
                Text("— $it", fontFamily = Mono, fontSize = 12.sp, lineHeight = 20.sp, color = Muted)
            }
        }
    }
}

/* ==================================================================
 * 로딩
 * ================================================================== */

@Composable
private fun LoadingScreen(step: Int, url: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(28.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Column(modifier = Modifier.widthIn(max = 520.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Wordmark()
            Text(
                text = "같은 사건의 다른 보도를\n찾고 있어요",
                fontFamily = Serif,
                fontWeight = FontWeight.Bold,
                fontSize = 26.sp,
                lineHeight = 34.sp,
                color = Ink
            )
            Text(url, fontFamily = Mono, fontSize = 12.sp, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth().height(2.dp),
                color = Ink,
                trackColor = Rule
            )
            listOf("입력 기사의 핵심 사건 파악", "같은 사건을 다룬 보도 찾기", "문장끼리 대응 관계 비교").forEachIndexed { i, label ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(two(i + 1), fontFamily = Mono, fontSize = 12.sp, color = if (i <= step) Ink else Muted)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = label,
                        fontSize = 15.sp,
                        fontWeight = if (i == step) FontWeight.Bold else FontWeight.Normal,
                        color = when {
                            i < step -> Muted
                            i == step -> Ink
                            else -> Muted.copy(alpha = 0.6f)
                        },
                        textDecoration = if (i < step) TextDecoration.LineThrough else null
                    )
                }
            }
        }
    }
}

/* ==================================================================
 * 결과
 *  넓은 화면(600dp 이상): 왼쪽 입력 기사 | 오른쪽 쟁점 상세 (split view)
 *  좁은 화면: '입력 기사 / 쟁점' 탭 + 쟁점 상세 화면 전환
 * ================================================================== */

@Composable
private fun ResultScreen(analysis: AnalysisResult, onNewArticle: () -> Unit) {
    val model = remember(analysis) { ResultModel(analysis) }
    var selectedIssue by rememberSaveable { mutableIntStateOf(if (model.issues.isNotEmpty()) 0 else OTHERS) }
    var focusedQuote by rememberSaveable { mutableStateOf<String?>(null) }
    var tab by rememberSaveable { mutableStateOf(NarrowTab.INPUT_ARTICLE) }
    /** 좁은 화면에서 쟁점 상세를 띄웠는지 */
    var narrowDetail by rememberSaveable { mutableStateOf(false) }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val wide = maxWidth >= SplitMinWidth
        val compact = wide && maxHeight < CompactMaxHeight

        BackHandler {
            if (!wide && narrowDetail) narrowDetail = false else onNewArticle()
        }

        Column(modifier = Modifier.fillMaxSize()) {
            ResultHeader(model = model, compact = compact, onNewArticle = onNewArticle)

            if (wide) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Panel(modifier = Modifier.weight(1f).fillMaxHeight()) {
                        InputArticleColumn(
                            model = model,
                            selectedIssue = selectedIssue,
                            focusedQuote = focusedQuote,
                            compact = compact,
                            onSentence = { s ->
                                focusedQuote = s.quote
                                selectedIssue = s.issueIndexes.firstOrNull() ?: OTHERS
                            }
                        )
                    }
                    Panel(modifier = Modifier.weight(1.15f).fillMaxHeight()) {
                        Column(modifier = Modifier.fillMaxSize()) {
                            IssueStrip(
                                model = model,
                                selected = selectedIssue,
                                onSelect = {
                                    selectedIssue = it
                                    focusedQuote = null
                                }
                            )
                            HRule()
                            DetailBody(model = model, selected = selectedIssue, compact = compact)
                        }
                    }
                }
            } else if (narrowDetail) {
                NarrowDetail(
                    model = model,
                    selected = selectedIssue,
                    onBack = { narrowDetail = false },
                    onSelect = { selectedIssue = it }
                )
            } else {
                NarrowTabs(selected = tab, issueCount = model.issues.size, onSelect = { tab = it })
                when (tab) {
                    NarrowTab.INPUT_ARTICLE -> Panel(
                        modifier = Modifier.fillMaxSize().padding(start = 16.dp, end = 16.dp, bottom = 12.dp)
                    ) {
                        InputArticleColumn(
                            model = model,
                            selectedIssue = null,
                            focusedQuote = null,
                            compact = false,
                            onSentence = { s ->
                                selectedIssue = s.issueIndexes.firstOrNull() ?: OTHERS
                                narrowDetail = true
                            }
                        )
                    }
                    NarrowTab.ISSUES -> IssueList(
                        model = model,
                        onOpen = {
                            selectedIssue = it
                            narrowDetail = true
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ResultHeader(model: ResultModel, compact: Boolean, onNewArticle: () -> Unit) {
    val analysis = model.analysis
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 12.dp, top = if (compact) 4.dp else 12.dp, bottom = if (compact) 6.dp else 14.dp),
        verticalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Wordmark(fontSize = if (compact) 16.sp else 18.sp)
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = "+ 새 기사",
                fontFamily = Mono,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = Ink,
                modifier = Modifier
                    .border(1.dp, Ink, RoundedCornerShape(50))
                    .clickable(onClick = onNewArticle)
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            )
        }
        if (compact) {
            Text(
                text = analysis.coreEvent.ifBlank { "핵심 사건을 요약하지 못했어요." },
                fontFamily = Serif,
                fontSize = 14.sp,
                color = Ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                MonoLabel("분석한 사건")
                Text(
                    text = analysis.coreEvent.ifBlank { "핵심 사건을 요약하지 못했어요." },
                    fontFamily = Serif,
                    fontWeight = FontWeight.Bold,
                    fontSize = 19.sp,
                    lineHeight = 27.sp,
                    color = Ink
                )
                Text(
                    text = "입력 기사 1 · 비교 기사 ${analysis.relatedArticleCount} · 쟁점 ${model.issues.size}",
                    fontFamily = Mono,
                    fontSize = 12.sp,
                    color = Muted
                )
                if (analysis.relatedWithoutMatches.isNotEmpty()) {
                    Text(
                        text = "대응 문장을 하나도 찾지 못한 기사: " + analysis.relatedWithoutMatches.joinToString(", "),
                        fontSize = 12.sp,
                        color = Muted
                    )
                }
            }
        }
    }
}

/* ---------------- 좁은 화면 ---------------- */

@Composable
private fun NarrowTabs(selected: NarrowTab, issueCount: Int, onSelect: (NarrowTab) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        listOf(NarrowTab.INPUT_ARTICLE to "입력 기사", NarrowTab.ISSUES to "쟁점 ${two(issueCount)}").forEach { (t, label) ->
            val on = t == selected
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onSelect(t) }
                    .padding(top = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = label,
                    fontSize = 15.sp,
                    fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                    color = if (on) Ink else Muted
                )
                Spacer(modifier = Modifier.height(10.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(if (on) 2.dp else 1.dp)
                        .background(if (on) Ink else Rule)
                )
            }
        }
    }
    Spacer(modifier = Modifier.height(12.dp))
}

@Composable
private fun IssueList(model: ResultModel, onOpen: (Int) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        model.issues.forEach { issue ->
            Panel(modifier = Modifier.fillMaxWidth().clickable { onOpen(issue.index) }) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.Top) {
                        Text(two(issue.index + 1), fontFamily = Mono, fontSize = 13.sp, color = Muted)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = issue.label,
                            fontFamily = Serif,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            lineHeight = 23.sp,
                            color = Ink,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("→", fontFamily = Mono, fontSize = 16.sp, color = Ink)
                    }
                    IssueTally(issue)
                }
            }
        }
        Panel(modifier = Modifier.fillMaxWidth().clickable { onOpen(OTHERS) }) {
            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("··", fontFamily = Mono, fontSize = 13.sp, color = Muted)
                Spacer(modifier = Modifier.width(12.dp))
                Text("쟁점에 묶이지 않은 문장 · 판정 기준", fontSize = 15.sp, color = Ink, modifier = Modifier.weight(1f))
                Text("→", fontFamily = Mono, fontSize = 16.sp, color = Ink)
            }
        }
    }
}

@Composable
private fun NarrowDetail(model: ResultModel, selected: Int, onBack: () -> Unit, onSelect: (Int) -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(start = 16.dp, end = 16.dp, bottom = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
            Text(
                text = "← 목록",
                fontFamily = Mono,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = Ink,
                modifier = Modifier.clickable(onClick = onBack).padding(vertical = 8.dp, horizontal = 4.dp)
            )
            Spacer(modifier = Modifier.weight(1f))
            if (selected >= 0) {
                val prev = (selected - 1).takeIf { it >= 0 }
                val next = (selected + 1).takeIf { it < model.issues.size }
                Text(
                    text = "‹",
                    fontFamily = Mono, fontSize = 20.sp,
                    color = if (prev != null) Ink else Rule,
                    modifier = Modifier.clickable(enabled = prev != null) { prev?.let(onSelect) }.padding(horizontal = 12.dp)
                )
                Text("${two(selected + 1)} / ${two(model.issues.size)}", fontFamily = Mono, fontSize = 13.sp, color = Muted)
                Text(
                    text = "›",
                    fontFamily = Mono, fontSize = 20.sp,
                    color = if (next != null) Ink else Rule,
                    modifier = Modifier.clickable(enabled = next != null) { next?.let(onSelect) }.padding(horizontal = 12.dp)
                )
            }
        }
        Panel(modifier = Modifier.fillMaxSize()) {
            DetailBody(model = model, selected = selected, compact = false)
        }
    }
}

/* ---------------- 입력 기사 열 ---------------- */

@Composable
private fun InputArticleColumn(
    model: ResultModel,
    selectedIssue: Int?,
    focusedQuote: String?,
    compact: Boolean,
    onSentence: (InputSentence) -> Unit
) {
    val uriHandler = LocalUriHandler.current
    val expressions = model.analysis.rubric?.expressions.orEmpty().filter { it.articleIndex == 0 }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = if (compact) 10.dp else 18.dp),
        verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            MonoLabel("입력 기사")
            Spacer(modifier = Modifier.weight(1f))
            model.input.url?.let { url -> SourceLink(onClick = { uriHandler.openUri(url) }) }
        }
        Text(
            text = model.input.title,
            fontFamily = Serif,
            fontWeight = FontWeight.Bold,
            fontSize = if (compact) 15.sp else 18.sp,
            lineHeight = if (compact) 21.sp else 26.sp,
            color = Ink,
            maxLines = if (compact) 1 else 3,
            overflow = TextOverflow.Ellipsis
        )
        if (!compact) {
            Text(
                text = "다른 보도와 비교한 문장만 모았어요. 문장을 누르면 대응하는 출처를 볼 수 있어요.",
                fontSize = 12.sp,
                lineHeight = 18.sp,
                color = Muted
            )
        }
        HRule()

        if (model.inputSentences.isEmpty()) {
            Text("비교에 쓰인 문장이 없어요.", fontSize = 14.sp, color = Muted)
        }
        model.inputSentences.forEachIndexed { n, s ->
            val focused = focusedQuote == s.quote
            val inIssue = selectedIssue != null && selectedIssue in s.issueIndexes
            val matches = model.matchesFor(s)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min)
                    .clickable { onSentence(s) }
            ) {
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .fillMaxHeight()
                        .background(if (focused || inIssue) Ink else Color.Transparent)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(two(n + 1), fontFamily = Mono, fontSize = 12.sp, color = Muted, modifier = Modifier.padding(top = 3.dp))
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = highlight(s.quote, expressions, null),
                        fontFamily = Serif,
                        fontSize = 16.sp,
                        lineHeight = 25.sp,
                        color = Ink
                    )
                    SentenceTally(matches = matches, issueIndexes = s.issueIndexes)
                }
            }
        }
    }
}

/** 입력 문장 아래: 대응 출처 수 (서버 relation 값 그대로 집계) */
@Composable
private fun SentenceTally(matches: List<Clip>, issueIndexes: List<Int>) {
    val same = matches.filter { kindOf(it.relationRaw) == Kind.SAME }.map { it.article.articleIndex }.distinct().size
    val diff = matches.filter { kindOf(it.relationRaw) == Kind.DIFF }.map { it.article.articleIndex }.distinct().size
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (same > 0) MarkerTag("같은 내용 · ${same}곳", MarkSame, MarkSameInk)
        if (diff > 0) MarkerTag("다른 해석/표현 · ${diff}곳", MarkDiff, MarkDiffInk)
        if (same == 0 && diff == 0) {
            Text("다른 기사에서 대응 문장을 찾지 못했어요", fontSize = 12.sp, color = Muted)
        }
        if (issueIndexes.isNotEmpty()) {
            Text("쟁점 " + issueIndexes.joinToString(" ") { two(it + 1) }, fontFamily = Mono, fontSize = 11.sp, color = Muted)
        }
    }
}

/* ---------------- 쟁점 선택 띠 (넓은 화면) ---------------- */

@Composable
private fun IssueStrip(model: ResultModel, selected: Int, onSelect: (Int) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        MonoLabel("쟁점")
        Spacer(modifier = Modifier.width(4.dp))
        model.issues.forEach { issue ->
            StripChip(label = two(issue.index + 1), selected = issue.index == selected) { onSelect(issue.index) }
        }
        StripChip(label = "그 밖", selected = selected == OTHERS) { onSelect(OTHERS) }
    }
}

@Composable
private fun StripChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = label,
        fontFamily = Mono,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        color = if (selected) Sheet else Ink,
        modifier = Modifier
            .background(if (selected) Ink else Color.Transparent, RoundedCornerShape(50))
            .border(1.dp, if (selected) Ink else Rule, RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    )
}

/* ==================================================================
 * 쟁점 상세 — 넓은 화면 오른쪽 / 좁은 화면 전환 화면에서 같이 쓴다
 * ================================================================== */

@Composable
private fun DetailBody(model: ResultModel, selected: Int, compact: Boolean) {
    val issue = model.issues.getOrNull(selected)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = if (compact) 10.dp else 18.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        if (issue == null) OthersDetail(model) else IssueDetail(model, issue)
        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
private fun IssueDetail(model: ResultModel, issue: IssueView) {
    val uriHandler = LocalUriHandler.current

    // 쟁점 제목
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        MonoLabel("쟁점 ${two(issue.index + 1)} / ${two(model.issues.size)}")
        Text(issue.label, fontFamily = Serif, fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 28.sp, color = Ink)
        IssueTally(issue)
    }

    // 입력 기사
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionHead("입력 기사")
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = model.input.title,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = Ink2,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            model.input.url?.let { SourceLink(onClick = { uriHandler.openUri(it) }) }
        }
        if (issue.inputQuotes.isEmpty()) {
            Text("이 쟁점에 연결된 입력 기사 문장이 없어요.", fontSize = 13.sp, color = Muted)
        }
        issue.inputQuotes.forEach { q -> PullQuote(q, mark = null) }
    }

    // 같은 내용
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHead("같은 내용을 보도한 출처", count = issue.same.map { it.article.articleIndex }.distinct().size)
        if (issue.same.isEmpty()) {
            EmptyNote("같은 내용으로 대응하는 문장을 찾지 못했어요.")
        } else {
            OutletRoll(issue.same.map { it.article }.distinctBy { it.articleIndex }, MarkSame)
            issue.same.forEachIndexed { n, clip -> Clipping(n + 1, clip, MarkSame, tag = null) }
        }
    }

    // 다르게 언급된 부분
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHead("다르게 언급된 부분", count = issue.diff.map { it.article.articleIndex }.distinct().size)
        if (issue.diff.isEmpty()) {
            EmptyNote("뚜렷한 차이를 찾지 못했어요.")
        } else {
            issue.diff.forEachIndexed { n, clip -> Clipping(n + 1, clip, MarkDiff, tag = "다른 해석/표현") }
        }
    }

    // 관계 정보가 없는 대응 문장 (서버가 같음/다름 외의 값을 보낸 경우)
    if (issue.other.isNotEmpty()) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionHead("관계가 표시되지 않은 대응 문장", count = issue.other.size)
            issue.other.forEachIndexed { n, clip -> Clipping(n + 1, clip, null, tag = null) }
        }
    }

    // 찾지 못한 출처
    if (issue.notFound.isNotEmpty()) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionHead("이 쟁점에서 대응 문장을 찾지 못한 출처")
            issue.notFound.forEach { a ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(a.title, fontSize = 13.sp, color = Ink2, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    a.url?.let { SourceLink(onClick = { uriHandler.openUri(it) }) }
                }
            }
            Text("찾지 못했다는 뜻이에요. 그 기사가 이 내용을 뺐다는 판정은 아니에요.", fontSize = 12.sp, color = Muted)
        }
    }
}

@Composable
private fun OthersDetail(model: ResultModel) {
    val uriHandler = LocalUriHandler.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        MonoLabel("그 밖")
        Text("쟁점에 묶이지 않은 문장", fontFamily = Serif, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Ink)
    }

    val inputOnly = model.inputSentences.filter { model.matchesFor(it).isEmpty() }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionHead("다른 기사에서 대응 문장을 찾지 못한 입력 기사 문장", count = inputOnly.size)
        if (inputOnly.isEmpty()) EmptyNote("모든 입력 기사 문장에서 대응 문장을 찾았어요.")
        inputOnly.forEach { PullQuote(it.quote, mark = null) }
        if (inputOnly.isNotEmpty()) {
            Text("비교한 기사들에서 짝을 찾지 못했다는 뜻이에요. 일부러 뺐다는 판정은 아니에요.", fontSize = 12.sp, color = Muted)
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHead("입력 기사와 연결되지 않은 다른 기사 문장", count = model.unlinkedClips.size)
        if (model.unlinkedClips.isEmpty()) EmptyNote("없어요.")
        model.unlinkedClips.forEachIndexed { n, clip -> Clipping(n + 1, clip, null, tag = null) }
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionHead("판정 기준 v0.2.1")
        val rubric = model.analysis.rubric
        if (rubric == null) {
            EmptyNote("표현 차이(A~G·유표성), 정보 구성 차이(I1~I5), 질문별 비교는 서버 응답에 아직 없어 표시하지 않아요.")
        } else {
            RubricBlock(rubric, model, onOpen = { uriHandler.openUri(it) })
        }
    }
}

/* ==================================================================
 * 조각들
 * ================================================================== */

/** 워드마크: FrameLESS를 네 귀퉁이 괄호로 감싼다 */
@Composable
private fun Wordmark(fontSize: TextUnit = 20.sp) {
    Row(
        modifier = Modifier
            .drawBehind {
                val l = 7.dp.toPx(); val w = 1.5.dp.toPx()
                val c = Ink
                // 왼쪽 위, 오른쪽 위, 왼쪽 아래, 오른쪽 아래
                drawLine(c, Offset(0f, 0f), Offset(l, 0f), w); drawLine(c, Offset(0f, 0f), Offset(0f, l), w)
                drawLine(c, Offset(size.width, 0f), Offset(size.width - l, 0f), w); drawLine(c, Offset(size.width, 0f), Offset(size.width, l), w)
                drawLine(c, Offset(0f, size.height), Offset(l, size.height), w); drawLine(c, Offset(0f, size.height), Offset(0f, size.height - l), w)
                drawLine(c, Offset(size.width, size.height), Offset(size.width - l, size.height), w)
                drawLine(c, Offset(size.width, size.height), Offset(size.width, size.height - l), w)
            }
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        Text("Frame", fontFamily = Serif, fontWeight = FontWeight.Bold, fontSize = fontSize, color = Ink)
        Text("LESS", fontFamily = Mono, fontSize = fontSize * 0.78f, color = Muted, modifier = Modifier.padding(start = 2.dp, bottom = 1.dp))
    }
}

@Composable
private fun Panel(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier = modifier
            .background(Sheet, RoundedCornerShape(6.dp))
            .border(1.dp, Rule, RoundedCornerShape(6.dp))
    ) { content() }
}

@Composable
private fun HRule() {
    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Rule))
}

@Composable
private fun MonoLabel(text: String, color: Color = Muted) {
    Text(text = text, fontFamily = Mono, fontSize = 11.sp, letterSpacing = 0.6.sp, color = color)
}

@Composable
private fun SectionHead(text: String, count: Int? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(text, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Ink, modifier = Modifier.weight(1f, fill = false))
            if (count != null) {
                Spacer(modifier = Modifier.width(8.dp))
                Text("${count}곳", fontFamily = Mono, fontSize = 12.sp, color = Muted)
            }
        }
        Box(modifier = Modifier.fillMaxWidth().height(1.5.dp).background(Ink))
    }
}

@Composable
private fun InkButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .background(Ink, RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Sheet, modifier = Modifier.weight(1f))
        Text("→", fontFamily = Mono, fontSize = 18.sp, color = MarkSame)
    }
}

@Composable
private fun SourceLink(onClick: () -> Unit) {
    Text(
        text = "원문 ↗",
        fontFamily = Mono,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        color = Ink,
        textDecoration = TextDecoration.Underline,
        modifier = Modifier.clickable(onClick = onClick).padding(horizontal = 6.dp, vertical = 8.dp)
    )
}

@Composable
private fun MarkerTag(text: String, bg: Color, fg: Color) {
    Text(
        text = text,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        color = fg,
        maxLines = 1,
        modifier = Modifier
            .background(bg, RoundedCornerShape(3.dp))
            .padding(horizontal = 7.dp, vertical = 2.dp)
    )
}

@Composable
private fun IssueTally(issue: IssueView) {
    val same = issue.same.map { it.article.articleIndex }.distinct().size
    val diff = issue.diff.map { it.article.articleIndex }.distinct().size
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        if (same > 0) MarkerTag("같은 내용 · ${same}곳", MarkSame, MarkSameInk)
        if (diff > 0) MarkerTag("다른 해석/표현 · ${diff}곳", MarkDiff, MarkDiffInk)
        if (same == 0 && diff == 0) Text("대응 문장을 찾지 못했어요", fontSize = 12.sp, color = Muted)
    }
}

/** 같은 내용을 보도한 출처 이름을 한 줄씩 나열 */
@Composable
private fun OutletRoll(articles: List<ArticleRef>, mark: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        articles.forEach { a ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(8.dp).background(mark, CircleShape).border(1.dp, Ink.copy(alpha = 0.25f), CircleShape))
                Spacer(modifier = Modifier.width(8.dp))
                Text(a.title, fontSize = 13.sp, color = Ink2, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** 인용 블록: 명조 + 왼쪽 굵은 세로선. mark가 있으면 형광펜으로 칠한다 */
@Composable
private fun PullQuote(text: String, mark: Color?) {
    Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        Box(modifier = Modifier.width(3.dp).fillMaxHeight().background(Ink))
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = if (mark != null) marked(text, mark) else AnnotatedString(text),
            fontFamily = Serif,
            fontSize = 16.sp,
            lineHeight = 26.sp,
            color = Ink,
            modifier = Modifier.weight(1f)
        )
    }
}

/** 출처 스크랩: 번호 · 기사 제목 · 원문 링크 · 형광펜 인용 */
@Composable
private fun Clipping(n: Int, clip: Clip, mark: Color?, tag: String?) {
    val uriHandler = LocalUriHandler.current
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(two(n), fontFamily = Mono, fontSize = 12.sp, color = Muted)
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = clip.article.title,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = Ink2,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            val url = clip.article.url
            if (url != null) SourceLink(onClick = { uriHandler.openUri(url) })
            else Text("링크 없음", fontFamily = Mono, fontSize = 11.sp, color = Muted)
        }
        if (tag != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                MarkerTag(tag, MarkDiff, MarkDiffInk)
                // 서버가 보낸 관계 값을 그대로 남겨 추적할 수 있게 한다
                Text(clip.relationRaw, fontFamily = Mono, fontSize = 10.sp, color = Muted)
            }
        } else if (mark == null && clip.relationRaw.isNotBlank()) {
            Text(clip.relationRaw, fontFamily = Mono, fontSize = 10.sp, color = Muted)
        }
        Text(
            text = if (mark != null) marked(clip.quote, mark) else AnnotatedString(clip.quote),
            fontFamily = Serif,
            fontSize = 15.sp,
            lineHeight = 25.sp,
            color = Ink,
            modifier = Modifier.padding(start = 26.dp)
        )
        if (clip.alsoIn.isNotEmpty()) {
            Text(
                text = "같은 문장이 쟁점 " + clip.alsoIn.joinToString(" ") { two(it + 1) } + "에도 연결돼 있어요",
                fontFamily = Mono,
                fontSize = 10.sp,
                color = Muted,
                modifier = Modifier.padding(start = 26.dp)
            )
        }
    }
}

/** 점선 상자: 비어 있는 상태 */
@Composable
private fun EmptyNote(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .drawBehind {
                drawRoundRect(
                    color = Rule,
                    cornerRadius = CornerRadius(4.dp.toPx()),
                    style = Stroke(width = 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)))
                )
            }
            .padding(14.dp)
    ) {
        Text(text, fontSize = 13.sp, lineHeight = 19.sp, color = Muted)
    }
}

/** 문장 전체를 형광펜으로 칠한다 */
private fun marked(text: String, mark: Color): AnnotatedString = buildAnnotatedString {
    withStyle(SpanStyle(background = mark)) { append(text) }
}

/** 서버 rubric 표현만 칠한다 (클라이언트가 만들어 내지 않음) */
private fun highlight(text: String, expressions: List<ExpressionDifference>, unused: Color?): AnnotatedString {
    val ranges = expressions.mapNotNull { e ->
        val start = text.indexOf(e.span)
        if (start < 0 || e.span.isBlank()) null else Triple(start, start + e.span.length, e)
    }.sortedBy { it.first }
    return buildAnnotatedString {
        var cursor = 0
        for ((start, end, e) in ranges) {
            if (start < cursor) continue
            append(text.substring(cursor, start))
            val style = if (e.maxMarkedness >= 1) SpanStyle(textDecoration = TextDecoration.Underline, fontWeight = FontWeight.Bold) else SpanStyle()
            withStyle(style) { append(text.substring(start, end)) }
            cursor = end
        }
        append(text.substring(cursor))
    }
}

/* ---------------- 판정 기준 (서버가 rubric을 보낼 때만) ---------------- */

@Composable
private fun RubricBlock(rubric: RubricResult, model: ResultModel, onOpen: (String) -> Unit) {
    val analysis = model.analysis
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("범주와 값만 보여주고 어느 쪽이 옳은지는 판정하지 않아요. 설명은 모델이 판정 뒤에 정리한 것이에요.", fontSize = 12.sp, color = Muted)
        if (rubric.droppedCount > 0) {
            Text("형식이 맞지 않아 표시하지 않은 항목 ${rubric.droppedCount}개", fontFamily = Mono, fontSize = 11.sp, color = Danger)
        }
        rubric.questions.forEach { q ->
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                MonoLabel(q.id)
                Text(q.question, fontFamily = Serif, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Ink)
                q.answers.forEach { a ->
                    val title = analysis.articleTitle(a.articleIndex)
                    if (a.answered) {
                        Text("$title — ${a.summary}", fontSize = 13.sp, color = Ink2)
                        a.evidenceQuotes.forEach { PullQuote(cleanQuote(it), mark = null) }
                    } else {
                        Text(
                            text = "$title — 이 기사에는 없음" + (a.absenceReason?.let { " · ${AbsenceReasonNames[it]}" } ?: ""),
                            fontSize = 13.sp,
                            color = Muted
                        )
                    }
                }
            }
        }
        rubric.expressions.forEach { e ->
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                MonoLabel("표현 차이 · " + e.categories.joinToString(" ") { "${it.code}${it.markedness}" } + if (e.held) " · 보류" else "")
                PullQuote(e.sentence, mark = null)
                Text("\"${e.span}\" · " + e.categories.joinToString(", ") { ExpressionCategoryNames[it.code] ?: it.code }, fontSize = 13.sp, color = Ink2)
                Text(
                    text = "기준 표현: ${e.baselineText.ifBlank { "(없음)" }} · " +
                            (if (e.baselineSource == "outlet") "비교 기사" else "중립 대체 표현(모델 생성)") +
                            " · 대상: ${e.target ?: "없음"} · ${PolarityNames[e.polarity] ?: e.polarity}",
                    fontSize = 12.sp,
                    color = Muted
                )
                if (e.note.isNotBlank()) Text(e.note, fontSize = 12.sp, color = Muted)
                analysis.articles.firstOrNull { it.articleIndex == e.articleIndex }?.url?.let { url ->
                    SourceLink(onClick = { onOpen(url) })
                }
            }
        }
        rubric.informations.forEach { i ->
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                MonoLabel("정보 구성 차이 · ${i.code} ${InformationCategoryNames[i.code] ?: ""}")
                Text(analysis.articleTitle(i.articleIndex), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Ink2)
                i.sentence?.let { PullQuote(cleanQuote(it), mark = null) }
                Text(i.value, fontSize = 13.sp, color = Ink2)
            }
        }
    }
}

private fun isValidArticleUrl(url: String): Boolean {
    val uri = Uri.parse(url)
    return (uri.scheme == "http" || uri.scheme == "https") && !uri.host.isNullOrBlank()
}