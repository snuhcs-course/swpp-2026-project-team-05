package com.swpp.team5.frameless.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swpp.team5.frameless.data.ReaderArticle
import com.swpp.team5.frameless.data.AnalysisStatus
import com.swpp.team5.frameless.data.ReaderExpressionDifference
import com.swpp.team5.frameless.data.ReaderI1Star
import com.swpp.team5.frameless.data.ReaderPair
import com.swpp.team5.frameless.data.ReaderResult
import com.swpp.team5.frameless.data.ReaderSpan
import com.swpp.team5.frameless.ui.components.RubricLabels
import com.swpp.team5.frameless.ui.theme.Caption
import com.swpp.team5.frameless.ui.theme.Fl
import com.swpp.team5.frameless.ui.theme.HRule
import com.swpp.team5.frameless.ui.theme.OutlinedPill
import com.swpp.team5.frameless.ui.theme.SourceLink
import kotlinx.coroutines.android.awaitFrame
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** The source article remains intact. Badges are UI annotations outside its quoted sentences. */
private val ReaderExpression = Color(0xFFE9E1F5)
private val ReaderMarker = Color(0xFFE5D0F2)

/** One article can align multiple content units to the same wording contrast. */
private fun List<ReaderExpressionDifference>.forComparisonSheet(): List<ReaderExpressionDifference> {
    val displayed = mutableListOf<ReaderExpressionDifference>()
    for (difference in this) {
        val repeated = displayed.indexOfFirst { earlier ->
            earlier.code == difference.code &&
                earlier.sourceMarkerSpan.start < difference.sourceMarkerSpan.end &&
                difference.sourceMarkerSpan.start < earlier.sourceMarkerSpan.end &&
                earlier.relatedMarkerSpan.start < difference.relatedMarkerSpan.end &&
                difference.relatedMarkerSpan.start < earlier.relatedMarkerSpan.end
        }
        if (repeated < 0) {
            displayed.add(difference)
        } else {
            // Prefer a concrete, shorter explanation for the same marked words.
            val previous = displayed[repeated]
            if (difference.note.isNotBlank() &&
                (previous.note.isBlank() || difference.note.trim().length < previous.note.trim().length)) {
                displayed[repeated] = difference
            }
        }
    }
    return displayed
}

internal data class ReaderTarget(
    val sentenceId: String,
    val span: ReaderSpan,
    val pairs: List<ReaderPair>
) {
    val key: String get() = "$sentenceId:${span.start}:${span.end}"
    val codes: List<String> get() = pairs.flatMap { it.expressionDifferences.map { difference -> difference.code } }
        .distinct().sorted()
}

/**
 * Only sentences with A–G evidence are visible tap targets. Group all verified
 * differences for the same source sentence so multiple differing outlets can
 * be swiped, while neutral alignments remain outside the comparison sheet.
 */
internal fun ReaderResult.targets(): List<ReaderTarget> {
    return pairs.filter { it.expressionDifferences.isNotEmpty() }
        .groupBy { it.sourceSentenceId }
        .map { (sentenceId, expressionPairs) ->
            val span = ReaderSpan(
                start = expressionPairs.minOf { it.sourceSpan.start },
                end = expressionPairs.maxOf { it.sourceSpan.end }
            )
            ReaderTarget(sentenceId, span, expressionPairs.distinctBy { it.id })
        }
        .sortedWith(compareBy({ it.span.start }, { it.span.end }))
}

/** Overlapping positions cannot have an unambiguous tap target; keep the first exact sentence. */
internal fun List<ReaderTarget>.nonOverlapping(): List<ReaderTarget> {
    var end = 0
    return sortedWith(compareBy({ it.span.start }, { it.span.end })).filter { target ->
        if (target.span.start < end) false else {
            end = target.span.end
            true
        }
    }
}

