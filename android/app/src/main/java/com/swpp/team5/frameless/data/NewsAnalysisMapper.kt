package com.swpp.team5.frameless.data

/** Convert a Unicode code-point offset to the UTF-16 index used by Kotlin/Compose. */
internal fun codePointOffsetToUtf16(text: String, offset: Int): Int? {
    if (offset < 0) return null
    val codePointCount = text.codePointCount(0, text.length)
    if (offset > codePointCount) return null
    return text.offsetByCodePoints(0, offset)
}

private fun mappedRange(text: String, start: Int, end: Int, expected: String): TextRange? {
    val utf16Start = codePointOffsetToUtf16(text, start) ?: return null
    val utf16End = codePointOffsetToUtf16(text, end) ?: return null
    val range = TextRange(utf16Start, utf16End)
    return range.takeIf { it.isWithin(text) && text.substring(it.start, it.end) == expected }
}

private fun ApiEvidence.toUi(article: ApiComparisonArticle): ComparisonEvidenceUi? {
    val selectedText = if (scope == "TITLE") article.title else article.body
    val sentenceRange = mappedRange(selectedText, sentence.start, sentence.end, sentence.text) ?: return null
    val markerStart = sentence.start + marker.startInSentence
    val markerEnd = sentence.start + marker.endInSentence
    val markerRange = mappedRange(selectedText, markerStart, markerEnd, marker.text) ?: return null
    if (markerRange.start < sentenceRange.start || markerRange.end > sentenceRange.end) return null
    return ComparisonEvidenceUi(
        scope = scope,
        sentenceRange = sentenceRange,
        sentenceText = sentence.text,
        markerRange = markerRange,
        markerText = marker.text,
        owner = targetOwner,
        speakerLabel = targetSpeakerLabel,
        summary = comparisonSummary
    )
}

internal fun ApiArticleAnalysis.toUi(article: ArticleDetail): ArticleAnalysisUi {
    var discarded = 0
    val mappedFindings = findings.mapNotNull { finding ->
        val selectedText = if (finding.scope == "TITLE") article.title else article.body
        val sentenceRange = mappedRange(
            selectedText, finding.sentence.start, finding.sentence.end, finding.sentence.text
        )
        val markerStart = finding.sentence.start + finding.marker.startInSentence
        val markerEnd = finding.sentence.start + finding.marker.endInSentence
        val markerRange = mappedRange(selectedText, markerStart, markerEnd, finding.marker.text)
        if (sentenceRange == null || markerRange == null ||
            markerRange.start < sentenceRange.start || markerRange.end > sentenceRange.end) {
            discarded += 1
            return@mapNotNull null
        }
        val comparisons = finding.comparisons.mapNotNull { target ->
            val evidence = target.evidence.mapNotNull { row -> row.toUi(target) }
            if (evidence.isEmpty()) {
                if (target.evidence.isNotEmpty()) discarded += target.evidence.size
                null
            } else ComparisonArticleUi(
                articleId = target.articleId,
                publisher = target.publisher,
                title = target.title,
                publishedAt = target.publishedAt,
                originalUrl = target.originalUrl,
                body = target.body,
                evidence = evidence
            )
        }
        FindingUi(
            id = finding.id,
            categories = finding.categories,
            scope = finding.scope,
            sentenceRange = sentenceRange,
            sentenceText = finding.sentence.text,
            markerRange = markerRange,
            markerText = finding.marker.text,
            explanation = finding.explanation,
            comparisons = comparisons
        )
    }
    return ArticleAnalysisUi(state, version, mappedFindings, discarded)
}
