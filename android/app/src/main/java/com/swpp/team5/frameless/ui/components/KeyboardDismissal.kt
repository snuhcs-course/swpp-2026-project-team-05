package com.swpp.team5.frameless.ui.components

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager

/**
 * Clears text-field focus after a completed background or child tap without
 * consuming that gesture. Child controls still receive the same tap normally.
 */
internal fun Modifier.dismissKeyboardOnTap(): Modifier = composed {
    val focusManager = LocalFocusManager.current
    pointerInput(focusManager) {
        awaitEachGesture {
            awaitFirstDown(
                requireUnconsumed = false,
                pass = PointerEventPass.Initial
            )
            if (waitForUpOrCancellation(pass = PointerEventPass.Initial) != null) {
                focusManager.clearFocus()
            }
        }
    }
}