/** Build one native text layout so body line breaks and paragraph spacing follow the extraction. */
internal fun sourceReaderText(
    reader: ReaderResult,
    targets: List<ReaderTarget>,
    showExpressions: Boolean = true
): AnnotatedString {
    val byStart = if (showExpressions) {
        targets.filter { it.codes.isNotEmpty() }.associateBy { it.span.start }
    } else emptyMap()
    val starsByEnd = reader.i1Stars.groupBy { it.sourceSpan.end }
    val body = reader.sourceArticle.body
    return buildAnnotatedString {
        var active: ReaderTarget? = null
        for (offset in 0..body.length) {
            val ending = active?.takeIf { it.span.end == offset }
            if (ending != null) {
                pop() // style
                pop() // click annotation
                active = null
            }
            starsByEnd[offset]?.forEach { star ->
                pushStringAnnotation("i1_star", star.sourceSentenceId)
                pushStyle(SpanStyle(color = Fl.Danger, fontWeight = FontWeight.Bold))
                // Keep the star with the sentence ending, before any following paragraph break.
                append("\u2060★")
                pop()
                pop()
            }
            if (ending?.codes?.isNotEmpty() == true) {
                pushStringAnnotation("reader_target", ending.key)
                pushStyle(SpanStyle(color = Fl.InkDiff, fontWeight = FontWeight.Bold))
                append(" 〔${ending.codes.joinToString("·")}〕")
                pop()
                pop()
            }
            val starting = byStart[offset]
            if (starting != null) {
                active = starting
                pushStringAnnotation("reader_target", starting.key)
                pushStyle(SpanStyle(
                    background = ReaderExpression,
                    textDecoration = TextDecoration.Underline
                ))
            }
            if (offset < body.length) append(body[offset])
        }
    }
}

@Composable
internal fun ReaderScreen(
    reader: ReaderResult,
    analysisStatus: AnalysisStatus?,
    onNewArticle: () -> Unit
) {
    val uriHandler = LocalUriHandler.current
    val showExpressions = analysisStatus?.executionStatus != "failed"
    val targets = remember(reader, showExpressions) {
        if (showExpressions) reader.targets().nonOverlapping() else emptyList()
    }
    val statusNotice = readerStatusNotice(reader, analysisStatus)
    val body = remember(reader, targets, showExpressions) {
        sourceReaderText(reader, targets, showExpressions)
    }
    var selected by remember(reader) { mutableStateOf<ReaderTarget?>(null) }
    var selectedStar by remember(reader) { mutableStateOf<ReaderI1Star?>(null) }
    var bodyLayout by remember(reader) { mutableStateOf<TextLayoutResult?>(null) }
    val currentBodyLayout = rememberUpdatedState(bodyLayout)
    val bodyScroll = rememberScrollState()

    BackHandler(enabled = selected == null && selectedStar == null, onBack = onNewArticle)

    Column(modifier = Modifier.fillMaxSize().background(Fl.Card)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Caption("FrameLESS")
                Text("기사 읽기", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Fl.Ink)
            }
            OutlinedPill("새 기사", onClick = onNewArticle)
        }
        HRule()
        if (statusNotice != null) {
            Column(
                modifier = Modifier.fillMaxWidth().background(Fl.Inset).padding(horizontal = 18.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Caption(statusNotice.headline, color = Fl.Danger)
                Caption(statusNotice.detail)
            }
        }
        Column(modifier = Modifier.fillMaxSize().verticalScroll(bodyScroll).padding(horizontal = 22.dp, vertical = 24.dp)) {
            Caption("기준 기사")
            Spacer(Modifier.height(7.dp))
            Text(
                reader.sourceArticle.title,
                fontSize = 25.sp,
                lineHeight = 32.sp,
                fontWeight = FontWeight.Bold,
                color = Fl.Ink,
                fontFamily = Fl.Serif
            )
            reader.sourceArticle.url?.let { url ->
                Spacer(Modifier.height(6.dp))
                SourceLink(onClick = { uriHandler.openUri(url) }, label = "기사 원문 열기")
            }
            Spacer(Modifier.height(14.dp))
            if (targets.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(" ", modifier = Modifier.background(ReaderExpression, RoundedCornerShape(3.dp)).width(16.dp))
                    Caption("표현 차이 A–G · 눌러 비교 기사 보기")
                }
            }
            if (reader.i1Stars.isNotEmpty()) {
                Caption("★ 문장 속 두드러진 내용 · 별표를 눌러 보기", color = Fl.Danger)
            } else if (reader.i1Status == "not_computed") {
                Caption("별표 표시는 이번 분석에서 확인하지 않았어요.")
            } else if (reader.i1Status == "withheld") {
                Caption("별표 표시는 근거가 부족해 보류했어요.")
            }
            if (targets.isEmpty()) {
                Spacer(Modifier.height(8.dp))
                Caption("이번 분석에서 표시할 A–G 표현 차이가 없어요.")
            }
            Spacer(Modifier.height(22.dp))
            HorizontalDivider(color = Fl.Line)
            Spacer(Modifier.height(20.dp))
            Text(
                text = body,
                modifier = Modifier.fillMaxWidth().pointerInput(body, targets, reader.i1Stars) {
                    detectTapGestures { position: Offset ->
                        val offset = currentBodyLayout.value?.getOffsetForPosition(position) ?: return@detectTapGestures
                        val starId = body.getStringAnnotations("i1_star", offset, (offset + 1).coerceAtMost(body.length))
                            .firstOrNull()?.item
                        if (starId != null) {
                            selectedStar = reader.i1Stars.firstOrNull { it.sourceSentenceId == starId }
                            selected = null
                            return@detectTapGestures
                        }
                        val key = body.getStringAnnotations("reader_target", offset, (offset + 1).coerceAtMost(body.length))
                            .firstOrNull()?.item ?: return@detectTapGestures
                        selected = targets.firstOrNull { it.key == key }
                        selectedStar = null
                    }
                },
                fontSize = 16.sp,
                lineHeight = 28.sp,
                color = Fl.Ink,
                fontFamily = Fl.Serif,
                onTextLayout = { bodyLayout = it }
            )
            Spacer(Modifier.height(32.dp))
        }
    }

    selected?.let { target ->
        key(target.key) {
            ReaderComparisonSheet(reader, target, onDismiss = { selected = null })
        }
    }
    selectedStar?.let { star ->
        key(star.sourceSentenceId) {
            ReaderI1Sheet(reader, star, onDismiss = { selectedStar = null })
        }
    }
}

