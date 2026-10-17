package com.swpp.team5.frameless.ui.state

import com.swpp.team5.frameless.data.AnalysisResult
import com.swpp.team5.frameless.data.ArticleRef
import com.swpp.team5.frameless.data.ExpressionDifference
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
internal enum class NarrowTab { ARTICLE, ISSUES }

/** 비교 화면에서 사용자가 고른 것 */
internal data class ComparisonSelection(
    /** 선택한 쟁점 번호. OTHERS_ISSUE면 '그 밖의 문장' */
    val issue: Int = 0,
    /** 왼쪽에서 누른 입력 기사 문장 */
    val focusedQuote: String? = null,
    val narrowTab: NarrowTab = NarrowTab.ARTICLE,
    /** 좁은 화면에서 쟁점 상세를 띄웠는지 */
    val narrowDetailOpen: Boolean = false
)

internal const val OTHERS_ISSUE = -1

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
    val issueIndexes: List<Int>,
    val sourceClaimIndexes: List<Int>
)

/** 다른 기사에서 찾은 대응 문장 하나 */
internal data class Clip(
    val article: ArticleRef,
    val quote: String,
    /** 서버 relation_to_source 원래 값 */
    val relationRaw: String,
    /** 같은 문장이 연결된 다른 쟁점 번호 */
    val alsoIn: List<Int>
)

internal data class IssueView(
    val index: Int,
    val label: String,
    val inputQuotes: List<String>,
    val same: List<Clip>,
    val diff: List<Clip>,
    val other: List<Clip>,
    /** 이 쟁점에서 대응 문장을 찾지 못한 비교 기사 */
    val notFound: List<ArticleRef>
) {
    val sameOutlets: Int get() = same.map { it.article.articleIndex }.distinct().size
    val diffOutlets: Int get() = diff.map { it.article.articleIndex }.distinct().size
}

internal class ComparisonModel(val analysis: AnalysisResult) {
    val input: ArticleRef = analysis.articles.firstOrNull { it.articleIndex == 0 }
        ?: ArticleRef(0, analysis.sourceTitle, analysis.sourceUrl)
    val others: List<ArticleRef> = analysis.articles.filter { it.articleIndex > 0 }

    val inputSentences: List<InputSentence> = buildInputSentences()
    val issues: List<IssueView> = analysis.issues.indices.map { buildIssue(it) }

    /** 입력 기사 주장과 연결되지 않은 다른 기사 문장 */
    val unlinkedClips: List<Clip> = analysis.issues.flatMap { it.members }
        .filter { m -> m.articleIndex > 0 && m.quote.isNotBlank() && m.sourceClaimIndex == null }
        .mapNotNull { m -> articleOf(m.articleIndex)?.let { Clip(it, cleanQuote(m.quote), m.relationRaw, emptyList()) } }
        .distinctBy { it.article.articleIndex to it.quote }

    /** 서버가 rubric을 보낸 경우에만 값이 있다 */
    val inputExpressions: List<ExpressionDifference> =
        analysis.rubric?.expressions.orEmpty().filter { it.articleIndex == 0 }

    fun articleOf(index: Int): ArticleRef? = analysis.articles.firstOrNull { it.articleIndex == index }

    /** 입력 문장과 대응하는 다른 기사 문장 */
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
            same = clips.filter { kindOf(it.relationRaw) == MatchKind.SAME },
            diff = clips.filter { kindOf(it.relationRaw) == MatchKind.DIFF },
            other = clips.filter { kindOf(it.relationRaw) == MatchKind.OTHER },
            notFound = others.filter { it.articleIndex !in covered }
        )
    }
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
