package com.swpp.team5.frameless.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ScrollState
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
import androidx.compose.foundation.layout.size
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
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Velocity
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swpp.team5.frameless.data.ArticleAnalysisUi
import com.swpp.team5.frameless.data.ArticleDetail
import com.swpp.team5.frameless.data.ComparisonArticleUi
import com.swpp.team5.frameless.data.FindingUi
import com.swpp.team5.frameless.data.StoredAnalysisState
import com.swpp.team5.frameless.ui.theme.Caption
import com.swpp.team5.frameless.ui.components.ArticleBookmarkIcon
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
    onAnalysisEnabled: (Boolean) -> Unit,
    onToggleSaved: (String, Boolean) -> Unit,
    savedPending: Boolean,
    saveError: String?
) {
    when {
        state.isArticleLoading -> {
            BackHandler(onBack = onBack)
            ReaderLoading(onBack)
        }
        state.articleError != null || state.article == null -> {
            BackHandler(onBack = onBack)
            ReaderError(state.articleError ?: "기사를 불러오지 못했어요.", onBack, onRetry)
        }
        else -> NewsReaderContent(state.article, state.analysis, state.analysisEnabled,
            state.isAnalysisLoading, state.analysisError, onBack, onAnalysisEnabled,
            onToggleSaved, savedPending, saveError)
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
    onAnalysisEnabled: (Boolean) -> Unit,
    onToggleSaved: (String, Boolean) -> Unit,
    savedPending: Boolean,
    saveError: String?
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
    var detailOpen by remember(article.id) { mutableStateOf(false) }
    var previewHeightPx by remember(article.id) { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val scroll = rememberScrollState()

    LaunchedEffect(analysisEnabled) {
        if (!analysisEnabled) {
            selected = null
            detailOpen = false
        }
    }

    BackHandler(enabled = selected == null && !detailOpen, onBack = onBack)
    BackHandler(enabled = selected != null && !detailOpen) { selected = null }

    Box(Modifier.fillMaxSize().background(Fl.Card)) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text("←", fontSize = 24.sp, color = Fl.Ink,
                    modifier = Modifier.clickable {
                        if (selected != null) selected = null else onBack()
                    }
                        .semantics {
                            role = Role.Button
                            contentDescription = if (selected != null) {
                                "표현 분석 닫기"
                            } else {
                                "기사 목록으로 돌아가기"
                            }
                        }
                        .padding(horizontal = 8.dp, vertical = 6.dp))
                Text("기사 읽기", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Fl.Ink,
                    modifier = Modifier.weight(1f))
                IconButton(
                    onClick = { onToggleSaved(article.id, article.isSaved) },
                    enabled = !savedPending,
                    modifier = Modifier.semantics {
                        contentDescription = if (article.isSaved) "저장 취소" else "기사 저장"
                    }
                ) {
                    ArticleBookmarkIcon(
                        article.isSaved,
                        if (article.isSaved) Fl.Accent else Fl.Muted,
                        Modifier.size(20.dp)
                    )
                }
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
            if (saveError != null) {
                Text(saveError, color = Fl.Danger, fontSize = 12.sp,
                    modifier = Modifier.fillMaxWidth().background(Fl.Inset)
                        .padding(horizontal = 18.dp, vertical = 9.dp))
            }
            Column(
                Modifier.fillMaxSize().verticalScroll(scroll)
                    .padding(horizontal = 22.dp, vertical = 22.dp)
            ) {
                Caption("${article.publisher.name} · ${formatNewsTimestamp(article.publishedAt)} · ${article.category.label}")
                Spacer(Modifier.height(10.dp))
                FindingText(
                    text = titleText,
                    findings = titleFindings,
                    fontSize = 26,
                    lineHeight = 34,
                    fontWeight = FontWeight.Bold,
                    onFinding = { selected = it; detailOpen = false },
                    onPlainTextTap = { selected = null }
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
                    onFinding = { selected = it; detailOpen = false },
                    onPlainTextTap = { selected = null }
                )
                val previewSpace = with(density) { previewHeightPx.toDp() } + 24.dp
                Spacer(Modifier.height(
                    if (selected != null && !detailOpen) previewSpace.coerceAtLeast(210.dp)
                    else 36.dp
                ))
            }
        }

        selected?.takeUnless { detailOpen }?.let { finding ->
            FindingPreviewCard(
                finding = finding,
                onDismiss = { selected = null },
                onOpenDetail = {
                    if (finding.comparisons.isNotEmpty()) detailOpen = true
                },
                onHeightChanged = { previewHeightPx = it },
                modifier = Modifier.align(Alignment.BottomCenter)
                    .padding(horizontal = 12.dp, vertical = 12.dp)
            )
        }
    }

    selected?.takeIf { detailOpen }?.let { finding ->
        FindingDetailSheet(
            finding = finding,
            onDismiss = {
                detailOpen = false
                selected = null
            }
        )
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
    onFinding: (FindingUi) -> Unit,
    onPlainTextTap: () -> Unit = {}
) {
    var layout by remember(text) { mutableStateOf<TextLayoutResult?>(null) }
    val currentLayout = rememberUpdatedState(layout)
    Text(
        text = text,
        modifier = Modifier.fillMaxWidth().pointerInput(text, findings) {
            detectTapGestures { position: Offset ->
                val offset = currentLayout.value?.getOffsetForPosition(position) ?: return@detectTapGestures
                val id = text.getStringAnnotations(
                    "finding", offset, (offset + 1).coerceAtMost(text.length)
                ).firstOrNull()?.item
                val finding = id?.let { selectedId -> findings.firstOrNull { it.id == selectedId } }
                if (finding != null) onFinding(finding) else onPlainTextTap()
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
internal fun FindingPreviewCard(
    finding: FindingUi,
    onDismiss: () -> Unit,
    onOpenDetail: () -> Unit,
    onHeightChanged: (Int) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val hasComparisons = finding.comparisons.isNotEmpty()
    var explanationExpanded by remember(finding.id) { mutableStateOf(false) }
    var explanationOverflows by remember(finding.id) { mutableStateOf(false) }
    Column(
        modifier.fillMaxWidth().shadow(12.dp, RoundedCornerShape(18.dp))
            .background(Fl.Card, RoundedCornerShape(18.dp))
            .border(1.dp, Fl.Line, RoundedCornerShape(18.dp))
            .then(if (hasComparisons) Modifier.clickable(onClick = onOpenDetail) else Modifier)
            .animateContentSize()
            .onSizeChanged { onHeightChanged(it.height) }
            .padding(start = 17.dp, end = 10.dp, top = 10.dp, bottom = 10.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("표현 분석", fontSize = 12.sp, fontWeight = FontWeight.Bold,
                color = Fl.Ink2, modifier = Modifier.weight(1f))
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.size(48.dp).semantics { contentDescription = "표현 분석 닫기" }
            ) {
                Text("×", fontSize = 23.sp, color = Fl.Muted)
            }
        }
        Text("“${finding.markerText}”", fontFamily = Fl.Serif, fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold, color = Fl.InkDiff,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                .padding(top = 2.dp, bottom = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            finding.categories.map(::categoryLabel).distinct().forEach {
                TagPill(it, Fl.TintDiff, Fl.InkDiff)
            }
        }
        Text(finding.explanation, fontSize = 13.sp, lineHeight = 19.sp, color = Fl.Ink2,
            maxLines = if (explanationExpanded) Int.MAX_VALUE else 4,
            overflow = TextOverflow.Ellipsis,
            onTextLayout = {
                if (!explanationExpanded) explanationOverflows = it.hasVisualOverflow
            })
        if (explanationOverflows || explanationExpanded) {
            Text(
                if (explanationExpanded) "설명 접기" else "설명 더보기",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = Fl.Accent,
                modifier = Modifier.clickable { explanationExpanded = !explanationExpanded }
                    .padding(vertical = 4.dp)
            )
        }
        if (hasComparisons) {
            Row(
                Modifier.fillMaxWidth().clickable(onClick = onOpenDetail)
                    .padding(top = 5.dp, bottom = 3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Spacer(Modifier.weight(1f))
                Text("다른 기사 ${finding.comparisons.size}곳과 비교  ›", fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold, color = Fl.Accent)
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun FindingDetailSheet(finding: FindingUi, onDismiss: () -> Unit) {
    if (finding.comparisons.isEmpty()) return
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val pagerState = rememberPagerState(pageCount = { finding.comparisons.size })
    val comparisonOverview = finding.comparisonOverview.ifBlank { finding.explanation }
    val scope = rememberCoroutineScope()
    var dismissing by remember { mutableStateOf(false) }
    val dismissSheet: () -> Unit = {
        if (!dismissing) {
            dismissing = true
            scope.launch {
                sheetState.hide()
                onDismiss()
            }
        }
    }
    val handleDismissModifier = downwardDismissModifier(dismissSheet)
    val sectionDismissModifier = downwardDismissModifier(dismissSheet)
    ModalBottomSheet(
        onDismissRequest = dismissSheet,
        sheetState = sheetState,
        // Material never moves the sheet directly. App-owned regions arbitrate
        // dismissal: fixed headers accept a downward drag, and article content
        // accepts it only after its own scroll reaches the top.
        sheetGesturesEnabled = false,
        dragHandle = {
            Box(
                Modifier.fillMaxWidth().height(48.dp)
                    .then(handleDismissModifier),
                contentAlignment = Alignment.Center
            ) {
                BottomSheetDefaults.DragHandle()
            }
        },
        containerColor = Fl.Card,
        contentColor = Fl.Ink
    ) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.93f)) {
            Column(
                Modifier.fillMaxWidth().then(sectionDismissModifier)
                    .padding(start = 20.dp, end = 8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("같은 내용을 다른 기사에서는", fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    IconButton(
                        onClick = dismissSheet,
                        modifier = Modifier.semantics { contentDescription = "비교 상세 닫기" }
                    ) {
                        Text("×", fontSize = 23.sp, color = Fl.Muted)
                    }
                }
                if (comparisonOverview.isNotBlank()) {
                    Text(
                        comparisonOverview,
                        fontSize = 13.sp,
                        lineHeight = 19.sp,
                        color = Fl.Ink2,
                        modifier = Modifier.fillMaxWidth()
                            .padding(end = 12.dp, bottom = 10.dp)
                            .background(Fl.Inset, RoundedCornerShape(10.dp))
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                    )
                }
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { index ->
                    NewsComparisonPage(
                        page = finding.comparisons[index],
                        sheetState = sheetState,
                        onDismiss = dismissSheet,
                        hasFloatingNavigator = finding.comparisons.size > 1
                    )
                }
                if (finding.comparisons.size > 1) {
                    val hasPrevious = pagerState.currentPage > 0
                    val hasNext = pagerState.currentPage < finding.comparisons.lastIndex
                    Row(
                        Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp)
                            .shadow(6.dp, RoundedCornerShape(50))
                            .background(Fl.Card.copy(alpha = 0.92f), RoundedCornerShape(50))
                            .border(1.dp, Fl.Line.copy(alpha = 0.75f), RoundedCornerShape(50))
                            .height(48.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier.size(48.dp).clickable(enabled = hasPrevious) {
                                scope.launch {
                                    pagerState.animateScrollToPage(pagerState.currentPage - 1)
                                }
                            }.semantics { contentDescription = "이전 비교 기사" },
                            contentAlignment = Alignment.Center
                        ) {
                            Text("‹", fontSize = 24.sp,
                                color = if (hasPrevious) Fl.Accent else Fl.Line)
                        }
                        Caption("${pagerState.currentPage + 1} / ${finding.comparisons.size}")
                        Box(
                            Modifier.size(48.dp).clickable(enabled = hasNext) {
                                scope.launch {
                                    pagerState.animateScrollToPage(pagerState.currentPage + 1)
                                }
                            }.semantics { contentDescription = "다음 비교 기사" },
                            contentAlignment = Alignment.Center
                        ) {
                            Text("›", fontSize = 24.sp,
                                color = if (hasNext) Fl.Accent else Fl.Line)
                        }
                    }
                }
            }
        }
    }
}

internal fun shouldDismissDetailSheet(dragDistancePx: Float, thresholdPx: Float): Boolean =
    dragDistancePx > thresholdPx

@Composable
private fun downwardDismissModifier(onDismiss: () -> Unit): Modifier {
    var dragDistance by remember { mutableFloatStateOf(0f) }
    val currentOnDismiss by rememberUpdatedState(onDismiss)
    return Modifier.pointerInput(Unit) {
        val threshold = 32.dp.toPx()
        detectVerticalDragGestures(
            onDragStart = { dragDistance = 0f },
            onVerticalDrag = { change, amount ->
                change.consume()
                dragDistance += amount
            },
            onDragEnd = {
                if (shouldDismissDetailSheet(dragDistance, threshold)) currentOnDismiss()
                dragDistance = 0f
            },
            onDragCancel = { dragDistance = 0f }
        )
    }
}

@Composable
private fun rememberPullDownDismissConnection(
    scroll: ScrollState,
    onDismiss: () -> Unit
): NestedScrollConnection {
    val threshold = with(LocalDensity.current) { 64.dp.toPx() }
    val currentOnDismiss by rememberUpdatedState(onDismiss)
    var pullDistance by remember(scroll) { mutableFloatStateOf(0f) }
    return remember(scroll, threshold) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.UserInput) return Offset.Zero
                if (scroll.value > 0) {
                    pullDistance = 0f
                    return Offset.Zero
                }
                if (available.y <= 0f && pullDistance <= 0f) return Offset.Zero

                val previous = pullDistance
                pullDistance = (pullDistance + available.y).coerceAtLeast(0f)
                val consumed = pullDistance - previous
                return Offset(0f, consumed)
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                val shouldDismiss = pullDistance >= threshold
                pullDistance = 0f
                if (shouldDismiss) {
                    currentOnDismiss()
                    return available
                }
                return Velocity.Zero
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun NewsComparisonPage(
    page: ComparisonArticleUi,
    sheetState: SheetState,
    onDismiss: () -> Unit,
    hasFloatingNavigator: Boolean
) {
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
    val headerDismissModifier = downwardDismissModifier(onDismiss)
    val pullDismissConnection = rememberPullDownDismissConnection(scroll, onDismiss)
    val speakerLabels = remember(page.evidence) {
        page.evidence.mapNotNull { it.speakerLabel?.takeIf(String::isNotBlank) }
            .distinct()
    }

    LaunchedEffect(layout, visibleHeight, viewportHeight, scroll.maxValue, sheetState.currentValue) {
        if (focused || bodyEvidence.isEmpty() || sheetState.currentValue == SheetValue.Hidden) {
            return@LaunchedEffect
        }
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
        Column(Modifier.fillMaxWidth().then(headerDismissModifier)) {
            if (speakerLabels.isNotEmpty()) {
                Caption(speakerLabels.joinToString(" · ") { "$it 표현" }, color = Fl.InkDiff)
            }
            Caption("${page.publisher.name} · ${formatNewsTimestamp(page.publishedAt)}")
            Text(title, fontFamily = Fl.Serif, fontSize = 18.sp, lineHeight = 25.sp,
                fontWeight = FontWeight.Bold, color = Fl.Ink)
            SourceLink(onClick = { uriHandler.openUri(page.originalUrl) })
            HorizontalDivider(color = Fl.Line)
        }
        Box(
            Modifier.fillMaxSize().onSizeChanged { viewportHeight = it.height }
                .onGloballyPositioned { coordinates ->
                    visibleHeight = (rootView.height - coordinates.positionInWindow().y).toInt()
                        .coerceIn(0, coordinates.size.height)
                }
        ) {
            Column(
                Modifier.fillMaxSize().nestedScroll(pullDismissConnection)
                    .verticalScroll(scroll, enabled = sheetState.currentValue == SheetValue.Expanded)
            ) {
                Text(body, modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
                    fontFamily = Fl.Serif, fontSize = 15.sp, lineHeight = 27.sp,
                    color = Fl.Ink, onTextLayout = { layout = it })
                if (hasFloatingNavigator) Spacer(Modifier.height(64.dp))
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
