package com.swpp.team5.frameless.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swpp.team5.frameless.data.ArticleAnalysisUi
import com.swpp.team5.frameless.data.ArticleDetail
import com.swpp.team5.frameless.data.ComparisonArticleUi
import com.swpp.team5.frameless.data.FindingUi
import com.swpp.team5.frameless.data.StoredAnalysisState
import com.swpp.team5.frameless.ui.theme.Caption
import com.swpp.team5.frameless.ui.theme.Fl
import com.swpp.team5.frameless.ui.theme.OutlinedPill
import com.swpp.team5.frameless.ui.theme.SourceLink
import com.swpp.team5.frameless.ui.theme.TagPill
import com.swpp.team5.frameless.ui.viewmodel.ArticleReaderState
import kotlinx.coroutines.android.awaitFrame
import kotlinx.coroutines.launch

private val NewsMarker = Color(0xFFE9E1F5)
private val NewsTargetMarker = Color(0xFFE5D0F2)

@Composable
internal fun NewsReaderScreen(
    state: ArticleReaderState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onAnalysisEnabled: (Boolean) -> Unit
) {
    when {
        state.isArticleLoading -> ReaderLoading(onBack)
        state.articleError != null || state.article == null -> ReaderError(
            state.articleError ?: "기사를 불러오지 못했어요.", onBack, onRetry
        )
        else -> NewsReaderContent(state.article, state.analysis, state.analysisEnabled,
            state.isAnalysisLoading, state.analysisError, onBack, onAnalysisEnabled)
    }
}

@Composable
private fun NewsReaderContent(
    article: ArticleDetail,
    analysis: ArticleAnalysisUi?,
    analysisEnabled: Boolean,
    analysisLoading: Boolean,
    analysisError: String?,
    onBack: () -> Unit,
    onAnalysisEnabled: (Boolean) -> Unit
) {
    val uriHandler = LocalUriHandler.current
    val visibleFindings = remember(analysis, analysisEnabled) {
        if (analysisEnabled) analysis?.findings.orEmpty().nonOverlappingByScope() else emptyList()
    }
    val titleFindings = visibleFindings.filter { it.scope == "TITLE" }
    val bodyFindings = visibleFindings.filter { it.scope == "BODY" }
    val titleText = remember(article.title, titleFindings) { annotatedFindings(article.title, titleFindings) }
    val bodyText = remember(article.body, bodyFindings) { annotatedFindings(article.body, bodyFindings) }
    var selected by remember(article.id) { mutableStateOf<FindingUi?>(null) }
    val scroll = rememberScrollState()

    BackHandler(enabled = selected == null, onBack = onBack)
    Column(Modifier.fillMaxSize().background(Fl.Card)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text("←", fontSize = 24.sp, color = Fl.Ink,
                modifier = Modifier.clickable(onClick = onBack)
                    .semantics {
                        role = Role.Button
                        contentDescription = "기사 목록으로 돌아가기"
                    }
                    .padding(horizontal = 8.dp, vertical = 6.dp))
            Text("기사 읽기", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Fl.Ink,
                modifier = Modifier.weight(1f))
            Text("표현 분석", fontSize = 11.sp, color = Fl.Muted,
                fontWeight = FontWeight.Medium)
            Spacer(Modifier.width(2.dp))
            Switch(
                checked = analysisEnabled,
                onCheckedChange = onAnalysisEnabled,
                enabled = analysis?.state in setOf(StoredAnalysisState.READY, StoredAnalysisState.PARTIAL),
                modifier = Modifier.scale(0.82f).semantics {
                    role = Role.Switch
                    contentDescription = "표현 분석 표시"
                    stateDescription = if (analysisEnabled) "켜짐" else "꺼짐"
                },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Fl.OnAccent,
                    checkedTrackColor = Fl.Accent,
                    uncheckedThumbColor = Fl.Muted,
                    uncheckedTrackColor = Fl.Inset,
                    uncheckedBorderColor = Fl.Line,
                    disabledCheckedThumbColor = Fl.OnAccent,
                    disabledCheckedTrackColor = Fl.Line,
                    disabledUncheckedThumbColor = Fl.Muted,
                    disabledUncheckedTrackColor = Fl.Inset
                )
            )
        }
        HorizontalDivider(color = Fl.Line)
        Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(horizontal = 22.dp, vertical = 22.dp)) {
            Caption("${article.publisher.name} · ${formatNewsTimestamp(article.publishedAt)} · ${article.category.label}")
            Spacer(Modifier.height(10.dp))
            FindingText(
                text = titleText,
                findings = titleFindings,
                fontSize = 26,
                lineHeight = 34,
                fontWeight = FontWeight.Bold,
                onFinding = { selected = it }
            )
            SourceLink(onClick = { uriHandler.openUri(article.originalUrl) })
            AnalysisNotice(analysis, analysisLoading, analysisError, analysisEnabled)
            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = Fl.Line)
            Spacer(Modifier.height(20.dp))
            FindingText(
                text = bodyText,
                findings = bodyFindings,
                fontSize = 16,
                lineHeight = 29,
                fontWeight = FontWeight.Normal,
                onFinding = { selected = it }
            )
            Spacer(Modifier.height(36.dp))
        }
    }
    selected?.let { finding ->
        key(finding.id) {
            FindingDetailSheet(finding, onDismiss = { selected = null })
        }
    }
}

