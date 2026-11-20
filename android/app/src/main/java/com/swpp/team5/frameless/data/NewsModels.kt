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

internal data class PublisherItem(
    val code: String,
    val name: String,
    val logoUrl: String?,
    val isFollowed: Boolean = false
)

internal enum class FeedScope(val wire: String, val label: String) {
    ALL("ALL", "최신"),
    FOLLOWING("FOLLOWING", "팔로잉")
}

internal enum class NewsCategory(val wire: String, val label: String) {
    POLITICS("POLITICS", "정치"),
    SOCIETY("SOCIETY", "사회"),
    ECONOMY("ECONOMY", "경제");

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
    val analysisState: StoredAnalysisState,
    val searchExcerpt: String? = null,
    val isSaved: Boolean = false,
    val savedAt: String? = null
)

internal data class ArticlePage(
    val items: List<ArticleSummary>,
    val nextCursor: String?,
    val totalCount: Int? = null
)

internal data class ArticleDetail(
    val id: String,
    val publisher: PublisherItem,
    val title: String,
    val body: String,
    val category: NewsCategory,
    val publishedAt: String,
    val originalUrl: String,
    val analysisState: StoredAnalysisState,
    val isSaved: Boolean = false,
    val contentHash: String = ""
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
    val comparisonOverview: String,
    val comparisons: List<ApiComparisonArticle>
)
/** Economic lens (specs/features/economic-lens.md): the event and each outlet's view of it in its own words. */
internal data class ApiEconomicView(
    val channels: List<String>,
    val stance: String,
    val direction: String?,
    val phrase: String,
    val scope: String,
    val sentence: ApiSentence
)
internal data class ApiEconomicOutlet(
    val articleId: String,
    val publisher: PublisherItem,
    val title: String,
    val views: List<ApiEconomicView>
)
internal data class ApiEconomicLens(
    val event: String,
    val views: List<ApiEconomicView>,
    val others: List<ApiEconomicOutlet>
)
/**
 * Event issue view (specs/features/event-issue-view.md): the event, what this article puts in front of it in its
 * headline and lead, and where the outlets stand on each issue. Shown as stored; nothing is computed on the device.
 */
internal data class FrameVoice(val speaker: String, val quote: String)
internal data class FrameFront(
    val kind: String,
    val label: String? = null,
    val phrase: String? = null,
    val voices: List<FrameVoice> = emptyList()
)
/** An outlet on an issue; `voice` names the speaker when its position comes from words it quotes. */
internal data class IssueOutlet(val articleId: String, val publisherName: String, val voice: String? = null)
internal data class IssuePosition(val label: String, val isOwn: Boolean, val outlets: List<IssueOutlet>)
internal data class IssueViewUi(
    val question: String,
    val positions: List<IssuePosition>,
    val factOnly: List<IssueOutlet>
)
internal data class FrameLensUi(
    val event: String,
    val frame: String,
    val front: FrameFront?,
    val issues: List<IssueViewUi>
)
internal data class ApiArticleAnalysis(
    val state: StoredAnalysisState,
    val version: String,
    val findings: List<ApiFinding>,
    val sourceContentHash: String = "",
    val rawJson: String = "",
    val economic: ApiEconomicLens? = null,
    val frame: FrameLensUi? = null
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
    val comparisonOverview: String,
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

/** A view of the current article carries its sentence and phrase ranges (UTF-16); other outlets' views do not. */
internal data class EconomicViewUi(
    val channels: List<String>,
    val stance: String,
    val direction: String?,
    val phrase: String,
    val scope: String,
    val sentenceRange: TextRange? = null,
    val phraseRange: TextRange? = null
)
internal data class EconomicOutletUi(
    val articleId: String,
    val publisher: PublisherItem,
    val title: String,
    val views: List<EconomicViewUi>
)
internal data class EconomicLensUi(
    val event: String,
    val views: List<EconomicViewUi>,
    val others: List<EconomicOutletUi>
)

internal data class ArticleAnalysisUi(
    val state: StoredAnalysisState,
    val version: String,
    val findings: List<FindingUi>,
    val discardedMarkerCount: Int = 0,
    val economic: EconomicLensUi? = null,
    val frame: FrameLensUi? = null
)

internal class NewsApiException(
    val code: String,
    override val message: String,
    val fields: Map<String, String> = emptyMap()
) : IllegalStateException(message)
