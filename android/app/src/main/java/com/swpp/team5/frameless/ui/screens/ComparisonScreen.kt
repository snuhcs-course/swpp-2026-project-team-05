package com.swpp.team5.frameless.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import com.swpp.team5.frameless.data.RubricResult
import com.swpp.team5.frameless.ui.components.ArticleSentenceCard
import com.swpp.team5.frameless.ui.components.EmptyState
import com.swpp.team5.frameless.ui.components.IssueChipRow
import com.swpp.team5.frameless.ui.components.OutletList
import com.swpp.team5.frameless.ui.components.QuoteText
import com.swpp.team5.frameless.ui.components.SourceEvidenceCard
import com.swpp.team5.frameless.ui.state.ComparisonModel
import com.swpp.team5.frameless.ui.state.ComparisonSelection
import com.swpp.team5.frameless.ui.state.InputSentence
import com.swpp.team5.frameless.ui.state.IssueView
import com.swpp.team5.frameless.ui.state.NarrowTab
import com.swpp.team5.frameless.ui.state.OTHERS_ISSUE
import com.swpp.team5.frameless.ui.state.cleanQuote
import com.swpp.team5.frameless.ui.state.two
import com.swpp.team5.frameless.ui.theme.Caption
import com.swpp.team5.frameless.ui.theme.Fl
import com.swpp.team5.frameless.ui.theme.FlCard
import com.swpp.team5.frameless.ui.theme.HRule
import com.swpp.team5.frameless.ui.theme.OutlinedPill
import com.swpp.team5.frameless.ui.theme.SectionTitle
import com.swpp.team5.frameless.ui.theme.SegmentedPills
import com.swpp.team5.frameless.ui.theme.SourceLink
import com.swpp.team5.frameless.ui.theme.TagPill

/** 이 폭 이상이면 split view (폴더블 펼침, 태블릿, 가로 모드) */
private val SplitMinWidth = 600.dp
/** 이 높이보다 낮으면(폰 가로) 머리글과 안내 문구를 줄인다 */
private val CompactMaxHeight = 500.dp

/**
 * 기사·쟁점 비교 화면.
 *  - 넓은 화면: 왼쪽 입력 기사 | 오른쪽 선택한 쟁점과 원문 근거
 *  - 좁은 화면: '기사 보기 / 쟁점 비교' 탭 + 쟁점 상세 화면 전환
 */
@Composable
internal fun ComparisonScreen(
    model: ComparisonModel,
    selection: ComparisonSelection,
    onNewArticle: () -> Unit,
    onSelectIssue: (Int) -> Unit,
    onFocusSentence: (InputSentence) -> Unit,
    onNarrowTab: (NarrowTab) -> Unit,
    onOpenNarrowDetail: (Int) -> Unit,
    onCloseNarrowDetail: () -> Unit
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val wide = maxWidth >= SplitMinWidth
        val compact = wide && maxHeight < CompactMaxHeight

        BackHandler {
            if (!wide && selection.narrowDetailOpen) onCloseNarrowDetail() else onNewArticle()
        }

        Column(modifier = Modifier.fillMaxSize()) {
            ComparisonHeader(model = model, compact = compact, onNewArticle = onNewArticle)

            when {
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
                                IssueChipRow(issues = model.issues, selected = selection.issue, onSelect = onSelectIssue)
                                HRule()
                                DetailPane(model = model, selected = selection.issue, compact = compact)
                            }
                        }
                    }
                }

                selection.narrowDetailOpen -> NarrowDetail(
                    model = model,
                    selected = selection.issue,
                    onBack = onCloseNarrowDetail,
                    onSelect = onOpenNarrowDetail
                )

                else -> {
                    SegmentedPills(
                        options = listOf("기사 보기", "쟁점 비교 ${two(model.issues.size)}"),
                        selected = if (selection.narrowTab == NarrowTab.ARTICLE) 0 else 1,
                        onSelect = { onNarrowTab(if (it == 0) NarrowTab.ARTICLE else NarrowTab.ISSUES) },
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
                                onSentence = { s -> onOpenNarrowDetail(s.issueIndexes.firstOrNull() ?: OTHERS_ISSUE) }
                            )
                        }
                        NarrowTab.ISSUES -> IssueList(model = model, onOpen = onOpenNarrowDetail)
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
            Caption("FrameLESS · 같은 소식 ${model.analysis.articles.size}개 매체 · 쟁점 ${model.issues.size}")
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
    if (!compact && analysis.relatedWithoutMatches.isNotEmpty()) {
        Caption(
            text = "대응 문장을 하나도 찾지 못한 기사: " + analysis.relatedWithoutMatches.joinToString(", "),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
        )
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
        LegendSwatch(Fl.TintNone, "대응 문장 없음")
        LegendSwatch(Fl.TintSame, "같은 내용")
        LegendSwatch(Fl.TintDiff, "다른 해석/표현")
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
            ArticleSentenceCard(
                number = n + 1,
                sentence = s,
                matches = model.matchesFor(s),
                selected = showSelection && selection.focusedQuote == s.quote,
                inSelectedIssue = showSelection && selection.issue in s.issueIndexes,
                expressions = model.inputExpressions,
                onClick = { onSentence(s) }
            )
        }
    }
}