@Composable
private fun AnalysisNotice(analysis: ArticleAnalysisUi?, loading: Boolean, error: String?, enabled: Boolean) {
    val message = when {
        loading -> "저장된 표현 분석을 불러오는 중이에요. 기사는 그대로 읽을 수 있어요."
        error != null -> "표현 분석을 불러오지 못했어요. 기사는 그대로 읽을 수 있어요."
        analysis?.state == StoredAnalysisState.UPDATING -> "표현 분석을 준비 중이에요."
        analysis?.state == StoredAnalysisState.FAILED || analysis?.state == StoredAnalysisState.NOT_STARTED ->
            "아직 표시할 표현 분석이 없어요."
        analysis?.state == StoredAnalysisState.PARTIAL -> "검토가 끝난 표현만 표시하고 있어요."
        analysis?.state == StoredAnalysisState.READY && analysis.findings.isEmpty() ->
            "분석은 완료되었지만 현재 표시할 표현 특징을 확인하지 못했어요."
        enabled && !analysis?.findings.isNullOrEmpty() -> "밑줄 친 표현을 누르면 설명과 다른 보도를 볼 수 있어요."
        else -> null
    }
    if (message != null) {
        Text(message, fontSize = 12.sp, lineHeight = 18.sp, color = Fl.Muted,
            modifier = Modifier.fillMaxWidth().background(Fl.Inset, RoundedCornerShape(8.dp))
                .padding(horizontal = 12.dp, vertical = 9.dp))
    }
}

@Composable
private fun FindingText(
    text: AnnotatedString,
    findings: List<FindingUi>,
    fontSize: Int,
    lineHeight: Int,
    fontWeight: FontWeight,
    onFinding: (FindingUi) -> Unit
) {
    var layout by remember(text) { mutableStateOf<TextLayoutResult?>(null) }
    val currentLayout = rememberUpdatedState(layout)
    Text(
        text = text,
        modifier = Modifier.fillMaxWidth().pointerInput(text, findings) {
            detectTapGestures { position: Offset ->
                val offset = currentLayout.value?.getOffsetForPosition(position) ?: return@detectTapGestures
                val id = text.getStringAnnotations("finding", offset, (offset + 1).coerceAtMost(text.length))
                    .firstOrNull()?.item ?: return@detectTapGestures
                findings.firstOrNull { it.id == id }?.let(onFinding)
            }
        },
        fontFamily = Fl.Serif,
        fontSize = fontSize.sp,
        lineHeight = lineHeight.sp,
        fontWeight = fontWeight,
        color = Fl.Ink,
        onTextLayout = { layout = it }
    )
}

internal fun annotatedFindings(text: String, findings: List<FindingUi>): AnnotatedString = buildAnnotatedString {
    append(text)
    findings.forEach { finding ->
        if (finding.markerRange.isWithin(text)) {
            addStringAnnotation("finding", finding.id, finding.markerRange.start, finding.markerRange.end)
            addStyle(
                SpanStyle(background = NewsMarker, textDecoration = TextDecoration.Underline,
                    fontWeight = FontWeight.SemiBold),
                finding.markerRange.start,
                finding.markerRange.end
            )
        }
    }
}

