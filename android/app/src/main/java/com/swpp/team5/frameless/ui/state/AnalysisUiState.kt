package com.swpp.team5.frameless.ui.state

import com.swpp.team5.frameless.data.AnalysisResult
import com.swpp.team5.frameless.data.ArticleRef
import com.swpp.team5.frameless.data.ExpressionDifference
import com.swpp.team5.frameless.data.InformationDifference
import com.swpp.team5.frameless.data.QuestionAnswer
import com.swpp.team5.frameless.data.Relation

/** 화면 전체 상태 */
internal sealed interface AnalysisUiState {
    /** 아직 분석 전 (입력 화면) */
    object Idle : AnalysisUiState

    /** 분석 중. step: 0 사건 파악, 1 보도 찾기, 2 문장 비교 */
    data class Loading(val url: String, val step: Int) : AnalysisUiState

    /** 분석 성공 */
    data class Success(val model: ComparisonModel) : AnalysisUiState

    /** 분석 실패 (입력 화면에 메시지를 띄운다) */
    data class Error(val message: String) : AnalysisUiState
}

/** 좁은 화면 탭 */
internal enum class NarrowTab { ARTICLE, GROUPS }

/** 비교 화면에서 사용자가 고른 것 */
internal data class ComparisonSelection(
    /** 선택한 쟁점(또는 주장) 번호. OTHERS_GROUP이면 '그 밖' */
    val group: Int = 0,
    /** 왼쪽에서 누른 입력 기사 문장 */
    val focusedQuote: String? = null,
    val narrowTab: NarrowTab = NarrowTab.ARTICLE,
    /** 좁은 화면에서 상세를 띄웠는지 */
    val narrowDetailOpen: Boolean = false,
    /** 대응 문장이 하나도 없을 때도 비교 화면을 펼쳐 보기로 했는지 */
    val showDespiteNoMatch: Boolean = false
)

internal const val OTHERS_GROUP = -1

/** 대응 문장이 없을 때 쓰는 문구. 누락 의도나 편향을 암시하지 않는다. */
internal const val NO_MATCH_TEXT = "현재 수집된 비교 기사에서 대응 문장을 찾지 못했어요."

/* ==================================================================
 * 비교 화면용 모델 — 서버 값을 묶기만 한다. 새 판정을 만들지 않는다.
 * ================================================================== */

internal enum class MatchKind { SAME, DIFF, OTHER }

internal fun kindOf(raw: String): MatchKind = when (Relation.from(raw)) {
    Relation.SAME -> MatchKind.SAME
    Relation.OPPOSES, Relation.DIFFERENT_INTERPRETATION -> MatchKind.DIFF
    Relation.OTHER -> MatchKind.OTHER
}

/** 입력 기사 문장 하나 */
internal data class InputSentence(
    val quote: String,
    /** 서버 comparison.issues 중 이 문장이 연결된 번호 */
    val issueIndexes: List<Int>,
    val sourceClaimIndexes: List<Int>
)

/** 다른 기사에서 찾은 대응 문장 하나 */
internal data class Clip(
    val article: ArticleRef,
    val quote: String,
    /** 서버 relation_to_source 원래 값 */
    val relationRaw: String,
    /** 같은 문장이 연결된 다른 묶음 번호 */
    val alsoIn: List<Int>
)

/** 서버가 보낸 표현 차이 하나 + 그 표현이 실린 기사 */
internal data class ExpressionItem(val article: ArticleRef?, val e: ExpressionDifference)

/** 서버가 보낸 정보 구성 차이 하나 + 기사 */
internal data class InformationItem(val article: ArticleRef?, val i: InformationDifference)

/** 묶음(쟁점 또는 주장)에 붙는 판정 기준 항목. 서버가 보낸 것만 담는다. */
internal data class RubricItems(
    val informations: List<InformationItem> = emptyList(),
    /** 매체의 표현 차이로 보여줄 것 */
    val expressions: List<ExpressionItem> = emptyList(),
    /** 직접 인용 안에 있어 표현 차이로 세지 않고 I5 인용 정보로만 보여줄 것 (D1) */
    val quotedExpressions: List<ExpressionItem> = emptyList()
) {
    val isEmpty: Boolean get() = informations.isEmpty() && expressions.isEmpty() && quotedExpressions.isEmpty()
}

/** 주장 단위 묶음 (서버 comparison.issues 하나) */
internal data class IssueView(
    val index: Int,
    val label: String,
    val inputQuotes: List<String>,
    val same: List<Clip>,
    val diff: List<Clip>,
    val other: List<Clip>,
    /** 이 묶음에서 대응 문장을 찾지 못한 비교 기사 */
    val notFound: List<ArticleRef>,
    val rubric: RubricItems
) {
    val sameOutlets: Int get() = same.map { it.article.articleIndex }.distinct().size
    val diffOutlets: Int get() = diff.map { it.article.articleIndex }.distinct().size
}

