package com.swpp.team5.frameless.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swpp.team5.frameless.ui.components.AnswerCard
import com.swpp.team5.frameless.ui.components.ArticleSentenceCard
import com.swpp.team5.frameless.ui.components.EmptyState
import com.swpp.team5.frameless.ui.components.ExpressionEvidenceCard
import com.swpp.team5.frameless.ui.components.InformationEvidenceCard
import com.swpp.team5.frameless.ui.components.IssueChipRow
import com.swpp.team5.frameless.ui.components.OutletList
import com.swpp.team5.frameless.ui.components.QuoteText
import com.swpp.team5.frameless.ui.components.QuotedExpressionCard
import com.swpp.team5.frameless.ui.components.SourceEvidenceCard
import com.swpp.team5.frameless.ui.state.Clip
import com.swpp.team5.frameless.ui.state.ComparisonModel
import com.swpp.team5.frameless.ui.state.ComparisonSelection
import com.swpp.team5.frameless.ui.state.InputSentence
import com.swpp.team5.frameless.ui.state.IssueView
import com.swpp.team5.frameless.ui.state.NO_MATCH_TEXT
import com.swpp.team5.frameless.ui.state.NarrowTab
import com.swpp.team5.frameless.ui.state.OTHERS_GROUP
import com.swpp.team5.frameless.ui.state.QuestionView
import com.swpp.team5.frameless.ui.state.RubricItems
import com.swpp.team5.frameless.ui.state.sameText
import com.swpp.team5.frameless.ui.state.two
import com.swpp.team5.frameless.ui.theme.Caption
import com.swpp.team5.frameless.ui.theme.FilledPill
import com.swpp.team5.frameless.ui.theme.Fl
import com.swpp.team5.frameless.ui.theme.FlCard
import com.swpp.team5.frameless.ui.theme.HRule
import com.swpp.team5.frameless.ui.theme.OutlinedPill
import com.swpp.team5.frameless.ui.theme.SectionTitle
import com.swpp.team5.frameless.ui.theme.SegmentedPills
import com.swpp.team5.frameless.ui.theme.SourceLink
import com.swpp.team5.frameless.ui.theme.TagPill

/** 이 폭 이상이면 split view (폴더블 펼침, 태블릿, 가로 모드). S23 세로(약 360dp)는 탭으로 간다. */
private val SplitMinWidth = 600.dp
/** 이 높이보다 낮으면(폰 가로) 머리글과 안내 문구를 줄인다 */
private val CompactMaxHeight = 500.dp

/**
 * 기사 비교 화면.
 *  - 넓은 화면: 왼쪽 입력 기사 | 오른쪽 선택한 쟁점(또는 주장)과 원문 근거
 *  - 좁은 화면: '입력 기사 / 쟁점 비교' 탭 + 상세 화면 전환
 *  - 서버가 질문 단위 데이터(rubric.questions)를 주면 '쟁점', 주장만 주면 '주장'으로 부른다.
 */