private fun List<FindingUi>.nonOverlappingByScope(): List<FindingUi> =
    groupBy { it.scope }.values.flatMap { scoped ->
        var end = -1
        scoped.sortedWith(compareBy({ it.markerRange.start }, { it.markerRange.end })).filter { finding ->
            if (finding.markerRange.start < end) false else {
                end = finding.markerRange.end
                true
            }
        }
    }

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun FindingDetailSheet(finding: FindingUi, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    val pagerState = rememberPagerState(pageCount = { finding.comparisons.size })
    val scope = rememberCoroutineScope()
    var dragDistance by remember { mutableFloatStateOf(0f) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        // Let only the explicit handle move the sheet. Mixing the pager, article
        // scroll and Material's whole-sheet drag produced unstable transitions.
        sheetGesturesEnabled = false,
        dragHandle = {
            Box(
                Modifier.fillMaxWidth().height(48.dp).pointerInput(sheetState.currentValue) {
                    val threshold = 24.dp.toPx()
                    detectVerticalDragGestures(
                        onDragStart = { dragDistance = 0f },
                        onVerticalDrag = { change, amount -> change.consume(); dragDistance += amount },
                        onDragEnd = {
                            when {
                                dragDistance > threshold && sheetState.currentValue == SheetValue.Expanded ->
                                    scope.launch { sheetState.partialExpand() }
                                dragDistance < -threshold && sheetState.currentValue == SheetValue.PartiallyExpanded ->
                                    scope.launch { sheetState.expand() }
                            }
                            dragDistance = 0f
                        },
                        onDragCancel = { dragDistance = 0f }
                    )
                },
                contentAlignment = Alignment.Center
            ) { BottomSheetDefaults.DragHandle() }
        },
        containerColor = Fl.Card,
        contentColor = Fl.Ink
    ) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.93f)) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("이 표현에서 드러난 특징", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text("“${finding.markerText}”", fontFamily = Fl.Serif, fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold, color = Fl.InkDiff)
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    finding.categories.map(::categoryLabel).distinct().forEach {
                        TagPill(it, Fl.TintDiff, Fl.InkDiff)
                    }
                }
                Text(finding.explanation, fontSize = 14.sp, lineHeight = 21.sp, color = Fl.Ink2)
                if (finding.comparisons.isEmpty()) {
                    Caption("기사 문맥")
                    Text(finding.sentenceText, fontFamily = Fl.Serif, fontSize = 14.sp,
                        lineHeight = 22.sp, color = Fl.Ink2,
                        modifier = Modifier.fillMaxWidth().background(Fl.Inset, RoundedCornerShape(8.dp))
                            .padding(12.dp))
                }
            }
            if (finding.comparisons.isNotEmpty()) {
                HorizontalDivider(color = Fl.Line, modifier = Modifier.padding(top = 6.dp))
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text("같은 내용을 다른 기사에서는", fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    Caption("${pagerState.currentPage + 1} / ${finding.comparisons.size}")
                }
                Caption(
                    sheetInstruction(finding.comparisons.size, sheetState.currentValue == SheetValue.Expanded),
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp)
                )
                HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { index ->
                    NewsComparisonPage(finding.comparisons[index], sheetState)
                }
            } else {
                Spacer(Modifier.height(22.dp))
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun NewsComparisonPage(page: ComparisonArticleUi, sheetState: SheetState) {
    val uriHandler = LocalUriHandler.current
    val density = LocalDensity.current
    val rootView = LocalView.current.rootView
    val scroll = rememberScrollState()
    val bodyEvidence = page.evidence.filter { it.scope == "BODY" }
    val titleEvidence = page.evidence.filter { it.scope == "TITLE" }
    val body = remember(page) { annotatedTarget(page.body, bodyEvidence) }
    val title = remember(page) { annotatedTarget(page.title, titleEvidence) }
    var layout by remember(page.articleId) { mutableStateOf<TextLayoutResult?>(null) }
    var viewportHeight by remember(page.articleId) { mutableIntStateOf(0) }
    var visibleHeight by remember(page.articleId) { mutableIntStateOf(0) }
    var focused by remember(page.articleId) { mutableStateOf(false) }

    LaunchedEffect(layout, visibleHeight, viewportHeight, scroll.maxValue, sheetState.currentValue) {
        if (focused || bodyEvidence.isEmpty() || sheetState.currentValue == SheetValue.Hidden) return@LaunchedEffect
        val textLayout = layout ?: return@LaunchedEffect
        val offset = bodyEvidence.first().markerRange.start
        if (offset !in page.body.indices || visibleHeight <= 0) return@LaunchedEffect
        if (scroll.maxValue == 0 && textLayout.size.height > viewportHeight) return@LaunchedEffect
        awaitFrame()
        val box = textLayout.getBoundingBox(offset)
        val target = centeredReaderOffset(
            with(density) { 14.dp.toPx() } + box.top + box.height / 2f,
            visibleHeight,
            scroll.maxValue
        )
        scroll.scrollTo(target)
        focused = true
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 8.dp)) {
        Caption("${page.publisher.name} · ${formatNewsTimestamp(page.publishedAt)}")
        Text(title, fontFamily = Fl.Serif, fontSize = 18.sp, lineHeight = 25.sp,
            fontWeight = FontWeight.Bold, color = Fl.Ink)
        SourceLink(onClick = { uriHandler.openUri(page.originalUrl) })
        page.evidence.forEach { evidence ->
            Column(Modifier.fillMaxWidth().background(Fl.Inset, RoundedCornerShape(8.dp))
                .padding(horizontal = 11.dp, vertical = 8.dp)) {
                val attribution = evidence.speakerLabel?.let { "$it 표현" }
                    ?: if (evidence.owner == "REPORTER") "기자 표현" else null
                if (attribution != null) Caption(attribution, color = Fl.InkDiff)
                Text(evidence.summary, fontSize = 12.sp, lineHeight = 18.sp, color = Fl.Ink2)
            }
            Spacer(Modifier.height(6.dp))
        }
        HorizontalDivider(color = Fl.Line)
        Box(
            Modifier.fillMaxSize().onSizeChanged { viewportHeight = it.height }
                .onGloballyPositioned { coordinates ->
                    visibleHeight = (rootView.height - coordinates.positionInWindow().y).toInt()
                        .coerceIn(0, coordinates.size.height)
                }
        ) {
            Column(Modifier.fillMaxSize().verticalScroll(
                scroll, enabled = sheetState.currentValue == SheetValue.Expanded
            )) {
                Text(body, modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
                    fontFamily = Fl.Serif, fontSize = 15.sp, lineHeight = 27.sp, color = Fl.Ink,
                    onTextLayout = { layout = it })
                Spacer(Modifier.height(with(density) {
                    if (sheetState.currentValue == SheetValue.Expanded) 0.dp
                    else readerTailPadding(viewportHeight, visibleHeight).toDp()
                }))
            }
        }
    }
}

