package com.swpp.team5.frameless.ui.screens

import com.swpp.team5.frameless.data.ArticleSummary
import com.swpp.team5.frameless.data.NewsCategory
import com.swpp.team5.frameless.data.PublisherItem
import com.swpp.team5.frameless.data.StoredAnalysisState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class FeedHomeChromeTest {
    private fun article(publisherName: String, category: NewsCategory = NewsCategory.ECONOMY) = ArticleSummary(
        "a1", PublisherItem("p1", publisherName, null), "제목", category,
        "2026-11-19T09:00:00+09:00", StoredAnalysisState.READY
    )

    @Test
    fun menuKeepsOnlyTutorialReplay() {
        assertEquals(
            listOf("튜토리얼 다시 보기"),
            FeedMenuItem.entries.map { it.label }
        )
    }

    @Test
    fun rowMetadataIsCategoryThenPublisherThenTime() {
        assertEquals(
            listOf("경제", "연합뉴스", "3시간 전"),
            feedRowMetaParts(article("연합뉴스"), "3시간 전")
        )
        assertEquals(
            listOf("정치", "연합뉴스", "방금"),
            feedRowMetaParts(article("연합뉴스", NewsCategory.POLITICS), "방금")
        )
    }

    @Test
    fun rowMetadataDropsBlankPartsInsteadOfInventingThem() {
        assertEquals(listOf("사회", "11월 19일 09:00"),
            feedRowMetaParts(article("  ", NewsCategory.SOCIETY), "11월 19일 09:00"))
        assertEquals(listOf("경제", "연합뉴스"), feedRowMetaParts(article("연합뉴스"), ""))
        assertFalse(feedRowMetaParts(article(""), " ").any { it.isBlank() })
    }

    @Test
    fun featuredSplitTakesTheFirstArticleOnceAndKeepsTheRestInOrder() {
        val a = article("연합뉴스").copy(id = "a")
        val b = article("한겨레").copy(id = "b")
        val c = article("경향신문").copy(id = "c")

        val split = splitFeaturedArticle(listOf(a, b, c), enabled = true)
        assertEquals(a, split.featured)
        assertEquals(listOf(b, c), split.rest)

        val single = splitFeaturedArticle(listOf(a), enabled = true)
        assertEquals(a, single.featured)
        assertEquals(emptyList<ArticleSummary>(), single.rest)
    }

    @Test
    fun featuredSplitLeavesTheListUntouchedWhenDisabledOrEmpty() {
        val a = article("연합뉴스").copy(id = "a")
        val b = article("한겨레").copy(id = "b")

        val disabled = splitFeaturedArticle(listOf(a, b), enabled = false)
        assertNull(disabled.featured)
        assertEquals(listOf(a, b), disabled.rest)

        val empty = splitFeaturedArticle(emptyList(), enabled = true)
        assertNull(empty.featured)
        assertEquals(emptyList<ArticleSummary>(), empty.rest)
    }

    @Test
    fun bylineIsPublisherThenTimeWithoutTheCategory() {
        assertEquals("연합뉴스  ·  3시간 전", feedByline(article("연합뉴스"), "3시간 전"))
        assertEquals("연합뉴스", feedByline(article("연합뉴스"), " "))
        assertEquals("방금", feedByline(article("  "), "방금"))
        assertEquals("", feedByline(article(""), ""))
    }

    @Test
    fun eachCategoryHasItsOwnArtDirection() {
        val palettes = NewsCategory.entries.map { categoryPalette(it) }
        assertEquals(NewsCategory.entries.size, palettes.toSet().size)
        assertEquals(NewsCategory.entries.size, palettes.map { it.ink }.toSet().size)
    }
}