@Composable
internal fun ComparisonScreen(
    model: ComparisonModel,
    selection: ComparisonSelection,
    onNewArticle: () -> Unit,
    onSelectGroup: (Int) -> Unit,
    onFocusSentence: (InputSentence) -> Unit,
    onOpenSentenceDetail: (InputSentence) -> Unit,
    onNarrowTab: (NarrowTab) -> Unit,
    onOpenNarrowDetail: (Int) -> Unit,
    onCloseNarrowDetail: () -> Unit,
    onShowDespiteNoMatch: () -> Unit
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val wide = maxWidth >= SplitMinWidth
        val compact = wide && maxHeight < CompactMaxHeight

        BackHandler {
            if (!wide && selection.narrowDetailOpen) onCloseNarrowDetail() else onNewArticle()
        }

        Column(modifier = Modifier.fillMaxSize()) {
            ComparisonHeader(model = model, compact = compact, onNewArticle = onNewArticle)
            AnalysisStatusNotice(model)

            when {
                model.noMatches && !selection.showDespiteNoMatch -> NoMatchState(
                    model = model,
                    onRetry = onNewArticle,
                    onShowAnyway = onShowDespiteNoMatch
                )

                wide -> {
                    if (!compact) Legend()
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(start = 14.dp, end = 14.dp, bottom = if (compact) 6.dp else 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        FlCard(modifier = Modifier.weight(1f).fillMaxHeight()) {
                            InputArticlePane(
                                model = model,
                                selection = selection,
                                compact = compact,
                                showSelection = true,
                                onSentence = onFocusSentence
                            )
                        }
                        FlCard(modifier = Modifier.weight(1.1f).fillMaxHeight()) {
                            Column(modifier = Modifier.fillMaxSize()) {
                                IssueChipRow(
                                    noun = model.noun,
                                    count = model.groupCount,
                                    selected = selection.group,
                                    onSelect = onSelectGroup
                                )
                                HRule()
                                DetailPane(model = model, selected = selection.group, compact = compact, showInput = false)
                            }
                        }
                    }
                }

                selection.narrowDetailOpen -> NarrowDetail(
                    model = model,
                    selected = selection.group,
                    onBack = onCloseNarrowDetail,
                    onSelect = onOpenNarrowDetail
                )

                else -> {
                    SegmentedPills(
                        options = listOf("입력 기사", "${model.noun} 비교 ${two(model.groupCount)}"),
                        selected = if (selection.narrowTab == NarrowTab.ARTICLE) 0 else 1,
                        onSelect = { onNarrowTab(if (it == 0) NarrowTab.ARTICLE else NarrowTab.GROUPS) },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp)
                    )
                    when (selection.narrowTab) {
                        NarrowTab.ARTICLE -> FlCard(
                            modifier = Modifier.fillMaxSize().padding(start = 14.dp, end = 14.dp, bottom = 12.dp)
                        ) {
                            InputArticlePane(
                                model = model,
                                selection = selection,
                                compact = false,
                                showSelection = false,
                                onSentence = onOpenSentenceDetail
                            )
                        }
                        NarrowTab.GROUPS -> GroupList(model = model, onOpen = onOpenNarrowDetail)
                    }
                }
            }
        }
    }
}

/* ---------------- 머리글 ---------------- */

@Composable
private fun ComparisonHeader(model: ComparisonModel, compact: Boolean, onNewArticle: () -> Unit) {
    val analysis = model.analysis
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Fl.Card)
            .padding(start = 6.dp, end = 14.dp, top = if (compact) 4.dp else 10.dp, bottom = if (compact) 4.dp else 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "‹",
            fontSize = 26.sp,
            color = Fl.Ink,
            modifier = Modifier.clickable(onClick = onNewArticle).padding(horizontal = 12.dp, vertical = 2.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            // 비교 기사 수는 서버 related_articles 기준
            Caption("FrameLESS · 비교 기사 ${analysis.relatedArticleCount}건 · ${model.noun} ${model.groupCount}")
            Text(
                text = analysis.coreEvent.ifBlank { analysis.sourceTitle },
                fontSize = if (compact) 13.sp else 15.sp,
                lineHeight = if (compact) 18.sp else 21.sp,
                fontWeight = FontWeight.SemiBold,
                color = Fl.Ink,
                maxLines = if (compact) 1 else 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        OutlinedPill(text = "새 기사", onClick = onNewArticle)
    }
    HRule()
    if (!compact && !model.noMatches && analysis.relatedWithoutMatches.isNotEmpty()) {
        Caption(
            text = "이번 주장 비교에 표시되지 않은 관련 기사: " + analysis.relatedWithoutMatches.joinToString(", "),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
        )
    }
}

/** AI-generated status guard: 일부 단계 실패를 '비교 결과 없음'으로 안내하지 않는다. */
@Composable
private fun AnalysisStatusNotice(model: ComparisonModel) {
    val status = model.analysis.analysisStatus ?: return
    if (status.executionStatus !in setOf("partial", "failed") && status.comparisonStatus != "unavailable") return
    Column(
        modifier = Modifier.fillMaxWidth().background(Fl.Inset).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Caption("일부 분석이나 관계 판정을 확인하지 못했어요.", color = Fl.Danger)
        Caption("아래에는 확인된 범위의 결과만 표시해요. 표시되지 않은 내용은 차이가 없다는 뜻이 아니에요.")
    }
}

/** 넓은 화면 범례 */
@Composable
private fun Legend() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        LegendSwatch(Fl.TintNone, "표시된 대응 문장 없음")
        LegendSwatch(Fl.TintSame, "같은 내용")
        LegendSwatch(Fl.TintDiff, "다르게 다룬 내용")
        Spacer(modifier = Modifier.weight(1f))
        Caption("문장을 누르면 대응하는 출처가 나와요")
    }
}