private fun annotatedTarget(text: String, evidence: List<com.swpp.team5.frameless.data.ComparisonEvidenceUi>) =
    buildAnnotatedString {
        append(text)
        evidence.forEach { row ->
            if (row.sentenceRange.isWithin(text)) {
                addStyle(SpanStyle(background = NewsMarker, textDecoration = TextDecoration.Underline),
                    row.sentenceRange.start, row.sentenceRange.end)
            }
            if (row.markerRange.isWithin(text)) {
                addStyle(SpanStyle(background = NewsTargetMarker, fontWeight = FontWeight.Bold),
                    row.markerRange.start, row.markerRange.end)
            }
        }
    }

private fun categoryLabel(code: String): String = when (code) {
    "A" -> "보도 태도가 드러나는 표현"
    "B" -> "발언을 규정하는 표현"
    "C" -> "행위를 규정하는 표현"
    "D" -> "정도를 강조하는 표현"
    "E" -> "평가·감정이 담긴 표현"
    "F" -> "한쪽의 명명이 반영된 표현"
    "G" -> "확신·범주화가 드러나는 표현"
    else -> "표현 특징"
}

@Composable
private fun ReaderLoading(onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().background(Fl.Card)) {
        Text("←", fontSize = 24.sp, color = Fl.Ink,
            modifier = Modifier.clickable(onClick = onBack).padding(20.dp))
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Fl.Accent)
        }
    }
}

@Composable
private fun ReaderError(message: String, onBack: () -> Unit, onRetry: () -> Unit) {
    Column(Modifier.fillMaxSize().background(Fl.Card).padding(22.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text("기사를 불러오지 못했어요", fontWeight = FontWeight.Bold, color = Fl.Ink)
        Spacer(Modifier.height(8.dp))
        Text(message, fontSize = 13.sp, color = Fl.Muted)
        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedPill("목록으로", onBack)
            OutlinedPill("다시 시도", onRetry)
        }
    }
}
