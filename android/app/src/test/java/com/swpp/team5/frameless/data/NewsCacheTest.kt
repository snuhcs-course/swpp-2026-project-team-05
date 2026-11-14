package com.swpp.team5.frameless.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.swpp.team5.frameless.data.local.FrameLESSDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class NewsCacheTest {
    private lateinit var database: FrameLESSDatabase
    private lateinit var cache: NewsCache
    private val publisher = PublisherItem("yna", "연합뉴스", null)
    private val summary = ArticleSummary(
        id = "a1",
        publisher = publisher,
        title = "정책 추진",
        category = NewsCategory.POLITICS,
        publishedAt = "2026-11-14T12:00:00+09:00",
        analysisState = StoredAnalysisState.READY
    )
    private val detail = ArticleDetail(
        id = summary.id,
        publisher = publisher,
        title = summary.title,
        body = "정책을 전격 추진한다.",
        category = summary.category,
        publishedAt = summary.publishedAt,
        originalUrl = "https://example.com/a1",
        analysisState = StoredAnalysisState.READY,
        contentHash = "hash-a1"
    )

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            FrameLESSDatabase::class.java
        ).allowMainThreadQueries().build()
        cache = NewsCache(ApplicationProvider.getApplicationContext(), database)
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun summaryRefreshPreservesPreviouslyCachedDetail() = runBlocking {
        cache.storeArticle(detail)
        cache.storeSummaries(listOf(summary.copy(title = "수정된 제목")))

        val restored = cache.article(summary.id)
        assertEquals("수정된 제목", restored?.title)
        assertEquals(detail.body, restored?.body)
        assertEquals(detail.contentHash, restored?.contentHash)
    }

    @Test
    fun analysisIsReusedOnlyForTheMatchingArticleHash() = runBlocking {
        cache.storeArticle(detail)
        val raw = """{
          "state":"READY","version":"reviewed-v1","source_content_hash":"hash-a1",
          "findings":[]
        }"""
        cache.storeAnalysis(
            detail,
            ApiArticleAnalysis(
                StoredAnalysisState.READY,
                "reviewed-v1",
                emptyList(),
                sourceContentHash = "hash-a1",
                rawJson = raw
            )
        )

        assertEquals("reviewed-v1", cache.analysis(detail)?.version)
        assertNull(cache.analysis(detail.copy(contentHash = "different-hash")))
    }

    @Test
    fun invalidAnalysisDoesNotOverwriteTheLastGroundedCopy() = runBlocking {
        cache.storeArticle(detail)
        val validRaw = """{
          "state":"READY","version":"valid","source_content_hash":"hash-a1",
          "findings":[]
        }"""
        val invalidRaw = """{
          "state":"READY","version":"invalid","source_content_hash":"hash-a1",
          "findings":[{
            "id":"f1","categories":["E"],"explanation":"설명",
            "comparison_overview":"","comparison_articles":[],
            "source":{"scope":"BODY",
              "sentence":{"start":0,"end":99,"text":"존재하지 않는 문장"},
              "marker":{"start_in_sentence":0,"end_in_sentence":2,"text":"존재"}}
          }]
        }"""
        cache.storeAnalysis(
            detail,
            ApiArticleAnalysis(
                StoredAnalysisState.READY, "valid", emptyList(), "hash-a1", validRaw
            )
        )
        cache.storeAnalysis(
            detail,
            ApiArticleAnalysis(
                StoredAnalysisState.READY, "invalid", emptyList(), "hash-a1", invalidRaw
            )
        )

        assertEquals("valid", cache.analysis(detail)?.version)
    }

    @Test
    fun savedSnapshotsAreIsolatedAndClearedPerUser() = runBlocking {
        val savedPage = ArticlePage(
            listOf(summary.copy(isSaved = true, savedAt = "2026-11-14T03:00:00Z")),
            null
        )
        cache.storeSavedPage("user-a", savedPage, replace = true)
        cache.storeSavedPage("user-b", savedPage, replace = true)

        assertTrue(cache.savedArticles("user-a")!!.items.single().isSaved)
        cache.clearSaved("user-a")
        assertNull(cache.savedArticles("user-a"))
        assertFalse(cache.savedArticles("user-b")!!.items.isEmpty())
    }

    @Test
    fun retentionKeepsSavedSummariesInAdditionToThePublicSummaryBudget() = runBlocking {
        val savedItems = (1..100).map { index ->
            summary.copy(
                id = "saved-$index",
                title = "저장 기사 $index",
                isSaved = true,
                savedAt = Instant.ofEpochSecond(index.toLong()).toString()
            )
        }
        cache.storeSavedPage("user-a", ArticlePage(savedItems, null), replace = true)
        cache.storeSummaries((1..101).map { index ->
            summary.copy(id = "public-$index", title = "일반 기사 $index")
        })

        assertEquals(
            200,
            database.newsCacheDao().articles(NewsCategory.POLITICS.wire, 500).size
        )
    }
}
