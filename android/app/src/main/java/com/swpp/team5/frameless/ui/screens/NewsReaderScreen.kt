package com.swpp.team5.frameless.ui.screens

import android.os.Build
import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
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
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import com.swpp.team5.frameless.R
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
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
import com.swpp.team5.frameless.data.EconomicLensUi
import com.swpp.team5.frameless.data.EconomicPositionUi
import com.swpp.team5.frameless.data.EconomicViewUi
import com.swpp.team5.frameless.data.FindingUi
import com.swpp.team5.frameless.data.StoredAnalysisState
import com.swpp.team5.frameless.ui.theme.Caption
import com.swpp.team5.frameless.ui.components.ArticleBookmarkIcon
import com.swpp.team5.frameless.ui.components.edgeSwipeBack
import com.swpp.team5.frameless.ui.components.CoachMarkOverlay
import com.swpp.team5.frameless.ui.components.CoachTooltip
import com.swpp.team5.frameless.ui.theme.FilledPill
import com.swpp.team5.frameless.ui.theme.Fl
import com.swpp.team5.frameless.ui.theme.OutlinedPill
import com.swpp.team5.frameless.ui.theme.SourceLink
import com.swpp.team5.frameless.ui.theme.TagPill
import com.swpp.team5.frameless.ui.viewmodel.ArticleReaderState
import com.swpp.team5.frameless.ui.viewmodel.ReaderCoachStep
import com.swpp.team5.frameless.ui.viewmodel.ReaderFocus
import kotlinx.coroutines.android.awaitFrame
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val NewsMarker = Color(0xFFE9E1F5)
private val NewsTargetMarker = Color(0xFFE5D0F2)
private val ComparisonContextMarker = Color(0xFFE8E2F0)

private data class ReaderHighlightColors(
    val finding: Color,
    val selected: Color,
    val context: Color,
    val economic: Color
)

@Composable
private fun readerHighlightColors(): ReaderHighlightColors = if (Fl.IsDark) {
    ReaderHighlightColors(Fl.TintDiff, Color(0xFF634874), Color(0xFF37303F), Fl.TintSame)
} else {
    ReaderHighlightColors(NewsMarker, NewsTargetMarker, ComparisonContextMarker, EconomicPhraseMarker)
}

/** 첫 기사 코치마크 연결. 기본값은 코치마크 없이 동작한다. */
internal data class ReaderCoachUi(
    val step: ReaderCoachStep? = null,
    val onEligible: () -> Unit = {},
    val onFindingOpened: () -> Unit = {},
    val onAnnotationNext: (hasComparisons: Boolean) -> Unit = {},
    val onFinish: () -> Unit = {}
)

@Composable
internal fun NewsReaderScreen(
    state: ArticleReaderState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onAnalysisEnabled: (Boolean) -> Unit,
    onToggleSaved: (String, Boolean) -> Unit,
    savedPending: Boolean,
    saveError: String?,
    onOpenComparisonArticle: (ComparisonArticleUi, Int, String?) -> Unit = { _, _, _ -> },
    onOpenRelatedArticle: (String, Int, String?) -> Unit = { _, _, _ -> },
    coach: ReaderCoachUi = ReaderCoachUi()
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
            state.isAnalysisLoading, state.analysisError, state.isOffline, onBack, onAnalysisEnabled,
            onToggleSaved, savedPending, saveError, state.comparisonFocus,
            state.initialScrollOffset, state.initialSelectedFindingId,
            onOpenComparisonArticle, onOpenRelatedArticle, coach)
    }
}

