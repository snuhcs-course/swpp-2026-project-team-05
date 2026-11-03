package com.swpp.team5.frameless.ui.screens

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UrlAnalysisMockTest {
    @Test
    fun acceptsOnlyAbsoluteHttpUrls() {
        assertTrue(isValidMockArticleUrl("https://news.example.com/article"))
        assertTrue(isValidMockArticleUrl("  http://example.com/story?id=1  "))

        assertFalse(isValidMockArticleUrl(""))
        assertFalse(isValidMockArticleUrl("/relative/article"))
        assertFalse(isValidMockArticleUrl("ftp://news.example.com/article"))
        assertFalse(isValidMockArticleUrl("https:///missing-host"))
        assertFalse(isValidMockArticleUrl("not-a-url"))
    }
}
