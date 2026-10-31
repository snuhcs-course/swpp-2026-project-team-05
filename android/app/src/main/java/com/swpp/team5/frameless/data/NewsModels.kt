package com.swpp.team5.frameless.data

internal data class NewsUser(
    val id: String,
    val email: String?,
    val username: String,
    val isDeveloper: Boolean
)

internal data class AppSession(
    val user: NewsUser,
    val token: String,
    val expiresAt: String
) {
    val username: String get() = user.username
}

internal data class PublisherItem(val code: String, val name: String, val logoUrl: String?)

internal enum class NewsCategory(val wire: String, val label: String) {
    POLITICS("POLITICS", "정치"),
    SOCIETY("SOCIETY", "사회");

    companion object {
        fun fromWire(value: String): NewsCategory = entries.firstOrNull { it.wire == value } ?: POLITICS
    }
}

internal enum class StoredAnalysisState {
    NOT_STARTED, UPDATING, READY, PARTIAL, FAILED;

    companion object {
        fun fromWire(value: String): StoredAnalysisState =
            entries.firstOrNull { it.name == value } ?: NOT_STARTED
    }
}

internal data class ArticleSummary(
    val id: String,
    val publisher: PublisherItem,
    val title: String,
    val category: NewsCategory,
    val publishedAt: String,
    val analysisState: StoredAnalysisState
)

internal data class ArticlePage(val items: List<ArticleSummary>, val nextCursor: String?)

internal data class ArticleDetail(
    val id: String,
    val publisher: PublisherItem,
    val title: String,
    val body: String,
    val category: NewsCategory,
    val publishedAt: String,
    val originalUrl: String,
    val analysisState: StoredAnalysisState
)

/** API offsets are Unicode code points. They are converted before reaching Compose. */
internal data class ApiSentence(val start: Int, val end: Int, val text: String)
internal data class ApiMarker(val startInSentence: Int, val endInSentence: Int, val text: String)
internal data class ApiEvidence(
    val scope: String,
    val sentence: ApiSentence,
    val marker: ApiMarker,
    val targetOwner: String,
    val targetSpeakerLabel: String?,
    val comparisonSummary: String
)
internal data class ApiComparisonArticle(
    val articleId: String,
    val publisher: PublisherItem,
    val title: String,
    val publishedAt: String,
    val originalUrl: String,
    val body: String,
    val evidence: List<ApiEvidence>
)
internal data class ApiFinding(
    val id: String,
    val categories: List<String>,
    val scope: String,
    val sentence: ApiSentence,
    val marker: ApiMarker,
    val explanation: String,
    val comparisons: List<ApiComparisonArticle>
)
internal data class ApiArticleAnalysis(
    val state: StoredAnalysisState,
    val version: String,
    val findings: List<ApiFinding>
)

internal data class TextRange(val start: Int, val end: Int) {
    fun isWithin(text: String): Boolean = start >= 0 && end > start && end <= text.length
}

internal data class FindingUi(
    val id: String,
    val categories: List<String>,
    val scope: String,
    val sentenceRange: TextRange,
    val sentenceText: String,
    val markerRange: TextRange,
    val markerText: String,
    val explanation: String,
    val comparisons: List<ComparisonArticleUi>
)

internal data class ComparisonEvidenceUi(
    val scope: String,
    val sentenceRange: TextRange,
    val sentenceText: String,
    val markerRange: TextRange,
    val markerText: String,
    val owner: String,
    val speakerLabel: String?,
    val summary: String
)

internal data class ComparisonArticleUi(
    val articleId: String,
    val publisher: PublisherItem,
    val title: String,
    val publishedAt: String,
    val originalUrl: String,
    val body: String,
    val evidence: List<ComparisonEvidenceUi>
)

internal data class ArticleAnalysisUi(
    val state: StoredAnalysisState,
    val version: String,
    val findings: List<FindingUi>,
    val discardedMarkerCount: Int = 0
)

internal class NewsApiException(
    val code: String,
    override val message: String,
    val fields: Map<String, String> = emptyMap()
) : IllegalStateException(message)
