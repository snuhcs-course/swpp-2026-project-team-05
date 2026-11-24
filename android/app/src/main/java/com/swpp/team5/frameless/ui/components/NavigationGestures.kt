package com.swpp.team5.frameless.ui.components

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlin.math.abs

private val BackEdgeWidth = 28.dp
private val BackCommitDistance = 72.dp
private const val BackHorizontalDominance = 1.25f

/**
 * Adds an app-level fallback for Android's system back gesture.
 *
 * It deliberately listens only at the left edge and does not consume an event
 * until a clearly horizontal drag crosses the commit distance. Vertical reading
 * and in-content horizontal pagers therefore keep ownership of ordinary drags.
 */
internal fun Modifier.edgeSwipeBack(
    enabled: Boolean = true,
    onBack: () -> Unit
): Modifier = composed {
    val density = LocalDensity.current
    val edgeWidthPx = with(density) { BackEdgeWidth.toPx() }
    val commitDistancePx = with(density) { BackCommitDistance.toPx() }
    val currentOnBack by rememberUpdatedState(onBack)

    pointerInput(enabled, edgeWidthPx, commitDistancePx) {
        if (!enabled) return@pointerInput
        awaitEachGesture {
            val down = awaitFirstDown(
                requireUnconsumed = false,
                pass = PointerEventPass.Initial
            )
            if (down.position.x > edgeWidthPx) return@awaitEachGesture

            val start = down.position
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                val deltaX = change.position.x - start.x
                val deltaY = change.position.y - start.y
                if (edgeBackGestureCompleted(deltaX, deltaY, commitDistancePx)) {
                    change.consume()
                    currentOnBack()
                    break
                }
                if (!change.pressed) break
            }
        }
    }
}

internal fun edgeBackGestureCompleted(
    deltaX: Float,
    deltaY: Float,
    commitDistancePx: Float
): Boolean = deltaX >= commitDistancePx &&
    deltaX > abs(deltaY) * BackHorizontalDominance