@Composable
private fun NewsReaderContent(
    article: ArticleDetail,
    analysis: ArticleAnalysisUi?,
    analysisEnabled: Boolean,
    analysisLoading: Boolean,
    analysisError: String?,
    isOffline: Boolean,
    onBack: () -> Unit,
    onAnalysisEnabled: (Boolean) -> Unit,
    onToggleSaved: (String, Boolean) -> Unit,
    savedPending: Boolean,
    saveError: String?,
    comparisonFocus: ReaderFocus?,
    initialScrollOffset: Int,
    initialSelectedFindingId: String?,
    onOpenComparisonArticle: (ComparisonArticleUi, Int, String?) -> Unit,
    onOpenRelatedArticle: (String, Int, String?) -> Unit,
    coach: ReaderCoachUi
) {
    val uriHandler = LocalUriHandler.current
    val visibleFindings = remember(analysis, analysisEnabled) {
        if (analysisEnabled) analysis?.findings.orEmpty().nonOverlappingByScope() else emptyList()
    }
    val titleFindings = visibleFindings.filter { it.scope == "TITLE" }
    val bodyFindings = visibleFindings.filter { it.scope == "BODY" }
    val titleFocus = comparisonFocus?.takeIf { it.scope == "TITLE" }
    val bodyFocus = comparisonFocus?.takeIf { it.scope == "BODY" }
    val economic = analysis?.economic
    val economicViews = if (analysisEnabled) economic?.views.orEmpty() else emptyList()
    var selected by remember(article.id, initialSelectedFindingId) { mutableStateOf<FindingUi?>(null) }
    var initialSelectionApplied by remember(article.id, initialSelectedFindingId) {
        mutableStateOf(false)
    }
    var detailOpen by remember(article.id) { mutableStateOf(false) }
    val readerInk = Fl.Ink
    val highlightColors = readerHighlightColors()
    val titleText = remember(article.title, titleFindings, titleFocus, economicViews, selected?.id, readerInk, highlightColors) {
        annotatedReaderText(
            article.title, titleFindings, titleFocus,
            economicViews.filter { it.scope == "TITLE" }, selected?.takeIf { it.scope == "TITLE" }?.id,
            readerInk, highlightColors
        )
    }
    val bodyText = remember(article.body, bodyFindings, bodyFocus, economicViews, selected?.id, readerInk, highlightColors) {
        annotatedReaderText(
            article.body, bodyFindings, bodyFocus,
            economicViews.filter { it.scope == "BODY" }, selected?.takeIf { it.scope == "BODY" }?.id,
            readerInk, highlightColors
        )
    }
    val density = LocalDensity.current
    val readerView = LocalView.current
    val scroll = remember(article.id) { ScrollState(initialScrollOffset) }
    var bodyLayout by remember(article.id) { mutableStateOf<TextLayoutResult?>(null) }
    var bodyPositionInContent by remember(article.id) { mutableStateOf(Offset.Zero) }
    var comparisonFocusApplied by remember(article.id, comparisonFocus) { mutableStateOf(false) }
    var titleLayout by remember(article.id) { mutableStateOf<TextLayoutResult?>(null) }
    var titleRootOffset by remember(article.id) { mutableStateOf(Offset.Zero) }
    var annotationDockBounds by remember(article.id) { mutableStateOf<Rect?>(null) }
    var readerViewportBounds by remember(article.id) { mutableStateOf<Rect?>(null) }
    var readerRootTopPx by remember(article.id) { mutableFloatStateOf(0f) }
    var userScrollArmed by remember(article.id) { mutableStateOf(false) }
    var directUserDragActive by remember(article.id) { mutableStateOf(false) }
    var lastAnnotationHapticAt by remember(article.id) { mutableStateOf(0L) }
    var guideCodes by remember(article.id) { mutableStateOf<Set<String>?>(null) }
    val coachFinding = titleFindings.firstOrNull() ?: bodyFindings.firstOrNull()
    val openFinding: (FindingUi) -> Unit = { finding ->
        selected = finding
        detailOpen = false
        coach.onFindingOpened()
    }
    // Reader "비교 포인트" entry (specs/features/reader-comparison-summary.md). Article-level like the
    // economic lens, so the marker toggle never inserts or removes it.
    val points = remember(analysis, article.publisher.name) {
        comparisonPoints(article.publisher.name, analysis?.findings.orEmpty())
    }
    val applySelection: (FindingSelection) -> Unit = { next ->
        selected = next.finding
        detailOpen = next.detailOpen
    }
    val onReaderUserScroll = rememberUpdatedState {
        userScrollArmed = true
    }
    val readerScrollConnection = remember(article.id) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput && available.y != 0f) {
                    onReaderUserScroll.value()
                }
                return Offset.Zero
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                if (source == NestedScrollSource.UserInput &&
                    (consumed.y != 0f || available.y != 0f)) {
                    onReaderUserScroll.value()
                }
                return Offset.Zero
            }
        }
    }

    LaunchedEffect(article.id, analysisEnabled, visibleFindings) {
        val visibleIds = visibleFindings.mapTo(mutableSetOf()) { it.id }
        selected = when {
            !analysisEnabled || visibleFindings.isEmpty() -> null
            !initialSelectionApplied -> visibleFindings.firstOrNull {
                it.id == initialSelectedFindingId
            } ?: titleFindings.firstOrNull() ?: bodyFindings.firstOrNull()
            selected?.id !in visibleIds -> titleFindings.firstOrNull() ?: bodyFindings.firstOrNull()
            else -> selected
        }
        if (analysisEnabled && visibleFindings.isNotEmpty()) initialSelectionApplied = true
    }

    LaunchedEffect(coachFinding?.id, comparisonFocus) {
        if (coachFinding != null && comparisonFocus == null) coach.onEligible()
    }
    LaunchedEffect(coach.step, coachFinding?.id) {
        if (coach.step == ReaderCoachStep.TAP_FINDING && coachFinding == null) coach.onFinish()
    }
    LaunchedEffect(coach.step, coachFinding?.id, bodyLayout, bodyPositionInContent, scroll.maxValue) {
        val finding = coachFinding
        if (coach.step != ReaderCoachStep.TAP_FINDING || finding == null || finding.scope != "BODY" ||
            scroll.maxValue == Int.MAX_VALUE) return@LaunchedEffect
        val layout = bodyLayout ?: return@LaunchedEffect
        if (finding.markerRange.start !in article.body.indices) return@LaunchedEffect
        awaitFrame()
        val markerTop = layout.getBoundingBox(finding.markerRange.start).top
        val target = (bodyPositionInContent.y + markerTop - with(density) { 160.dp.toPx() })
            .roundToInt().coerceIn(0, scroll.maxValue)
        scroll.animateScrollTo(target)
    }

    LaunchedEffect(bodyFocus, bodyLayout, bodyPositionInContent, scroll.maxValue) {
        if (comparisonFocusApplied || bodyFocus == null ||
            !bodyFocus.markerRange.isWithin(article.body) ||
            scroll.maxValue == Int.MAX_VALUE) return@LaunchedEffect
        val layout = bodyLayout ?: return@LaunchedEffect
        awaitFrame()
        val markerTop = layout.getBoundingBox(bodyFocus.markerRange.start).top
        val target = (bodyPositionInContent.y + markerTop - with(density) { 96.dp.toPx() })
            .roundToInt().coerceIn(0, scroll.maxValue)
        scroll.scrollTo(target)
        comparisonFocusApplied = true
    }

    LaunchedEffect(
        article.id,
        userScrollArmed,
        analysisEnabled,
        scroll.value,
    ) {
        if (!userScrollArmed || !analysisEnabled ||
            detailOpen || guideCodes != null || coach.step != null) return@LaunchedEffect
        val viewport = readerViewportBounds ?: return@LaunchedEffect
        val measured = bodyFindings.mapIndexedNotNull { index, finding ->
            markerBoundsInScrolledBody(
                bodyLayout, finding, bodyPositionInContent, viewport, scroll.value
            )?.let { bounds ->
                Triple(
                    finding,
                    bounds,
                    PeekMarkerPosition(
                        findingId = finding.id,
                        centerY = bounds.center.y,
                        readingOrder = index,
                        visualLine = bodyLayout?.getLineForOffset(finding.markerRange.start) ?: index
                    )
                )
            }
        }
        val selectionMarkers = spaceSameLinePeekMarkers(
            measured.map { it.third },
            stepPx = with(density) { 20.dp.toPx() }
        )
        val candidateId = selectScrollSyncedPeekCandidate(
            selectionMarkers,
            viewport.top,
            viewport.bottom,
            atScrollEnd = scroll.maxValue > 0 && scroll.value >= scroll.maxValue - 1
        )
        if (candidateId == null || candidateId == selected?.id) return@LaunchedEffect
        val candidate = measured.firstOrNull { it.first.id == candidateId } ?: return@LaunchedEffect
        val now = SystemClock.elapsedRealtime()
        val hapticConstant = annotationHapticFeedbackConstant(Build.VERSION.SDK_INT)
        if (directUserDragActive && selected != null && hapticConstant != null &&
            now - lastAnnotationHapticAt >= AnnotationHapticMinIntervalMillis) {
            readerView.performHapticFeedback(hapticConstant)
            lastAnnotationHapticAt = now
        }
        selected = candidate.first
    }

    LaunchedEffect(analysisEnabled) {
        if (!analysisEnabled) {
            selected = null
            detailOpen = false
            userScrollArmed = false
        }
    }

    BackHandler(enabled = !detailOpen, onBack = onBack)
    BackHandler(enabled = coach.step != null && coach.step != ReaderCoachStep.COMPARISON) {
        coach.onFinish()
    }

    Box(
        Modifier.fillMaxSize()
            .edgeSwipeBack(enabled = !detailOpen && coach.step == null, onBack = onBack)
            .background(Fl.Card)
            .testTag("reader-screen")
            .onGloballyPositioned { readerRootTopPx = it.boundsInRoot().top }
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack, modifier = Modifier.semantics {
                            contentDescription = if (comparisonFocus != null) {
                                "이전 기사로 돌아가기"
                            } else {
                                "기사 목록으로 돌아가기"
                            }
                        }) {
                    Icon(painterResource(R.drawable.ic_portal_arrow_back), contentDescription = null,
                        tint = Fl.Ink, modifier = Modifier.size(23.dp))
                }
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
            if (isOffline) {
                Text(
                    "저장된 사본을 보고 있어요.",
                    color = Fl.Muted,
                    fontSize = 12.sp,
                    modifier = Modifier.fillMaxWidth().background(Fl.Inset)
                        .padding(horizontal = 18.dp, vertical = 9.dp)
                )
            }
            if (saveError != null) {
                Text(saveError, color = Fl.Danger, fontSize = 12.sp,
                    modifier = Modifier.fillMaxWidth().background(Fl.Inset)
                        .padding(horizontal = 18.dp, vertical = 9.dp))
            }
            Column(
                Modifier.weight(1f).fillMaxWidth()
                    .testTag("article-reader-scroll")
                    .pointerInput(article.id) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            directUserDragActive = false
                            try {
                                do {
                                    val event = awaitPointerEvent(PointerEventPass.Initial)
                                    if (event.changes.any {
                                            it.position.y != it.previousPosition.y
                                        }) {
                                        directUserDragActive = true
                                    }
                                } while (event.changes.any { it.pressed })
                            } finally {
                                directUserDragActive = false
                            }
                        }
                    }
                    .nestedScroll(readerScrollConnection)
                    .onGloballyPositioned { readerViewportBounds = it.boundsInRoot() }
                    .verticalScroll(scroll)
                    .padding(horizontal = 22.dp, vertical = 22.dp)
            ) {
                if (comparisonFocus != null) {
                    Row(
                        Modifier.fillMaxWidth()
                            .background(Fl.Inset, RoundedCornerShape(8.dp))
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            Modifier.size(width = 3.dp, height = 30.dp)
                                .background(Fl.InkDiff, RoundedCornerShape(50))
                        )
                        Column {
                            Text("비교 기사에서 이어 읽는 중", fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold, color = Fl.Ink)
                            Text("확인한 표현을 본문에 표시했어요.", fontSize = 11.sp,
                                lineHeight = 16.sp, color = Fl.Muted)
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                }
                Caption("${article.publisher.name} · ${formatNewsTimestamp(article.publishedAt)} · ${article.category.label}")
                ComparisonPointsSection(
                    points = points,
                    onOpen = { applySelection(comparisonPointSelection(it)) },
                    modifier = Modifier.padding(top = 10.dp)
                )
                Spacer(Modifier.height(10.dp))
                FindingText(
                    text = titleText,
                    findings = titleFindings,
                    fontSize = 26,
                    lineHeight = 34,
                    fontWeight = FontWeight.Bold,
                    onFinding = openFinding,
                    onPlainTextTap = {},
                    modifier = Modifier.onGloballyPositioned { titleRootOffset = it.positionInRoot() },
                    onTextLayout = { titleLayout = it }
                )
                SourceLink(onClick = { uriHandler.openUri(article.originalUrl) })
                AnalysisNotice(
                    analysis,
                    analysisLoading,
                    analysisError,
                    fromComparison = comparisonFocus != null
                )
                economic?.let {
                    Spacer(Modifier.height(12.dp))
                    EconomicLensCard(it)
                }
                Spacer(Modifier.height(12.dp))
                HorizontalDivider(color = Fl.Line)
                Spacer(Modifier.height(20.dp))
                FindingText(
                    text = bodyText,
                    findings = bodyFindings,
                    fontSize = 16,
                    lineHeight = 29,
                    fontWeight = FontWeight.Normal,
                    onFinding = openFinding,
                    onPlainTextTap = {},
                    modifier = Modifier.onGloballyPositioned {
                        bodyPositionInContent = it.positionInParent()
                    },
                    onTextLayout = { bodyLayout = it }
                )
                economic?.let { lens ->
                    Spacer(Modifier.height(28.dp))
                    OtherOutletsSection(
                        lens,
                        onOpenArticle = { onOpenRelatedArticle(it, scroll.value, selected?.id) }
                    )
                }
                Spacer(Modifier.height(36.dp))
            }
            selected?.let { finding ->
                AnalysisAnnotationDock(
                    finding = finding,
                    onOpenDetail = { target ->
                        if (target.comparisons.isNotEmpty()) {
                            selected = target
                            detailOpen = true
                        }
                    },
                    onShowLabelGuide = { guideCodes = finding.categories.toSet() },
                    modifier = Modifier.onGloballyPositioned { annotationDockBounds = it.boundsInRoot() }
                )
            }
        }

        val focusViewport = readerViewportBounds
        AnimatedVisibility(
            visible = analysisEnabled && bodyFindings.isNotEmpty() && userScrollArmed &&
                scroll.isScrollInProgress && !detailOpen && guideCodes == null &&
                coach.step == null && focusViewport != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopEnd)
        ) {
            val focusY = focusViewport?.let {
                it.top - readerRootTopPx + it.height * PeekAnchorFraction
            } ?: 0f
            Box(
                Modifier.offset { IntOffset(0, focusY.roundToInt()) }
                    .padding(end = 8.dp)
                    .width(30.dp)
                    .height(2.dp)
                    .background(Fl.Accent.copy(alpha = 0.34f), RoundedCornerShape(50))
            )
        }

        when (coach.step) {
            ReaderCoachStep.TAP_FINDING -> {
                val finding = coachFinding
                val spotlight = finding?.let {
                    if (it.scope == "TITLE") markerBoundsInRoot(titleLayout, it, titleRootOffset)
                    else markerBoundsInScrolledBody(
                        bodyLayout, it, bodyPositionInContent,
                        readerViewportBounds ?: return@let null,
                        scroll.value
                    )
                }
                if (finding != null && spotlight != null) {
                    CoachMarkOverlay(
                        spotlightInRoot = spotlight,
                        stepLabel = "1 / 3",
                        title = "보라색 표현을 눌러보세요",
                        body = "같은 사건을 다룬 다른 언론사 기사와 비교했을 때 표현이 다른 부분이에요. " +
                            "오른쪽 위 '표현 분석'을 끄면 표시가 사라져요.",
                        primaryLabel = "다음",
                        onPrimary = { openFinding(finding) },
                        secondaryLabel = "건너뛰기",
                        onSecondary = coach.onFinish,
                        onSpotlightTap = { openFinding(finding) }
                    )
                }
            }
            ReaderCoachStep.ANNOTATION_DOCK -> {
                val finding = selected
                if (finding != null && !detailOpen) {
                    val hasComparisons = finding.comparisons.isNotEmpty()
                    CoachMarkOverlay(
                        spotlightInRoot = annotationDockBounds,
                        stepLabel = if (hasComparisons) "2 / 3" else "2 / 2",
                        title = "어떤 차이인지 확인해요",
                        body = "보라색 라벨은 어떤 종류의 표현 차이인지 알려줘요. " +
                            "라벨 옆의 ? 를 누르면 라벨 기준을 볼 수 있어요.",
                        primaryLabel = if (hasComparisons) "다음" else "완료",
                        onPrimary = {
                            if (hasComparisons) detailOpen = true
                            coach.onAnnotationNext(hasComparisons)
                        },
                        secondaryLabel = "건너뛰기",
                        onSecondary = coach.onFinish
                    )
                }
            }
            else -> Unit
        }
    }

    guideCodes?.let { codes ->
        ExpressionLabelGuideDialog(highlightCodes = codes, onDismiss = { guideCodes = null })
    }

    selected?.takeIf { detailOpen }?.let { finding ->
        FindingDetailSheet(
            finding = finding,
            onDismiss = {
                detailOpen = false
                coach.onFinish()
            },
            onOpenArticle = { onOpenComparisonArticle(it, scroll.value, finding.id) },
            coachVisible = coach.step == ReaderCoachStep.COMPARISON,
            onCoachDone = coach.onFinish,
            sourcePublisher = article.publisher.name
        )
    }
}

