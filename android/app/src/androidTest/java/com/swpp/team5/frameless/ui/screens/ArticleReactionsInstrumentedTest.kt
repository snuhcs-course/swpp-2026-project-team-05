package com.swpp.team5.frameless.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.swpp.team5.frameless.data.ArticleReactions
import com.swpp.team5.frameless.data.ReactionKind
import com.swpp.team5.frameless.ui.theme.FrameLESSTheme
import com.swpp.team5.frameless.ui.theme.Fl
import com.swpp.team5.frameless.ui.viewmodel.ArticleReactionState
import com.swpp.team5.frameless.ui.viewmodel.ReactionIntent
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ArticleReactionsInstrumentedTest {
    @get:Rule val compose = createComposeRule()
    private val page = ArticleReactions("a", ReactionKind.entries.associateWith { if (it == ReactionKind.LIKE) 12_345L else 0L }, ReactionKind.LIKE)

    @Test fun fiveOriginalVectorsKeepSelectionCountsAndPendingGeometryInBothThemes() {
        val dark = mutableStateOf(false)
        val state = mutableStateOf(ArticleReactionState(page = page))
        var chosen: ReactionKind? = null
        compose.setContent {
            FrameLESSTheme(darkTheme = dark.value) {
                ArticleReactionSection(state.value, { chosen = it }, {}, Modifier.background(Fl.Card))
            }
        }
        for (isDark in listOf(false, true)) {
            compose.runOnIdle { dark.value = isDark }
            ReactionKind.entries.forEach { kind ->
                compose.onNodeWithTag("reaction-${kind.name}").assertIsDisplayed().assertHeightIsAtLeast(48.dp)
            }
            compose.onNodeWithTag("reaction-LIKE").assertIsSelected().performClick()
            compose.runOnIdle { assertEquals(ReactionKind.LIKE, chosen) }
            compose.mainClock.advanceTimeBy(500)
            compose.onNodeWithText("1.2만").assertIsDisplayed()
            compose.onNodeWithTag("reaction-LIKE").assertContentDescriptionEquals("좋아요, 12345 명")
            capture("reactions-$isDark.png")
        }
        val width = compose.onNodeWithTag("reaction-LIKE").fetchSemanticsNode().boundsInRoot.width
        compose.runOnIdle { state.value = state.value.copy(pending = true, intent = ReactionIntent(ReactionKind.ANGRY)) }
        ReactionKind.entries.forEach { compose.onNodeWithTag("reaction-${it.name}").assertIsNotEnabled() }
        assertEquals(width, compose.onNodeWithTag("reaction-LIKE").fetchSemanticsNode().boundsInRoot.width, 0.5f)
        compose.onNodeWithText("1.2만").assertIsDisplayed()
    }

    @Test fun unknownCountsAreNotZeroAndFailuresHaveAnIndependentRetry() {
        val state = mutableStateOf(ArticleReactionState(loading = true))
        var retries = 0
        compose.setContent { FrameLESSTheme { ArticleReactionSection(state.value, {}, { retries++ }) } }
        compose.onAllNodesWithText("0").assertCountEquals(0)
        compose.onNodeWithTag("reaction-LIKE").assertIsNotEnabled()
        compose.runOnIdle { state.value = ArticleReactionState(error = "반응을 불러오지 못했어요") }
        compose.onNodeWithText("다시 시도").performClick()
        compose.runOnIdle { assertEquals(1, retries) }
    }

    @Test fun allReactionsRemainReadableWithLargeTextOnANarrowPhone() {
        compose.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, fontScale = 1.6f)) {
                FrameLESSTheme { Box(Modifier.width(360.dp)) { ArticleReactionSection(ArticleReactionState(page = page), {}, {}) } }
            }
        }
        ReactionKind.entries.forEach { kind -> compose.onNodeWithTag("reaction-${kind.name}").assertIsDisplayed() }
        compose.onNodeWithText("감동이에요").assertIsDisplayed()
        val top = compose.onNodeWithTag("reaction-count-LIKE", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.top
        ReactionKind.entries.forEach { kind ->
            assertEquals(top, compose.onNodeWithTag("reaction-count-${kind.name}", useUnmergedTree = true)
                .fetchSemanticsNode().boundsInRoot.top, 1f)
        }
        capture("reactions-large-text.png")
    }

    private fun capture(name: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        java.io.File(context.cacheDir, name).outputStream().use {
            compose.onNodeWithTag("article-reactions").captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