@Composable
private fun LegendSwatch(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.width(18.dp).height(10.dp).background(color, RoundedCornerShape(3.dp)))
        Spacer(modifier = Modifier.width(6.dp))
        Caption(label)
    }
}

/* ---------------- 대응 문장이 하나도 없을 때 ---------------- */

@Composable
private fun NoMatchState(model: ComparisonModel, onRetry: () -> Unit, onShowAnyway: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    val analysis = model.analysis
    Box(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        FlCard(modifier = Modifier.fillMaxWidth().widthIn(max = 560.dp)) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Caption("비교 결과 없음")
                Text(
                    text = if (analysis.analysisStatus?.comparisonStatus == "not_requested") {
                        "이번 기사에서 비교할 핵심 주장을\n추출하지 못했어요"
                    } else if (analysis.relatedArticleCount == 0) {
                        "같은 사건을 보도한 다른 기사를\n찾지 못했어요"
                    } else {
                        "비교 기사 ${analysis.relatedArticleCount}건에서\n표시할 대응 문장이 없어요"
                    },
                    fontFamily = Fl.Serif,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 20.sp,
                    lineHeight = 29.sp,
                    color = Fl.Ink
                )
                Text(
                    text = if (analysis.relatedArticleCount == 0) "현재 수집된 비교 기사가 없어요." else NO_MATCH_TEXT,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    color = Fl.Ink2
                )
                Caption("수집 범위나 문장 짝짓기 결과에 따른 것이에요. 입력 기사나 비교 기사에 대한 평가는 아니에요.")

                HRule()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = model.input.title,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Fl.Ink,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    model.input.url?.let { SourceLink(onClick = { uriHandler.openUri(it) }) }
                }
                if (analysis.relatedWithoutMatches.isNotEmpty()) {
                    Caption("찾은 비교 기사")
                    analysis.relatedWithoutMatches.forEach { title ->
                        Text("· $title", fontSize = 12.sp, color = Fl.Ink2, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                    FilledPill(text = "다른 기사로 다시 시도", onClick = onRetry)
                    if (model.inputSentences.isNotEmpty()) {
                        OutlinedPill(text = "입력 기사 문장 보기", onClick = onShowAnyway)
                    }
                }
            }
        }
    }
}

/* ---------------- 입력 기사 ---------------- */

@Composable
private fun InputArticlePane(
    model: ComparisonModel,
    selection: ComparisonSelection,
    compact: Boolean,
    showSelection: Boolean,
    onSentence: (InputSentence) -> Unit
) {
    val uriHandler = LocalUriHandler.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = if (compact) 8.dp else 14.dp),
        verticalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 8.dp)
    ) {
        Row(modifier = Modifier.padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(7.dp).background(Fl.Accent, RoundedCornerShape(50)))
            Spacer(modifier = Modifier.width(6.dp))
            Text("입력 기사", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Fl.Ink)
            Spacer(modifier = Modifier.weight(1f))
            model.input.url?.let { url -> SourceLink(onClick = { uriHandler.openUri(url) }) }
        }
        Text(
            text = model.input.title,
            fontFamily = Fl.Serif,
            fontWeight = FontWeight.ExtraBold,
            fontSize = if (compact) 15.sp else 19.sp,
            lineHeight = if (compact) 21.sp else 28.sp,
            color = Fl.Ink,
            maxLines = if (compact) 1 else 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 6.dp)
        )
        if (!compact) {
            Caption(
                "다른 보도와 비교한 문장만 모았어요. 본문 전체는 원문에서 볼 수 있어요.",
                modifier = Modifier.padding(horizontal = 6.dp)
            )
        }
        Box(modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)) { HRule() }

        if (model.inputSentences.isEmpty()) {
            Caption("비교에 쓰인 문장이 없어요.", modifier = Modifier.padding(6.dp))
        }
        model.inputSentences.forEachIndexed { n, s ->
            val groups = model.groupsFor(s)
            ArticleSentenceCard(
                number = n + 1,
                sentence = s,
                matches = model.matchesFor(s),
                groups = groups,
                noun = model.noun,
                selected = showSelection && selection.focusedQuote == s.quote,
                inSelectedGroup = showSelection && selection.group in groups,
                expressions = model.inputExpressions.filter { sameText(it.sentence, s.quote) },
                onClick = { onSentence(s) }
            )
        }
    }
}