private data class RelatedPage(val article: ReaderArticle, val pairs: List<ReaderPair>)

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun ReaderI1Sheet(reader: ReaderResult, star: ReaderI1Star, onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Fl.Card,
        contentColor = Fl.Ink
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("★ 이번 비교에서 두드러진 내용", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Fl.Ink)
            Caption("별표는 문장 전체가 아니라 아래 내용에만 해당해요.")
            star.evidence.forEach { evidence ->
                Column(
                    modifier = Modifier.fillMaxWidth().background(Fl.Inset, RoundedCornerShape(10.dp))
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    Text(evidence.content, fontSize = 15.sp, lineHeight = 23.sp,
                        fontWeight = FontWeight.SemiBold, color = Fl.Ink)
                    Caption("기사 원문")
                    Text(reader.sourceArticle.body.substring(
                        evidence.sourceEvidenceSpan.start, evidence.sourceEvidenceSpan.end),
                        fontSize = 14.sp, lineHeight = 22.sp, fontFamily = Fl.Serif, color = Fl.Ink2)
                }
            }
            Caption("이번에 확인한 비교 기사 ${star.checkedArticleCount}건에서는 같은 내용을 찾지 못했어요.")
            Caption("확인한 기사 범위의 관찰이에요. 사실 여부나 다른 모든 기사를 판정한 결과는 아니에요.")
            Spacer(Modifier.height(16.dp))
        }
    }
}

internal fun sheetInstruction(pageCount: Int, expanded: Boolean): String = when {
    pageCount > 1 && expanded -> "좌우로 다른 기사 보기 · 손잡이를 아래로 끌어 접기"
    pageCount > 1 -> "좌우로 다른 기사 보기 · 위로 끌어 펼치기"
    expanded -> "손잡이를 아래로 끌어 중간 높이로 접기"
    else -> "위로 끌어 펼치기"
}

/** Center the selected sentence inside the part of the sheet currently visible. */
internal fun centeredReaderOffset(sentenceCenter: Float, visibleHeight: Int, maxScroll: Int): Int =
    (sentenceCenter - visibleHeight / 2f).roundToInt().coerceIn(0, maxScroll)