/** 질문 단위 쟁점 (서버 rubric.questions 하나) */
internal data class QuestionView(
    val index: Int,
    val id: String,
    val question: String,
    val answers: List<AnswerView>,
    /** 이 쟁점에 걸린 입력 기사 문장들과 대응하는 다른 기사 문장 */
    val same: List<Clip>,
    val diff: List<Clip>,
    val rubric: RubricItems
) {
    val sameOutlets: Int get() = same.map { it.article.articleIndex }.distinct().size
    val diffOutlets: Int get() = diff.map { it.article.articleIndex }.distinct().size
}

internal data class AnswerView(val article: ArticleRef?, val articleIndex: Int, val answer: QuestionAnswer)

internal class ComparisonModel(val analysis: AnalysisResult) {
    val input: ArticleRef = analysis.articles.firstOrNull { it.articleIndex == 0 }
        ?: ArticleRef(0, analysis.sourceTitle, analysis.sourceUrl)
    val others: List<ArticleRef> = analysis.articles.filter { it.articleIndex > 0 }

    /** 서버가 질문 단위 데이터(rubric.questions)를 주면 '쟁점', 아니면 '주장' 단위로 보여준다 */
    val questionMode: Boolean = analysis.rubric?.questions?.isNotEmpty() == true
    val noun: String = if (questionMode) "쟁점" else "주장"

    val inputSentences: List<InputSentence> = buildInputSentences()

    private val allExpressions: List<ExpressionItem> = analysis.rubric?.expressions.orEmpty()
        .map { ExpressionItem(articleOf(it.articleIndex), it) }
    private val allInformations: List<InformationItem> = analysis.rubric?.informations.orEmpty()
        .map { InformationItem(articleOf(it.articleIndex), it) }

    val issues: List<IssueView> = analysis.issues.indices.map { buildIssue(it) }
    val questions: List<QuestionView> = analysis.rubric?.questions.orEmpty().mapIndexed { i, q -> buildQuestion(i, q.id) }

    /** 화면 탭·칩에 쓰는 묶음 수 */
    val groupCount: Int get() = if (questionMode) questions.size else issues.size

    /** 입력 기사 주장과 연결되지 않은 다른 기사 문장 */
    val unlinkedClips: List<Clip> = analysis.issues.flatMap { it.members }
        .filter { m -> m.articleIndex > 0 && m.quote.isNotBlank() && m.sourceClaimIndex == null }
        .mapNotNull { m -> articleOf(m.articleIndex)?.let { Clip(it, cleanQuote(m.quote), m.relationRaw, emptyList()) } }
        .distinctBy { it.article.articleIndex to it.quote }

    /** 다른 기사에서 찾은 대응 문장이 하나도 없음 */
    val noMatches: Boolean =
        issues.all { it.same.isEmpty() && it.diff.isEmpty() && it.other.isEmpty() } && unlinkedClips.isEmpty()

    /** 왼쪽 문장 밑줄용: 입력 기사의 표현 차이 중 직접 인용 밖에 있는 것만 */
    val inputExpressions: List<ExpressionDifference> =
        allExpressions.filter { it.e.articleIndex == 0 && !insideDirectQuote(it.e.sentence, it.e.span) }.map { it.e }

    /** 어느 묶음에도 붙지 않은 rubric 항목 ('그 밖'에 표시) */
    val unassignedRubric: RubricItems by lazy {
        val used = (issues.map { it.rubric } + questions.map { it.rubric })
        val usedE = used.flatMap { it.expressions + it.quotedExpressions }.toSet()
        val usedI = used.flatMap { it.informations }.toSet()
        splitRubric(allInformations.filter { it !in usedI }, allExpressions.filter { it !in usedE })
    }

    fun articleOf(index: Int): ArticleRef? = analysis.articles.firstOrNull { it.articleIndex == index }

    /** 입력 문장과 대응하는 다른 기사 문장 */
    fun matchesFor(s: InputSentence): List<Clip> =
        analysis.issues.flatMap { it.members }
            .filter { it.articleIndex > 0 && it.quote.isNotBlank() && it.sourceClaimIndex in s.sourceClaimIndexes }
            .mapNotNull { m -> articleOf(m.articleIndex)?.let { Clip(it, cleanQuote(m.quote), m.relationRaw, emptyList()) } }
            .distinctBy { Triple(it.article.articleIndex, it.quote, kindOf(it.relationRaw)) }

