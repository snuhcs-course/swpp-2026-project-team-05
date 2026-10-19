package com.swpp.team5.frameless.data

/*
 * 서버 응답을 담는 데이터 모델. UI 코드와 분리해 둔다.
 * 서버 값을 그대로 보존하고, 화면용 판정을 여기서 만들지 않는다.
 */

/* ------------------------------------------------------------------
 * 1. 현재 서버가 실제로 주는 데이터
 *    (source_analysis / comparison.issues / related_articles)
 * ------------------------------------------------------------------ */

internal enum class ClaimType { SHARED, DIFFERENT }

/** 서버 relation_to_source 값을 그대로 보존한다. 알 수 없는 값도 버리지 않는다. */
internal enum class Relation(val wire: String, val label: String) {
    SAME("same", "같은 내용"),
    OPPOSES("opposes", "반대되는 주장"),
    DIFFERENT_INTERPRETATION("different_interpretation", "다른 해석"),
    SAME_ISSUE("same_issue", "같은 쟁점의 다른 내용"),
    UNCERTAIN("uncertain", "관계 판정 불확실"),
    OTHER("", "기타");

    companion object {
        fun from(raw: String): Relation = values().firstOrNull { it.wire == raw && it != OTHER } ?: OTHER
    }
}

internal data class SourcePassage(
    val outlet: String,
    val excerpt: String,
    val url: String?,
    /** 입력 기사 문장이면 null. 다른 기사 문장이면 서버 relation_to_source 원문 값. */
    val relationRaw: String? = null
)

internal data class ClaimPreview(
    val type: ClaimType,
    val summary: String,
    val explanation: String,
    val passages: List<SourcePassage>
)

/** 입력 기사(article_index 0)를 포함해, 응답에 등장한 기사 하나. */
internal data class ArticleRef(
    val articleIndex: Int,
    val title: String,
    val url: String?
)

internal data class SourceClaim(
    val index: Int,
    val text: String,
    val quote: String
)

/** comparison.issues[].members[] 한 개. */
internal data class IssueMember(
    val articleIndex: Int,
    val title: String,
    val url: String?,
    val sourceClaimIndex: Int?,
    val relationRaw: String,
    val quote: String,
    /** 이 관계 판정의 주체: reporter | attributed_speaker | uncertain. 구 API에는 없음. */
    val relationOwnerRaw: String? = null,
    val speaker: String = "",
    /** direct | indirect | none | uncertain. 구 API에는 없음. */
    val speechKindRaw: String? = null,
    val hasMultipleVoices: Boolean = false,
    val relationWarning: String? = null
) {
    val relation: Relation get() = Relation.from(relationRaw)
}

/** comparison.issues[] 한 개. label은 서버 issue 문자열 그대로. */
internal data class IssueGroup(
    val label: String,
    val members: List<IssueMember>,
    /** v2의 원본 주장 ID. 문장 유사도로 다른 주장에 루브릭을 붙이지 않는다. */
    val claimId: String? = null
)

/* ------------------------------------------------------------------
 * 2. 판정 기준 v0.2.1 데이터
 *    응답 최상위에 "rubric" 객체가 있을 때만 채워진다. 없으면 null.
 *    앱은 질문 묶음, 유표성, 누락 판정을 만들거나 추정하지 않는다.
 * ------------------------------------------------------------------ */

internal data class RubricResult(
    val version: String,
    val judgmentStatus: String,
    val questions: List<RubricQuestion>,
    val expressions: List<ExpressionDifference>,
    val informations: List<InformationDifference>,
    /** A–G 표현 검증에 실패한 v2 주장 ID. 표현 차이가 없다는 뜻이 아니다. */
    val expressionUnverifiedClaimIds: Set<String>,
    /** 형식 검사에서 버린 항목 수. 0이 아니면 서버 출력 점검이 필요하다. */
    val droppedCount: Int
)

internal data class RubricQuestion(
    val id: String,
    val question: String,
    val answers: List<QuestionAnswer>
)

internal data class QuestionAnswer(
    val articleIndex: Int,
    val answered: Boolean,
    val summary: String,
    val evidenceQuotes: List<String>,
    /** P4 [제안]: timing_impossible | format | editorial | unknown. 없으면 null. */
    val absenceReason: String?,
    /** P4 [제안]: issue_relevant | peripheral. 없으면 null. */
    val relevance: String?
)

internal data class CategoryMark(
    /** 채택된 A~G만 표시한다. */
    val code: String,
    /** 잠정 상대 유표성 0~2. 보류되면 null이며 0은 절대 중립이 아니다. */
    val markedness: Int?
)

internal data class ExpressionDifference(
    val articleIndex: Int,
    val questionId: String?,
    val claimId: String?,
    val sentence: String,
    val span: String,
    val categories: List<CategoryMark>,
    val baselineText: String,
    /** outlet 또는 빈 문자열. P1 중립 재작성은 미적용. */
    val baselineSource: String,
    val baselineArticleIndex: Int?,
    val target: String?,
    /** negative | positive | none */
    val polarity: String,
    /** direct | indirect, 없으면 null */
    val aim: String?,
    /** provisional | needs_review | needs_comparison */
    val status: String,
    val held: Boolean,
    val note: String
) {
    val maxMarkedness: Int? get() = categories.mapNotNull { it.markedness }.maxOrNull()
}