/** Make even a match near the article's end reachable in a short sheet peek. */
internal fun readerTailPadding(viewportHeight: Int, visibleHeight: Int): Int =
    viewportHeight - visibleHeight.coerceIn(0, viewportHeight) / 2

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun ReaderComparisonSheet(
    reader: ReaderResult,
    target: ReaderTarget,
    onDismiss: () -> Unit
) {
    val pages = remember(reader, target) {
        target.pairs.groupBy { it.articleId }.mapNotNull { (id, pairs) ->
            reader.article(id)?.let { RelatedPage(it, pairs) }
        }.sortedWith(
            compareByDescending<RelatedPage> { page ->
                page.pairs.any { it.expressionDifferences.isNotEmpty() }
            }.thenBy { it.article.articleIndex }
        )
    }
    if (pages.isEmpty()) return
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    val pagerState = rememberPagerState(pageCount = { pages.size })
    val sheetScope = rememberCoroutineScope()
    var handleDragDistance by remember { mutableFloatStateOf(0f) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        // Once fully expanded, upward drags belong exclusively to the article body.
        // Competing sheet and body scroll consumption made the sheet oscillate near its top.
        sheetGesturesEnabled = sheetState.currentValue != SheetValue.Expanded,
        dragHandle = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .pointerInput(sheetState.currentValue) {
                        val threshold = 24.dp.toPx()
                        detectVerticalDragGestures(
                            onDragStart = { handleDragDistance = 0f },
                            onVerticalDrag = { change, dragAmount ->
                                change.consume()
                                handleDragDistance += dragAmount
                            },
                            onDragEnd = {
                                when {
                                    handleDragDistance > threshold &&
                                        sheetState.currentValue == SheetValue.Expanded -> {
                                        sheetScope.launch { sheetState.partialExpand() }
                                    }
                                    handleDragDistance < -threshold &&
                                        sheetState.currentValue == SheetValue.PartiallyExpanded -> {
                                        sheetScope.launch { sheetState.expand() }
                                    }
                                }
                                handleDragDistance = 0f
                            },
                            onDragCancel = { handleDragDistance = 0f }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                BottomSheetDefaults.DragHandle()
            }
        },
        containerColor = Fl.Card,
        contentColor = Fl.Ink
    ) {
        Column(modifier = Modifier.fillMaxWidth().fillMaxHeight(0.93f)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("다른 기사", fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Caption("${pagerState.currentPage + 1} / ${pages.size}")
            }
            val instruction = sheetInstruction(pages.size, sheetState.currentValue == SheetValue.Expanded)
            if (instruction.isNotEmpty()) {
                Caption(instruction, modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp))
            }
            Spacer(Modifier.height(8.dp))
            HorizontalDivider(color = Fl.Line)
            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { pageIndex ->
                RelatedArticlePage(pages[pageIndex], sheetState)
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun RelatedArticlePage(page: RelatedPage, sheetState: SheetState) {
    val uriHandler = LocalUriHandler.current
    val density = LocalDensity.current
    val windowView = LocalView.current.rootView
    val article = page.article
    val scroll = rememberScrollState()
    val firstOffset = page.pairs.preferredFocusPair()?.relatedSpan?.start
    var layout by remember(article.id, page.pairs) { mutableStateOf<TextLayoutResult?>(null) }
    var viewportHeight by remember(article.id) { mutableIntStateOf(0) }
    var visibleHeight by remember(article.id) { mutableIntStateOf(0) }
    var initialFocusApplied by remember(article.id, firstOffset) { mutableStateOf(false) }
    val highlightText = remember(article, page.pairs) {
        relatedReaderText(article, page.pairs)
    }

    LaunchedEffect(article.id, firstOffset, layout, visibleHeight, viewportHeight,
        scroll.maxValue, sheetState.currentValue) {
        if (initialFocusApplied || sheetState.currentValue == SheetValue.Hidden) return@LaunchedEffect
        val offset = firstOffset ?: return@LaunchedEffect
        val textLayout = layout ?: return@LaunchedEffect
        if (visibleHeight <= 0 || offset !in article.body.indices) return@LaunchedEffect
        // Text layout can arrive before the scroll container has its final range.
        if (scroll.maxValue == 0 && textLayout.size.height > viewportHeight) return@LaunchedEffect
        awaitFrame()
        val box = textLayout.getBoundingBox(offset)
        val textTopPadding = with(density) { 14.dp.toPx() }
        val target = centeredReaderOffset(
            sentenceCenter = textTopPadding + box.top + box.height / 2f,
            visibleHeight = visibleHeight,
            maxScroll = scroll.maxValue
        )
        scroll.scrollTo(target)
        initialFocusApplied = true
    }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 14.dp)) {
        Text(article.title, fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.Bold, color = Fl.Ink)
        article.url?.let { url -> SourceLink(onClick = { uriHandler.openUri(url) }, label = "원문 링크") }
        val relation = page.pairs.map { it.relation }.distinct().joinToString(" · ") { relationLabel(it) }
        if (relation.isNotBlank()) Caption(relation, modifier = Modifier.padding(vertical = 5.dp))
        val differences = page.pairs.flatMap { it.expressionDifferences }.forComparisonSheet()
        if (differences.isNotEmpty()) {
            Column(
                modifier = Modifier.fillMaxWidth().background(Fl.Inset, RoundedCornerShape(10.dp))
                    .padding(horizontal = 12.dp, vertical = 9.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Caption("다르게 쓴 표현", color = Fl.InkDiff)
                differences.forEach { difference ->
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = readerDifferenceHeading(difference.code),
                            color = Fl.InkDiff,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp,
                            lineHeight = 17.sp
                        )
                        if (difference.note.isNotBlank()) {
                            Text(
                                text = difference.note.trim(),
                                color = Fl.Ink2,
                                fontSize = 12.sp,
                                lineHeight = 18.sp
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        HorizontalDivider(color = Fl.Line)
        Box(
            modifier = Modifier.fillMaxSize()
                .onSizeChanged { viewportHeight = it.height }
                .onGloballyPositioned { coordinates ->
                    visibleHeight = (windowView.height - coordinates.positionInWindow().y)
                        .roundToInt().coerceIn(0, coordinates.size.height)
                }
        ) {
            Column(modifier = Modifier.fillMaxSize().verticalScroll(
                scroll, enabled = sheetState.currentValue == SheetValue.Expanded)) {
                Text(
                    text = highlightText,
                    modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
                    color = Fl.Ink,
                    fontFamily = Fl.Serif,
                    fontSize = 15.sp,
                    lineHeight = 27.sp,
                    onTextLayout = { layout = it }
                )
                Spacer(Modifier.height(with(density) {
                    // Keep content height stable while the expanded sheet body scrolls.
                    if (sheetState.currentValue == SheetValue.Expanded) 0.dp
                    else readerTailPadding(viewportHeight, visibleHeight).toDp()
                }))
            }
        }
    }
}

/** Shared with the legacy rubric cards so the Reader names match the backend categories. */
internal fun readerDifferenceHeading(code: String): String =
    RubricLabels.expression[code]?.let { "$code · $it" } ?: code

/** An A–G tap focuses its marked counterpart even when neutral pairs are present. */
internal fun List<ReaderPair>.preferredFocusPair(): ReaderPair? =
    filter { it.expressionDifferences.isNotEmpty() }.minByOrNull { it.relatedSpan.start }

internal fun relatedReaderText(
    article: ReaderArticle,
    pairs: List<ReaderPair>
): AnnotatedString = buildAnnotatedString {
    append(article.body)
    pairs.filter { it.expressionDifferences.isNotEmpty() }.forEach { pair ->
        addStyle(SpanStyle(background = ReaderExpression, textDecoration = TextDecoration.Underline),
            pair.relatedSpan.start, pair.relatedSpan.end)
        pair.expressionDifferences.forEach { difference ->
            addStyle(SpanStyle(background = ReaderMarker, fontWeight = FontWeight.Bold),
                difference.relatedMarkerSpan.start, difference.relatedMarkerSpan.end)
        }
    }
}

private fun relationLabel(raw: String): String = when (raw) {
    "same" -> "같은 내용"
    "opposes" -> "반대되는 주장"
    "refutes" -> "반박한 내용"
    "reframes" -> "다르게 구성한 내용"
    "different_interpretation" -> "다른 해석"
    "same_issue" -> "같은 쟁점"
    "uncertain" -> "연결을 확인 중"
    else -> ""
}