@Composable
private fun AnalysisNotice(
    analysis: ArticleAnalysisUi?,
    loading: Boolean,
    error: String?,
    fromComparison: Boolean
) {
    val message = when {
        loading -> "저장된 표현 분석을 불러오는 중이에요. 기사는 그대로 읽을 수 있어요."
        error != null -> "표현 분석을 불러오지 못했어요. 기사는 그대로 읽을 수 있어요."
        analysis?.state == StoredAnalysisState.UPDATING -> "표현 분석을 준비 중이에요."
        analysis?.state == StoredAnalysisState.FAILED || analysis?.state == StoredAnalysisState.NOT_STARTED ->
            "아직 표시할 표현 분석이 없어요."
        analysis?.state == StoredAnalysisState.PARTIAL -> "검토가 끝난 표현만 표시하고 있어요."
        analysis?.state == StoredAnalysisState.READY && analysis.findings.isEmpty() && fromComparison ->
            "이 기사의 별도 표현 분석은 없어요."
        analysis?.state == StoredAnalysisState.READY && analysis.findings.isEmpty() ->
            "분석은 완료되었지만 현재 표시할 표현 특징을 확인하지 못했어요."
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
    onPlainTextTap: () -> Unit = {},
    modifier: Modifier = Modifier,
    onTextLayout: (TextLayoutResult) -> Unit = {}
) {
    var layout by remember(text) { mutableStateOf<TextLayoutResult?>(null) }
    val currentLayout = rememberUpdatedState(layout)
    Text(
        text = text,
        modifier = modifier.fillMaxWidth().pointerInput(text, findings) {
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
        onTextLayout = { layout = it; onTextLayout(it) }
    )
}

internal fun annotatedFindings(text: String, findings: List<FindingUi>, markerColor: Color = NewsMarker): AnnotatedString = buildAnnotatedString {
    append(text)
    findings.forEach { finding ->
        if (finding.markerRange.isWithin(text)) {
            addStringAnnotation("finding", finding.id, finding.markerRange.start, finding.markerRange.end)
            addStyle(
                SpanStyle(background = markerColor, textDecoration = TextDecoration.Underline,
                    fontWeight = FontWeight.SemiBold),
                finding.markerRange.start,
                finding.markerRange.end
            )
        }
    }
}

private fun annotatedReaderText(
    text: String,
    findings: List<FindingUi>,
    comparisonFocus: ReaderFocus?,
    economicViews: List<EconomicViewUi> = emptyList(),
    selectedFindingId: String? = null,
    ink: Color,
    colors: ReaderHighlightColors
): AnnotatedString = buildAnnotatedString {
    append(annotatedFindings(text, findings, colors.finding))
    findings.firstOrNull { it.id == selectedFindingId }?.markerRange
        ?.takeIf { it.isWithin(text) }
        ?.let { marker ->
            addStyle(
                SpanStyle(
                    background = colors.selected,
                    fontWeight = FontWeight.SemiBold,
                    textDecoration = TextDecoration.Underline
                ),
                marker.start,
                marker.end
            )
        }
    economicViews.forEach { view ->
        view.sentenceRange?.takeIf { it.isWithin(text) }?.let {
            addStyle(SpanStyle(textDecoration = TextDecoration.Underline, color = ink), it.start, it.end)
        }
        view.phraseRange?.takeIf { it.isWithin(text) }?.let {
            addStyle(SpanStyle(background = colors.economic, fontWeight = FontWeight.SemiBold), it.start, it.end)
        }
    }
    comparisonFocus?.markerRange?.takeIf { it.isWithin(text) }?.let { marker ->
        addStyle(
            SpanStyle(
                background = colors.context,
                fontWeight = FontWeight.SemiBold,
                textDecoration = TextDecoration.Underline
            ),
            marker.start,
            marker.end
        )
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
internal fun AnalysisAnnotationDock(
    finding: FindingUi,
    onOpenDetail: (FindingUi) -> Unit,
    modifier: Modifier = Modifier,
    onShowLabelGuide: (() -> Unit)? = null
) {
    val content = remember(finding) { analysisPeekContent(finding) }
    val comparisonCount = content.comparisons.size + content.remainingPublisherCount
    val hasComparisons = comparisonCount > 0
    Column(
        modifier.fillMaxWidth().height(118.dp)
            .background(Fl.Card)
            .testTag("analysis-annotation-dock")
    ) {
        HorizontalDivider(color = Fl.Line)
        Row(
            Modifier.fillMaxSize()
                .then(
                    if (hasComparisons) {
                        Modifier.clickable(
                            onClickLabel = "비교 상세 보기",
                            role = Role.Button,
                            onClick = { onOpenDetail(finding) }
                        )
                    } else Modifier
                )
                .padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.width(3.dp).height(68.dp)
                    .background(Fl.Accent.copy(alpha = 0.72f), RoundedCornerShape(50))
            )
            Spacer(Modifier.width(12.dp))
            Column(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        finding.categories.map(::categoryLabel).distinct().joinToString(" · "),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = Fl.Muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (hasComparisons) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("다른 보도 ${comparisonCount}곳", fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold, color = Fl.Accent, maxLines = 1)
                            Icon(painterResource(R.drawable.ic_portal_chevron_right), contentDescription = null,
                                tint = Fl.Accent, modifier = Modifier.padding(start = 3.dp).size(14.dp))
                        }
                    }
                }
                Text(
                    "“${finding.markerText}”",
                    fontFamily = Fl.Serif,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Fl.InkDiff,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    finding.explanation,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    color = Fl.Ink2,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (onShowLabelGuide != null) LabelGuideButton(onShowLabelGuide)
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun FindingDetailSheet(
    finding: FindingUi,
    onDismiss: () -> Unit,
    onOpenArticle: (ComparisonArticleUi) -> Unit = {},
    coachVisible: Boolean = false,
    onCoachDone: () -> Unit = {},
    sourcePublisher: String = ""
) {
    if (finding.comparisons.isEmpty()) return
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val pagerState = rememberPagerState(pageCount = { finding.comparisons.size })
    val comparisonOverview = finding.comparisonOverview.ifBlank { finding.explanation }
    val scope = rememberCoroutineScope()
    var dismissing by remember { mutableStateOf(false) }
    val closeSheet: (() -> Unit) -> Unit = { afterClose ->
        if (!dismissing) {
            dismissing = true
            scope.launch {
                sheetState.hide()
                onDismiss()
                afterClose()
            }
        }
    }
    val dismissSheet: () -> Unit = { closeSheet {} }
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
        containerColor = HomeTokens.OffWhite,
        contentColor = Fl.Ink
    ) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.93f)) {
            Column(
                Modifier.fillMaxWidth().then(sectionDismissModifier)
                    .padding(start = 20.dp, end = 8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("같은 내용을 다른 기사에서는", fontFamily = Fl.Serif, fontSize = 17.sp,
                        fontWeight = FontWeight.Bold, color = HomeTokens.Navy, modifier = Modifier.weight(1f))
                    IconButton(
                        onClick = dismissSheet,
                        modifier = Modifier.semantics { contentDescription = "비교 상세 닫기" }
                    ) {
                        Icon(painterResource(R.drawable.ic_portal_close), contentDescription = null,
                            tint = Fl.Muted, modifier = Modifier.size(22.dp))
                    }
                }
                if (comparisonOverview.isNotBlank()) {
                    // Stored overview, unchanged; set as a quiet editorial note with a navy rule.
                    Row(
                        Modifier.fillMaxWidth().height(IntrinsicSize.Min)
                            .padding(end = 12.dp, bottom = 12.dp)
                    ) {
                        Box(Modifier.width(2.dp).fillMaxHeight()
                            .background(HomeTokens.Navy.copy(alpha = 0.55f), RoundedCornerShape(1.dp)))
                        Spacer(Modifier.width(10.dp))
                        Text(
                            comparisonOverview,
                            fontSize = 13.sp,
                            lineHeight = 20.sp,
                            color = Fl.Ink2
                        )
                    }
                }
                if (coachVisible) {
                    CoachTooltip(
                        stepLabel = "3 / 3",
                        title = "다른 언론사는 이렇게 썼어요",
                        body = "아래 기사에서 밑줄 친 문장이 같은 내용을 다룬 부분이에요.",
                        primaryLabel = "완료",
                        onPrimary = onCoachDone,
                        secondaryLabel = null,
                        onSecondary = {},
                        modifier = Modifier.padding(end = 12.dp, bottom = 12.dp),
                        extra = {
                            CoachTermRow("이 기사 읽기", "앱에서 이 기사 전체를 읽어요")
                            CoachTermRow("원문 보기", "언론사 사이트에서 원문을 열어요")
                        }
                    )
                }
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { index ->
                    val page = finding.comparisons[index]
                    NewsComparisonPage(
                        page = page,
                        pair = remember(finding, page, sourcePublisher) {
                            expressionPairFor(finding, sourcePublisher, page)
                        },
                        sheetState = sheetState,
                        onDismiss = dismissSheet,
                        onOpenArticle = { page -> closeSheet { onOpenArticle(page) } },
                        hasFloatingNavigator = finding.comparisons.size > 1
                    )
                }
                if (finding.comparisons.size > 1) {
                    val hasPrevious = pagerState.currentPage > 0
                    val hasNext = pagerState.currentPage < finding.comparisons.lastIndex
                    Row(
                        Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp)
                            .shadow(6.dp, RoundedCornerShape(50))
                            .background(HomeTokens.OffWhite.copy(alpha = 0.95f), RoundedCornerShape(50))
                            .border(1.dp, HomeTokens.Hairline, RoundedCornerShape(50))
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
                            Icon(painterResource(R.drawable.ic_portal_chevron_left), contentDescription = null,
                                tint = if (hasPrevious) HomeTokens.Navy else HomeTokens.Hairline,
                                modifier = Modifier.size(23.dp))
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
                            Icon(painterResource(R.drawable.ic_portal_chevron_right), contentDescription = null,
                                tint = if (hasNext) HomeTokens.Navy else HomeTokens.Hairline,
                                modifier = Modifier.size(23.dp))
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
    pair: ExpressionPairUi?,
    sheetState: SheetState,
    onDismiss: () -> Unit,
    onOpenArticle: (ComparisonArticleUi) -> Unit,
    hasFloatingNavigator: Boolean
) {
    val uriHandler = LocalUriHandler.current
    val density = LocalDensity.current
    val rootView = LocalView.current.rootView
    val scroll = rememberScrollState()
    val bodyEvidence = page.evidence.filter { it.scope == "BODY" }
    val titleEvidence = page.evidence.filter { it.scope == "TITLE" }
    val highlightColors = readerHighlightColors()
    val body = remember(page, highlightColors) { annotatedTarget(page.body, bodyEvidence, highlightColors) }
    val title = remember(page, highlightColors) { annotatedTarget(page.title, titleEvidence, highlightColors) }
    var layout by remember(page.articleId) { mutableStateOf<TextLayoutResult?>(null) }
    var viewportHeight by remember(page.articleId) { mutableIntStateOf(0) }
    var visibleHeight by remember(page.articleId) { mutableIntStateOf(0) }
    var focused by rememberSaveable(page.articleId) { mutableStateOf(false) }
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

    Column(Modifier.fillMaxSize().padding(bottom = 8.dp)) {
        // The pair sits edge to edge above the article; it belongs to the drag-to-dismiss header region.
        Column(Modifier.fillMaxWidth().then(headerDismissModifier)) {
            pair?.let { ExpressionPairPanel(it) }
            Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 12.dp)) {
                if (speakerLabels.isNotEmpty()) {
                    Caption(speakerLabels.joinToString(" · ") { "$it 표현" }, color = Fl.InkDiff)
                }
                Caption("${page.publisher.name} · ${formatNewsTimestamp(page.publishedAt)}")
                Text(title, fontFamily = Fl.Serif, fontSize = 18.sp, lineHeight = 25.sp,
                    fontWeight = FontWeight.Bold, color = Fl.Ink)
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilledPill(text = "이 기사 읽기", onClick = { onOpenArticle(page) })
                    SourceLink(onClick = { uriHandler.openUri(page.originalUrl) })
                }
                HorizontalDivider(color = HomeTokens.Hairline)
            }
        }
        Box(
            Modifier.fillMaxSize().padding(horizontal = 20.dp).onSizeChanged { viewportHeight = it.height }
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

private fun annotatedTarget(text: String, evidence: List<com.swpp.team5.frameless.data.ComparisonEvidenceUi>, colors: ReaderHighlightColors) =
    buildAnnotatedString {
        append(text)
        evidence.forEach { row ->
            if (row.sentenceRange.isWithin(text)) {
                addStyle(SpanStyle(background = colors.finding, textDecoration = TextDecoration.Underline),
                    row.sentenceRange.start, row.sentenceRange.end)
            }
            if (row.markerRange.isWithin(text)) {
                addStyle(SpanStyle(background = colors.selected, fontWeight = FontWeight.Bold),
                    row.markerRange.start, row.markerRange.end)
            }
        }
    }

/** 표현 구간의 루트 좌표 사각형. 레이아웃이 없거나 구간이 맞지 않으면 null. */
private fun markerBoundsInRoot(layout: TextLayoutResult?, finding: FindingUi, origin: Offset): Rect? {
    val textLayout = layout ?: return null
    val range = finding.markerRange
    if (range.start < 0 || range.end <= range.start ||
        range.end > textLayout.layoutInput.text.length) return null
    return textLayout.getPathForRange(range.start, range.end).getBounds().translate(origin)
}

/** 스크롤 콘텐츠 안의 표현 좌표를 현재 Reader 화면 좌표로 변환한다. */
private fun markerBoundsInScrolledBody(
    layout: TextLayoutResult?,
    finding: FindingUi,
    bodyPositionInContent: Offset,
    viewport: Rect,
    scrollValue: Int
): Rect? = markerBoundsInRoot(
    layout,
    finding,
    Offset(
        x = viewport.left + bodyPositionInContent.x,
        y = viewport.top + bodyPositionInContent.y - scrollValue
    )
)

@Composable
private fun CoachTermRow(term: String, meaning: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(term, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Fl.Ink,
            modifier = Modifier.width(76.dp))
        Text(meaning, fontSize = 13.sp, lineHeight = 19.sp, color = Fl.Ink2)
    }
}

private val EconomicInk: Color @Composable get() = Fl.InkSame
private val EconomicTint: Color @Composable get() = Fl.TintSame.copy(alpha = 0.5f)
private val EconomicPhraseMarker = Color(0xFFD5ECE8)
/** How a view sees the event: its evaluation, or the move it reports when it evaluates nothing. */
internal fun economicStanceLabel(stance: String, direction: String? = null): String = when (stance) {
    "CONCERN" -> "우려"
    "OPTIMISM" -> "기대"
    "LIMITED" -> "제한적"
    else -> when (direction) {
        "UP" -> "상승"
        "DOWN" -> "하락"
        "SWING" -> "등락"
        else -> "중립"
    }
}

/** The article's stance toward the event (specs/features/economic-lens.md, Position). */
internal fun economicPositionLabel(stance: String): String = when (stance) {
    "FAVOR" -> "찬성"
    "AGAINST" -> "반대"
    "CONCERN" -> "우려"
    "OPTIMISM" -> "기대"
    else -> "중립"
}

@Composable
private fun EconomicStanceChip(label: String) {
    Text(
        label,
        fontSize = 11.sp, fontWeight = FontWeight.Bold, color = EconomicInk,
        modifier = Modifier
            .border(1.dp, EconomicInk.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 1.dp)
    )
}

/** Stance label, then the summary line. */
@Composable
private fun EconomicPositionLine(position: EconomicPositionUi, fontSize: Int = 14) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        EconomicStanceChip(economicPositionLabel(position.stance))
        Text(position.summary, fontSize = fontSize.sp, lineHeight = (fontSize + 6).sp, color = Fl.Ink)
    }
}

@Composable
private fun EconomicViewRow(view: EconomicViewUi) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text("«${view.phrase}»", fontFamily = Fl.Serif, fontSize = 15.sp, lineHeight = 22.sp,
            fontWeight = FontWeight.Bold, color = Fl.Ink)
        Text(
            economicStanceLabel(view.stance, view.direction),
            fontSize = 11.sp, fontWeight = FontWeight.Bold, color = EconomicInk,
            modifier = Modifier.padding(top = 3.dp)
                .border(1.dp, EconomicInk.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                .padding(horizontal = 6.dp, vertical = 1.dp)
        )
    }
}

/** The event and this article's view of it (specs/features/economic-lens.md). */
@Composable
private fun EconomicLensCard(lens: EconomicLensUi) {
    Column(
        Modifier.fillMaxWidth().background(EconomicTint, RoundedCornerShape(8.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp)
            .semantics { contentDescription = "경제 관점" }
    ) {
        Text("경제 관점", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = EconomicInk)
        Spacer(Modifier.height(6.dp))
        Text("사건", fontSize = 11.sp, color = Fl.Muted)
        Text(lens.event, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Bold, color = Fl.Ink)
        Spacer(Modifier.height(8.dp))
        Text("이 기사의 시각", fontSize = 11.sp, color = Fl.Muted)
        val position = lens.position
        if (position != null) {
            Spacer(Modifier.height(4.dp))
            EconomicPositionLine(position)
        } else if (lens.views.isEmpty()) {
            Text("이 기사에서는 시각을 읽어내지 못했어요.", fontSize = 13.sp, color = Fl.Muted)
        } else lens.views.forEach { EconomicViewRow(it) }
    }
}

/** How the other outlets on the same event see it: a table of publisher and stance with its summary line. */
@Composable
private fun OtherOutletsSection(lens: EconomicLensUi, onOpenArticle: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().semantics { contentDescription = "다른 언론사의 시각" }) {
        HorizontalDivider(color = EconomicInk, thickness = 2.dp)
        Spacer(Modifier.height(12.dp))
        Text("다른 언론사는 이 사건을 이렇게 봐요", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = EconomicInk)
        Text(lens.event, fontSize = 12.sp, color = Fl.Muted)
        if (lens.others.isEmpty()) {
            Text("같은 사건을 다룬 다른 기사의 시각이 아직 없어요.", fontSize = 13.sp, color = Fl.Muted,
                modifier = Modifier.padding(top = 10.dp))
            return@Column
        }
        Spacer(Modifier.height(10.dp))
        Row(
            Modifier.fillMaxWidth().background(EconomicTint, RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Text("언론사", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = EconomicInk,
                modifier = Modifier.width(OutletColumn))
            Text("입장 · 한 줄 정리", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = EconomicInk)
        }
        lens.others.forEach { outlet ->
            Row(
                Modifier.fillMaxWidth()
                    .clickable { onOpenArticle(outlet.articleId) }
                    .semantics { role = Role.Button; contentDescription = "${outlet.publisher.name} 기사 열기" }
                    .padding(horizontal = 10.dp, vertical = 10.dp)
            ) {
                Row(Modifier.width(OutletColumn).padding(end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(outlet.publisher.name, fontSize = 13.sp, lineHeight = 19.sp,
                        fontWeight = FontWeight.Bold, color = Fl.Ink, modifier = Modifier.weight(1f, fill = false))
                    Icon(painterResource(R.drawable.ic_portal_chevron_right), contentDescription = null,
                        tint = Fl.Muted, modifier = Modifier.size(14.dp))
                }
                Column(Modifier.weight(1f)) {
                    val position = outlet.position
                    if (position != null) EconomicPositionLine(position, fontSize = 13)
                    else outlet.views.forEach { EconomicViewRow(it) }
                }
            }
            HorizontalDivider(color = Fl.Line)
        }
    }
}

private val OutletColumn = 92.dp

internal fun categoryLabel(code: String): String = when (code) {
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
        IconButton(onClick = onBack, modifier = Modifier.padding(start = 16.dp, top = 10.dp)
            .semantics { contentDescription = "기사 목록으로 돌아가기" }) {
            Icon(painterResource(R.drawable.ic_portal_arrow_back), contentDescription = null,
                tint = Fl.Ink, modifier = Modifier.size(23.dp))
        }
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