/* ---------------- 좁은 화면: 목록과 상세 ---------------- */

@Composable
private fun GroupList(model: ComparisonModel, onOpen: (Int) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 14.dp, end = 14.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (model.questionMode) {
            model.questions.forEach { q ->
                GroupRow(caption = "쟁점 ${two(q.index + 1)}", title = q.question, onClick = { onOpen(q.index) }) {
                    QuestionTally(q)
                }
            }
        } else {
            Caption(
                "현재 분석은 입력 기사 주장별로 묶었어요.",
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
            )
            model.issues.forEach { issue ->
                GroupRow(caption = "주장 ${two(issue.index + 1)}", title = issue.label, onClick = { onOpen(issue.index) }) {
                    Tally(issue.sameOutlets, issue.diffOutlets, issue.otherOutlets)
                }
            }
        }
        FlCard(modifier = Modifier.fillMaxWidth().clickable { onOpen(OTHERS_GROUP) }) {
            Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${model.noun}에 묶이지 않은 문장", fontSize = 14.sp, color = Fl.Ink, modifier = Modifier.weight(1f))
                Text("›", fontSize = 20.sp, color = Fl.Muted)
            }
        }
    }
}

@Composable
private fun GroupRow(caption: String, title: String, onClick: () -> Unit, tally: @Composable () -> Unit) {
    FlCard(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Caption(caption, modifier = Modifier.padding(top = 2.dp, end = 10.dp))
                Text(
                    text = title,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Fl.Ink,
                    modifier = Modifier.weight(1f)
                )
                Text("›", fontSize = 20.sp, color = Fl.Muted, modifier = Modifier.padding(start = 8.dp))
            }
            tally()
        }
    }
}

@Composable
private fun NarrowDetail(model: ComparisonModel, selected: Int, onBack: () -> Unit, onSelect: (Int) -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(start = 14.dp, end = 14.dp, bottom = 12.dp)) {
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedPill(text = "‹ 목록", onClick = onBack)
            Spacer(modifier = Modifier.weight(1f))
            if (selected >= 0) {
                val prev = (selected - 1).takeIf { it >= 0 }
                val next = (selected + 1).takeIf { it < model.groupCount }
                NavArrow("‹", enabled = prev != null) { prev?.let(onSelect) }
                Caption("${two(selected + 1)} / ${two(model.groupCount)}")
                NavArrow("›", enabled = next != null) { next?.let(onSelect) }
            }
        }
        FlCard(modifier = Modifier.fillMaxSize()) {
            DetailPane(model = model, selected = selected, compact = false, showInput = true)
        }
    }
}

@Composable
private fun NavArrow(symbol: String, enabled: Boolean, onClick: () -> Unit) {
    Text(
        text = symbol,
        fontSize = 22.sp,
        color = if (enabled) Fl.Ink else Fl.Line,
        modifier = Modifier.clickable(enabled = enabled, onClick = onClick).padding(horizontal = 14.dp, vertical = 4.dp)
    )
}

/* ==================================================================
 * 상세 — 넓은 화면 오른쪽과 좁은 화면 상세에서 같이 쓴다
 * ================================================================== */

@Composable
/** showInput: 좁은 화면 상세에서만 입력 기사 문장을 다시 보여준다. 넓은 화면은 왼쪽에 이미 있다. */
private fun DetailPane(model: ComparisonModel, selected: Int, compact: Boolean, showInput: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = if (compact) 8.dp else 16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        val question = if (model.questionMode) model.questions.getOrNull(selected) else null
        val issue = if (!model.questionMode) model.issues.getOrNull(selected) else null
        when {
            question != null -> QuestionDetail(model, question)
            issue != null -> IssueDetail(model, issue, showInput)
            else -> OthersDetail(model)
        }
        Spacer(modifier = Modifier.height(4.dp))
    }
}

