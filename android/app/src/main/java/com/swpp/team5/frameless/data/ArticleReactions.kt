package com.swpp.team5.frameless.data

internal enum class ReactionKind(val label: String) {
    RECOMMEND("추천해요"), LIKE("좋아요"), MOVED("감동이에요"), ANGRY("화나요"), SAD("슬퍼요")
}

internal data class ArticleReactions(
    val articleId: String,
    val counts: Map<ReactionKind, Long>,
    val selected: ReactionKind?
)

internal data class BreakingHeadline(val article: ArticleSummary, val expiresAt: String)
internal data class IssueHeadline(val story: StorySummary, val reactionUserCount: Long)
internal data class HomeHighlights(val generatedAt: String, val breaking: BreakingHeadline?, val issue: IssueHeadline?)