    /** 입력 문장이 속한 묶음 번호 (쟁점 모드면 질문, 아니면 주장) */
    fun groupsFor(s: InputSentence): List<Int> =
        if (!questionMode) s.issueIndexes
        else questions.filter { q -> q.answers.any { a -> a.articleIndex == 0 && a.answer.evidenceQuotes.any { sameText(it, s.quote) } } }
            .map { it.index }

    /* ---------------- 만들기 ---------------- */

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

        // 이 주장에 붙는 rubric: 서버가 준 문장이 이 주장의 문장과 같을 때만
        val sentences = inputQuotes.map { 0 to it } + clips.map { it.article.articleIndex to it.quote }
        val infos = allInformations.filter { item ->
            val s = item.i.sentence ?: return@filter false
            sentences.any { (idx, q) -> idx == item.i.articleIndex && sameText(q, s) }
        }
        val exprs = allExpressions.filter { item ->
            sentences.any { (idx, q) -> idx == item.e.articleIndex && sameText(q, item.e.sentence) }
        }

        val covered = clips.map { it.article.articleIndex }.toSet()
        return IssueView(
            index = i,
            label = issue.label.ifBlank { "이름 없는 주장" },
            inputQuotes = inputQuotes,
            same = clips.filter { kindOf(it.relationRaw) == MatchKind.SAME },
            diff = clips.filter { kindOf(it.relationRaw) == MatchKind.DIFF },
            other = clips.filter { kindOf(it.relationRaw) == MatchKind.OTHER },
            notFound = others.filter { it.articleIndex !in covered },
            rubric = splitRubric(infos, exprs)
        )
    }

    private fun buildQuestion(index: Int, id: String): QuestionView {
        val q = analysis.rubric!!.questions[index]
        val answers = q.answers.map { AnswerView(articleOf(it.articleIndex), it.articleIndex, it) }
        // 이 쟁점의 입력 기사 근거 문장과 같은 입력 문장 → 그 문장의 대응 문장
        val inputHere = inputSentences.filter { s ->
            answers.any { a -> a.articleIndex == 0 && a.answer.evidenceQuotes.any { sameText(it, s.quote) } }
        }
        val clips = inputHere.flatMap { matchesFor(it) }
            .distinctBy { Triple(it.article.articleIndex, it.quote, kindOf(it.relationRaw)) }
        return QuestionView(
            index = index,
            id = id,
            question = q.question,
            answers = answers,
            same = clips.filter { kindOf(it.relationRaw) == MatchKind.SAME },
            diff = clips.filter { kindOf(it.relationRaw) == MatchKind.DIFF },
            rubric = splitRubric(
                allInformations.filter { it.i.questionId == id },
                allExpressions.filter { it.e.questionId == id }
            )
        )
    }

    /** D1: 직접 인용 안의 표현은 매체의 표현 차이에서 빼서 인용 정보로 옮긴다 */
    private fun splitRubric(infos: List<InformationItem>, exprs: List<ExpressionItem>): RubricItems {
        val (quoted, own) = exprs.partition { insideDirectQuote(it.e.sentence, it.e.span) }
        return RubricItems(informations = infos, expressions = own, quotedExpressions = quoted)
    }
}

/** 따옴표 차이와 앞뒤 공백을 무시하고 같은 문장인지 */
internal fun sameText(a: String, b: String): Boolean {
    val x = cleanQuote(a)
    val y = cleanQuote(b)
    return x == y || (x.length > 12 && y.contains(x)) || (y.length > 12 && x.contains(y))
}

private val DIRECT_QUOTE = Regex("“[^”]*”|\"[^\"]*\"|‘[^’]*’|「[^」]*」|『[^』]*』")

/** 표현이 문장 안의 직접 인용(따옴표 안)에 들어 있는지 */
internal fun insideDirectQuote(sentence: String, span: String): Boolean {
    val start = sentence.indexOf(span)
    if (start < 0) return false
    val end = start + span.length
    return DIRECT_QUOTE.findAll(sentence).any { m -> start >= m.range.first && end <= m.range.last + 1 }
}

/** 서버 문장 양끝을 감싼 따옴표 한 쌍만 걷어낸다. 안쪽 따옴표는 그대로 둔다. */
internal fun cleanQuote(raw: String): String {
    val s = raw.trim()
    val pairs = listOf('“' to '”', '"' to '"', '‘' to '’', '\'' to '\'', '「' to '」', '『' to '』')
    for ((open, close) in pairs) {
        if (s.length >= 2 && s.first() == open && s.last() == close &&
            s.count { it == open || it == close } == 2
        ) return s.substring(1, s.length - 1).trim()
    }
    return s
}

internal fun two(n: Int) = n.toString().padStart(2, '0')