internal data class InformationDifference(
    /** I1~I5 */
    val code: String,
    val articleIndex: Int,
    val questionId: String?,
    val claimId: String?,
    val sentence: String?,
    val value: String,
    val target: String?,
    val polarity: String?
)

/** v2 처리 상태. 실패와 실제 비교 결과 없음은 별도로 보존한다. */
internal data class AnalysisStatus(
    val executionStatus: String,
    val comparisonStatus: String,
    val warnings: List<String>,
    /** Server error stages let the Reader explain a partial search without blaming sentence review. */
    val errorStages: Set<String> = emptySet()
)

/** Reader의 위치는 각 기사 body 문자열의 UTF-16 인덱스, 끝은 포함하지 않는다. */
internal data class ReaderSpan(val start: Int, val end: Int) {
    fun isWithin(body: String): Boolean = start >= 0 && end > start && end <= body.length
    fun isWithin(outer: ReaderSpan): Boolean = start >= outer.start && end <= outer.end && end > start
}

internal data class ReaderSentence(
    val id: String,
    val span: ReaderSpan,
    val text: String,
    val paragraphIndex: Int
)

internal data class ReaderArticle(
    val id: String,
    val articleIndex: Int,
    val title: String,
    val url: String?,
    val body: String,
    val sentences: List<ReaderSentence>
)

internal data class ReaderExpressionDifference(
    val code: String,
    val note: String,
    val sourceMarkerSpan: ReaderSpan,
    val relatedMarkerSpan: ReaderSpan,
    val status: String
)

internal data class ReaderPair(
    val id: String,
    val sourceSentenceId: String,
    val sourceSpan: ReaderSpan,
    val sourceEvidenceSpan: ReaderSpan,
    val articleId: String,
    val articleIndex: Int,
    val relatedSentenceId: String,
    val relatedSpan: ReaderSpan,
    val relatedEvidenceSpan: ReaderSpan?,
    val relation: String,
    val differenceSummary: String,
    val expressionDifferences: List<ReaderExpressionDifference>
)

internal data class ReaderHighlight(
    val claimId: String?,
    val sourceSentenceId: String,
    val sourceSpan: ReaderSpan,
    val sourceEvidenceSpan: ReaderSpan?,
    val pairs: List<ReaderPair>
) {
    val expressionCodes: List<String> get() = pairs.flatMap { pair ->
        pair.expressionDifferences.map { it.code }
    }.distinct().sorted()
}

/** I1의 내용과 이 내용이 실제로 인용된 원문 구간. */
internal data class ReaderI1Evidence(
    val content: String,
    val sourceEvidenceSpan: ReaderSpan
)

/** 별표는 검증된 서버 항목과 원문 근거가 모두 있을 때만 표시한다. */
internal data class ReaderI1Star(
    val sourceSentenceId: String,
    val sourceSpan: ReaderSpan,
    val checkedArticleCount: Int,
    val evidence: List<ReaderI1Evidence>
)

internal data class ReaderResult(
    val version: String,
    val coverage: String,
    val sourceArticle: ReaderArticle,
    val relatedArticles: List<ReaderArticle>,
    val highlights: List<ReaderHighlight>,
    val pairs: List<ReaderPair>,
    val i1Stars: List<ReaderI1Star>,
    val i1Status: String,
    val articleFailureCount: Int
) {
    fun article(id: String): ReaderArticle? = relatedArticles.firstOrNull { it.id == id }
}

/* ------------------------------------------------------------------
 * 3. 화면이 받는 결과
 * ------------------------------------------------------------------ */

internal data class AnalysisResult(
    val sourceTitle: String,
    val sourceUrl: String,
    val coreEvent: String,
    val relatedArticleCount: Int,
    /** 기존 화면용: 입력 기사 클레임별 공통/차이 카드 */
    val claims: List<ClaimPreview>,
    /** article_index 0 = 입력 기사, 1 이상 = 응답 members에 등장한 다른 기사 */
    val articles: List<ArticleRef>,
    /** related_articles에는 있지만 어떤 issue member에도 등장하지 않은 기사 제목 */
    val relatedWithoutMatches: List<String>,
    val sourceClaims: List<SourceClaim>,
    val issues: List<IssueGroup>,
    /** 다른 기사 어디에서도 대응 문장을 찾지 못한 입력 기사 클레임 */
    val sourceOnlyClaims: List<SourceClaim>,
    /** 입력 기사 클레임과 연결되지 않은 다른 기사 문장 (source_claim_index 없음) */
    val relatedOnlyMembers: List<IssueMember>,
    /** 판정 기준 v0.2.1 데이터. 이번 분석에 없으면 null. */
    val rubric: RubricResult?,
    val analysisStatus: AnalysisStatus? = null,
    /** Optional additive Reader contract; null keeps the legacy comparison screen. */
    val reader: ReaderResult? = null
) {
    fun articleTitle(index: Int): String =
        articles.firstOrNull { it.articleIndex == index }?.title
            ?: if (index == 0) sourceTitle else "다른 출처 $index"
}