/** 질문 단위 쟁점 (서버 rubric.questions가 있을 때) */
@Composable
private fun QuestionDetail(model: ComparisonModel, q: QuestionView) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Caption("쟁점 ${two(q.index + 1)} / ${two(model.questions.size)}")
        Text(q.question, fontFamily = Fl.Serif, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, lineHeight = 26.sp, color = Fl.Ink)
        QuestionTally(q)
    }

    // 기사별 답: 입력 기사 먼저
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionTitle("기사별로 이 쟁점을 다룬 문장", count = q.answers.count { it.answer.answered })
        if (q.answers.isEmpty()) EmptyState("이 쟁점에 대한 기사별 답이 응답에 없어요.")
        q.answers.sortedBy { it.articleIndex }.forEach { AnswerCard(it) }
    }

    ClipSections(same = q.same, diff = q.diff, other = emptyList(), sameOutlets = q.sameOutlets, diffOutlets = q.diffOutlets)
    RubricSections(model, q.rubric)
}

/** 주장 단위 묶음 (서버 comparison.issues) */
@Composable
private fun IssueDetail(model: ComparisonModel, issue: IssueView, showInput: Boolean) {
    val uriHandler = LocalUriHandler.current

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Caption("주장 ${two(issue.index + 1)} / ${two(model.issues.size)}")
        Text(issue.label, fontFamily = Fl.Serif, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, lineHeight = 26.sp, color = Fl.Ink)
        Tally(issue.sameOutlets, issue.diffOutlets, issue.otherOutlets)
    }

    // 입력 기사 문장 (좁은 화면 상세에서만)
    if (showInput) Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            WeightedTitle("입력 기사")
            model.input.url?.let { SourceLink(onClick = { uriHandler.openUri(it) }) }
        }
        Caption(model.input.title)
        if (issue.inputQuotes.isEmpty()) EmptyState("이 주장에 연결된 입력 기사 문장이 없어요.")
        issue.inputQuotes.forEach { q ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Fl.Inset, RoundedCornerShape(10.dp))
                    .padding(12.dp)
            ) { QuoteText(q, tint = null) }
        }
    }

    ClipSections(issue.same, issue.diff, issue.other, issue.sameOutlets, issue.diffOutlets)

    // v1 응답에서 이번 묶음에 표시되지 않은 기사. v2 선택 밖의 기사는 누락으로 추정하지 않는다.
    if (issue.notFound.isNotEmpty()) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            SectionTitle("이번 주장 비교에 표시되지 않은 기사", count = issue.notFound.size)
            Caption(NO_MATCH_TEXT)
            issue.notFound.forEach { a ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(a.title, fontSize = 12.sp, color = Fl.Ink2, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    a.url?.let { SourceLink(onClick = { uriHandler.openUri(it) }) }
                }
            }
        }
    }

    RubricSections(model, issue.rubric)
}

/** 같은 내용 / 다르게 언급된 부분 / 관계 미표시 */
@Composable
private fun ClipSections(same: List<Clip>, diff: List<Clip>, other: List<Clip>, sameOutlets: Int, diffOutlets: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionTitle("같은 내용을 보도한 출처", count = sameOutlets)
        if (same.isEmpty()) {
            EmptyState(NO_MATCH_TEXT)
        } else {
            OutletList(same.map { it.article }.distinctBy { it.articleIndex }, Fl.InkSame)
            same.forEachIndexed { n, clip -> SourceEvidenceCard(n + 1, clip) }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionTitle("다르게 다룬 내용", count = diffOutlets)
        if (diff.isEmpty()) {
            EmptyState("뚜렷한 차이를 찾지 못했어요.")
        } else {
            diff.forEachIndexed { n, clip -> SourceEvidenceCard(n + 1, clip) }
        }
    }

    if (other.isNotEmpty()) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionTitle("그 밖의 대응 관계", count = other.size)
            other.forEachIndexed { n, clip -> SourceEvidenceCard(n + 1, clip) }
        }
    }
}

/**
 * 정보 구성 차이 · 표현 차이. 서버가 실제로 보낸 항목이 있을 때만 섹션을 그린다.
 * 직접 인용 안의 표현은 표현 차이가 아니라 정보 구성 차이의 I5 인용 정보로만 보여준다 (D1).
 */
