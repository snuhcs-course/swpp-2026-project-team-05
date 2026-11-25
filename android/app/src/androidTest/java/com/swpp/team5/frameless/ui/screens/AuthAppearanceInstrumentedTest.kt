package com.swpp.team5.frameless.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.swpp.team5.frameless.ui.theme.Fl
import com.swpp.team5.frameless.ui.theme.FrameLESSTheme
import java.io.File
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class AuthAppearanceInstrumentedTest {
    @get:Rule val compose = createComposeRule()

    @Test fun lightLoginUsesReadableNeutralAction() = checkLoginAppearance(dark = false)

    @Test fun darkLoginUsesReadableNeutralAction() = checkLoginAppearance(dark = true)

    private fun checkLoginAppearance(dark: Boolean) {
        var actionText = Color.Unspecified
        compose.setContent {
            FrameLESSTheme(darkTheme = dark) {
                actionText = Fl.OnAccent
                LoginScreen("", "", false, null, {}, {}, {}, {}, {})
            }
        }
        compose.onNodeWithText("FrameLESS").assertIsDisplayed()
        assertNeutralButton(actionText)
        capture("auth-${if (dark) "dark" else "light"}.png")
    }

    @Test
    fun submittingLoginPreservesInputAndDisablesDuplicateActions() {
        compose.setContent {
            FrameLESSTheme {
                LoginScreen("reader@example.com", "private-value", true, "연결 오류",
                    {}, {}, {}, {}, {})
            }
        }
        compose.onNodeWithTag("auth-primary-action").assertIsNotEnabled()
        compose.onNodeWithText("reader@example.com").assertIsDisplayed().assertIsNotEnabled()
        compose.onNodeWithText("회원가입").assertIsNotEnabled()
        compose.onNodeWithText("로그인 중…").assertIsDisplayed()
        compose.onNodeWithText("연결 오류").assertIsDisplayed()
    }

    @Test
    fun registrationKeepsNeutralActionsAndInlineErrors() {
        var actionText = Color.Unspecified
        compose.setContent {
            FrameLESSTheme(darkTheme = false) {
                actionText = Fl.OnAccent
                RegistrationScreen("reader@example.com", "reader", "", "", false, null,
                    mapOf("email" to "이미 사용 중인 이메일이에요."), {}, {}, {}, {}, {}, {})
            }
        }
        compose.onNodeWithText("reader@example.com").assertIsDisplayed()
        compose.onNodeWithText("이미 사용 중인 이메일이에요.").assertIsDisplayed()
        assertNeutralButton(actionText)
        capture("registration-light.png")
    }

    @Test
    fun analysisPaletteFollowsTheAppAppearanceOverride() {
        var text = Color.Unspecified
        var semanticText = Color.Unspecified
        var semanticBackground = Color.Unspecified
        compose.setContent {
            FrameLESSTheme(darkTheme = true) {
                text = Fl.Ink
                semanticText = Fl.InkSame
                semanticBackground = Fl.TintSame
                Box(Modifier.fillMaxWidth().height(48.dp).background(Fl.TintDiff).testTag("analysis-mark"))
            }
        }
        val pixels = compose.onNodeWithTag("analysis-mark").captureToImage().toPixelMap()
        assertTrue("Marked text must stay readable in app-selected dark mode", contrast(text, pixels[10, 10]) >= 4.5f)
        assertTrue("Semantic analysis labels must keep readable contrast", contrast(semanticText, semanticBackground) >= 4.5f)
    }

    private fun assertNeutralButton(text: Color) {
        compose.onNodeWithTag("auth-primary-action").performScrollTo()
        val pixels = compose.onNodeWithTag("auth-primary-action").captureToImage().toPixelMap()
        val background = pixels[10, pixels.height / 2]
        assertTrue("Primary action must not keep the old blue/purple fill",
            abs(background.red - background.green) < 0.025f && abs(background.green - background.blue) < 0.025f)
        assertTrue("Primary action text must meet 4.5:1 contrast", contrast(text, background) >= 4.5f)
    }

    private fun capture(name: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        File(context.cacheDir, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun contrast(first: Color, second: Color): Float =
        (max(first.luminance(), second.luminance()) + 0.05f) /
            (min(first.luminance(), second.luminance()) + 0.05f)
}