/* ---------------- 좁은 화면: 쟁점 목록과 상세 ---------------- */

@Composable
private fun IssueList(model: ComparisonModel, onOpen: (Int) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 14.dp, end = 14.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        model.issues.forEach { issue ->
            FlCard(modifier = Modifier.fillMaxWidth().clickable { onOpen(issue.index) }) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.Top) {
                        Caption("쟁점 ${two(issue.index + 1)}", modifier = Modifier.padding(top = 2.dp, end = 10.dp))
                        Text(
                            text = issue.label,
                            fontSize = 14.sp,
                            lineHeight = 20.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Fl.Ink,
                            modifier = Modifier.weight(1f)
                        )
                        Text("›", fontSize = 20.sp, color = Fl.Muted, modifier = Modifier.padding(start = 8.dp))
                    }
                    IssueTally(issue)
                }
            }
        }
        FlCard(modifier = Modifier.fillMaxWidth().clickable { onOpen(OTHERS_ISSUE) }) {
            Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("쟁점에 묶이지 않은 문장 · 판정 기준", fontSize = 14.sp, color = Fl.Ink, modifier = Modifier.weight(1f))
                Text("›", fontSize = 20.sp, color = Fl.Muted)
            }
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
                val next = (selected + 1).takeIf { it < model.issues.size }
                NavArrow("‹", enabled = prev != null) { prev?.let(onSelect) }
                Caption("${two(selected + 1)} / ${two(model.issues.size)}")
                NavArrow("›", enabled = next != null) { next?.let(onSelect) }
            }
        }
        FlCard(modifier = Modifier.fillMaxSize()) {
            DetailPane(model = model, selected = selected, compact = false)
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
 * 쟁점 상세 — 넓은 화면 오른쪽과 좁은 화면 상세에서 같이 쓴다
 * ================================================================== */

@Composable
private fun DetailPane(model: ComparisonModel, selected: Int, compact: Boolean) {
    val issue = model.issues.getOrNull(selected)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = if (compact) 8.dp else 16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        if (issue == null) OthersDetail(model) else IssueDetail(model, issue)
        Spacer(modifier = Modifier.height(4.dp))
    }
}

@Composable
private fun IssueDetail(model: ComparisonModel, issue: IssueView) {
    val uriHandler = LocalUriHandler.current

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Caption("쟁점 ${two(issue.index + 1)} / ${two(model.issues.size)}")
        Text(issue.label, fontFamily = Fl.Serif, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, lineHeight = 26.sp, color = Fl.Ink)
        IssueTally(issue)
    }

    // 입력 기사 문장
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionTitleWeighted("입력 기사")
            model.input.url?.let { SourceLink(onClick = { uriHandler.openUri(it) }) }
        }
        Caption(model.input.title)
        if (issue.inputQuotes.isEmpty()) EmptyState("이 쟁점에 연결된 입력 기사 문장이 없어요.")
        issue.inputQuotes.forEach { q ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Fl.Inset, RoundedCornerShape(10.dp))
                    .padding(12.dp)
            ) { QuoteText(q, tint = null) }
        }
    }

    // 같은 내용을 보도한 출처
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionTitle("같은 내용을 보도한 출처", count = issue.sameOutlets)
        if (issue.same.isEmpty()) {
            EmptyState("같은 내용으로 대응하는 문장을 찾지 못했어요.")
        } else {
            OutletList(issue.same.map { it.article }.distinctBy { it.articleIndex }, Fl.InkSame)
            issue.same.forEachIndexed { n, clip -> SourceEvidenceCard(n + 1, clip) }
        }
    }

    // 다르게 언급된 부분
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionTitle("다르게 언급된 부분", count = issue.diffOutlets)
        if (issue.diff.isEmpty()) {
            EmptyState("뚜렷한 차이를 찾지 못했어요.")
        } else {
            issue.diff.forEachIndexed { n, clip -> SourceEvidenceCard(n + 1, clip) }
        }
    }

    // 서버가 같음/다름 외의 관계 값을 보낸 문장
    if (issue.other.isNotEmpty()) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionTitle("관계가 표시되지 않은 대응 문장", count = issue.other.size)
            issue.other.forEachIndexed { n, clip -> SourceEvidenceCard(n + 1, clip) }
        }
    }

    // 이 쟁점에서 대응 문장을 찾지 못한 출처
    if (issue.notFound.isNotEmpty()) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            SectionTitle("대응 문장을 찾지 못한 출처", count = issue.notFound.size)
            issue.notFound.forEach { a ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(a.title, fontSize = 12.sp, color = Fl.Ink2, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    a.url?.let { SourceLink(onClick = { uriHandler.openUri(it) }) }
                }
            }
            Caption("찾지 못했다는 뜻이에요. 그 기사가 이 내용을 뺐다는 판정은 아니에요.")
        }
    }
}