@Composable
private fun RubricSections(model: ComparisonModel, items: RubricItems) {
    val infoCount = items.informations.size + items.quotedExpressions.size
    if (infoCount > 0) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionTitle("정보 구성", count = infoCount)
            items.informations.sortedBy { it.i.code }.forEach { InformationEvidenceCard(it) }
            items.quotedExpressions.forEach { QuotedExpressionCard(it) }
        }
    }
    if (items.expressions.isNotEmpty()) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionTitle("표현 차이 후보", count = items.expressions.size)
            Caption("모델의 잠정 판정과 원문을 함께 보여줘요. 어느 쪽이 옳은지는 판정하지 않아요.")
            items.expressions.forEach { item ->
                ExpressionEvidenceCard(
                    item = item,
                    baselineArticle = item.e.baselineArticleIndex?.let { model.articleOf(it) }
                )
            }
        }
    }
}

@Composable
private fun OthersDetail(model: ComparisonModel) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Caption("그 밖")
        Text("${model.noun}에 묶이지 않은 문장", fontFamily = Fl.Serif, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = Fl.Ink)
    }

    val inputOnly = model.inputSentences.filter { model.matchesFor(it).isEmpty() }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionTitle("이번 비교에 대응이 표시되지 않은 입력 기사 문장", count = inputOnly.size)
        if (inputOnly.isEmpty()) EmptyState("모든 입력 기사 문장에 대응이 표시됐어요.")
        else Caption(NO_MATCH_TEXT)
        inputOnly.forEach { s ->
            Box(modifier = Modifier.fillMaxWidth().background(Fl.Inset, RoundedCornerShape(10.dp)).padding(12.dp)) {
                QuoteText(s.quote, tint = Fl.TintNone)
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionTitle("입력 기사와 연결되지 않은 다른 기사 문장", count = model.unlinkedClips.size)
        if (model.unlinkedClips.isEmpty()) EmptyState("없어요.")
        model.unlinkedClips.forEachIndexed { n, clip -> SourceEvidenceCard(n + 1, clip) }
    }

    val rubric = model.analysis.rubric
    if (rubric == null) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionTitle("판정 기준 v0.2.1")
            EmptyState(if (model.analysis.analysisStatus == null) {
                "서버 응답에 정보 구성(I1~I5)과 표현 분석(A~G)이 없어 표시하지 않아요."
            } else {
                "이번 분석에서 정보 구성(I1~I5)과 표현 분석(A~G) 결과를 표시할 수 없어요."
            })
        }
    } else {
        if (rubric.droppedCount > 0) {
            Caption("형식이 맞지 않아 표시하지 않은 판정 기준 항목 ${rubric.droppedCount}개", color = Fl.Danger)
        }
        RubricSections(model, model.unassignedRubric)
    }
}

/* ---------------- 작은 조각 ---------------- */

@Composable
private fun Tally(sameOutlets: Int, diffOutlets: Int, otherOutlets: Int = 0) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        if (sameOutlets > 0) TagPill("같은 내용 · ${sameOutlets}곳", Fl.TintSame, Fl.InkSame)
        if (diffOutlets > 0) TagPill("다르게 다룬 내용 · ${diffOutlets}곳", Fl.TintDiff, Fl.InkDiff)
        if (otherOutlets > 0) TagPill("그 밖의 대응 · ${otherOutlets}곳", Fl.TintNone, Fl.Ink2)
        if (sameOutlets == 0 && diffOutlets == 0 && otherOutlets == 0) Caption("표시된 대응 문장 없음")
    }
}

@Composable
private fun QuestionTally(q: QuestionView) {
    val answered = q.answers.count { it.answer.answered && it.articleIndex > 0 }
    val absent = q.answers.count { !it.answer.answered && it.articleIndex > 0 }
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        Tally(q.sameOutlets, q.diffOutlets)
        Caption("비교 기사 중 다룬 곳 $answered · 문장 없음 $absent")
    }
}

@Composable
private fun RowScope.WeightedTitle(text: String) {
    Text(text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Fl.Ink, modifier = Modifier.weight(1f))
}