@Composable
private fun OthersDetail(model: ComparisonModel) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Caption("그 밖의 문장")
        Text("쟁점에 묶이지 않은 문장", fontFamily = Fl.Serif, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = Fl.Ink)
    }

    val inputOnly = model.inputSentences.filter { model.matchesFor(it).isEmpty() }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionTitle("다른 기사에서 대응 문장을 찾지 못한 입력 기사 문장", count = inputOnly.size)
        if (inputOnly.isEmpty()) EmptyState("모든 입력 기사 문장에서 대응 문장을 찾았어요.")
        inputOnly.forEach { s ->
            Box(modifier = Modifier.fillMaxWidth().background(Fl.Inset, RoundedCornerShape(10.dp)).padding(12.dp)) {
                QuoteText(s.quote, tint = Fl.TintNone)
            }
        }
        if (inputOnly.isNotEmpty()) Caption("비교한 기사들에서 짝을 찾지 못했다는 뜻이에요. 일부러 뺐다는 판정은 아니에요.")
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionTitle("입력 기사와 연결되지 않은 다른 기사 문장", count = model.unlinkedClips.size)
        if (model.unlinkedClips.isEmpty()) EmptyState("없어요.")
        model.unlinkedClips.forEachIndexed { n, clip -> SourceEvidenceCard(n + 1, clip) }
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionTitle("판정 기준 v0.2.1")
        val rubric = model.analysis.rubric
        if (rubric == null) {
            EmptyState("표현 차이(A~G·유표성), 정보 구성 차이(I1~I5), 질문별 비교는 서버 응답에 아직 없어 표시하지 않아요.")
        } else {
            RubricBlock(rubric, model)
        }
    }
}

/* ---------------- 작은 조각 ---------------- */

@Composable
private fun IssueTally(issue: IssueView) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        if (issue.sameOutlets > 0) TagPill("같은 내용 · ${issue.sameOutlets}곳", Fl.TintSame, Fl.InkSame)
        if (issue.diffOutlets > 0) TagPill("다른 해석/표현 · ${issue.diffOutlets}곳", Fl.TintDiff, Fl.InkDiff)
        if (issue.sameOutlets == 0 && issue.diffOutlets == 0) Caption("대응 문장을 찾지 못했어요")
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.SectionTitleWeighted(text: String) {
    Text(text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Fl.Ink, modifier = Modifier.weight(1f))
}

/** 서버가 rubric을 보낼 때만 그 값을 그대로 보여준다 */
@Composable
private fun RubricBlock(rubric: RubricResult, model: ComparisonModel) {
    val analysis = model.analysis
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Caption("범주와 값만 보여주고 어느 쪽이 옳은지는 판정하지 않아요. 설명은 모델이 판정 뒤에 정리한 것이에요.")
        if (rubric.droppedCount > 0) Caption("형식이 맞지 않아 표시하지 않은 항목 ${rubric.droppedCount}개", color = Fl.Danger)
        rubric.questions.forEach { q ->
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Caption(q.id)
                Text(q.question, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Fl.Ink)
                q.answers.forEach { a ->
                    val title = analysis.articleTitle(a.articleIndex)
                    Text(
                        text = if (a.answered) "$title — ${a.summary}" else "$title — 이 기사에는 없음" + (a.absenceReason?.let { " · $it" } ?: ""),
                        fontSize = 12.sp,
                        color = if (a.answered) Fl.Ink2 else Fl.Muted
                    )
                    a.evidenceQuotes.forEach { QuoteText(cleanQuote(it), tint = null) }
                }
            }
        }
        rubric.expressions.forEach { e ->
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Caption("표현 차이 · " + e.categories.joinToString(" ") { "${it.code}${it.markedness}" } + if (e.held) " · 보류" else "")
                QuoteText(e.sentence, tint = null)
                Text(
                    text = "\"${e.span}\" · 기준 표현: ${e.baselineText.ifBlank { "(없음)" }} (" +
                        (if (e.baselineSource == "outlet") "비교 기사" else "중립 대체 표현, 모델 생성") +
                        ") · 대상: ${e.target ?: "없음"} · ${e.polarity}",
                    fontSize = 12.sp,
                    color = Fl.Ink2
                )
                if (e.note.isNotBlank()) Caption(e.note)
            }
        }
        rubric.informations.forEach { i ->
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Caption("정보 구성 차이 · ${i.code}")
                Text(analysis.articleTitle(i.articleIndex), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Fl.Ink2)
                i.sentence?.let { QuoteText(cleanQuote(it), tint = null) }
                Text(i.value, fontSize = 12.sp, color = Fl.Ink2)
            }
        }
    }
}
