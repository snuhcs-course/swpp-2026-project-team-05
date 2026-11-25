package com.swpp.team5.frameless.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlin.math.abs

private val BackEdgeWidth = 28.dp
private val BackCommitDistance = 72.dp
private val BackMinimumFlingDistance = 24.dp
private val BackMinimumFlingVelocity = 900.dp
private const val BackHorizontalDominance = 1.25f
private const val BackExitDurationMillis = 180

/**
 * Adds an app-level fallback for Android's system back gesture.
 *
 * It listens only at the left edge and does not consume an event until a clearly
 * horizontal drag wins. The surface then follows the finger, springs back after
 * a cancelled gesture, or finishes its exit before invoking Back. Vertical
 * reading and in-content horizontal pagers keep ownership of ordinary drags.
 */
internal fun Modifier.edgeSwipeBack(
    enabled: Boolean = true,
    onBack: () -> Unit
): Modifier = composed {
    val density = LocalDensity.current
    val edgeWidthPx = with(density) { BackEdgeWidth.toPx() }
    val commitDistancePx = with(density) { BackCommitDistance.toPx() }
    val minimumFlingDistancePx = with(density) { BackMinimumFlingDistance.toPx() }
    val minimumFlingVelocityPx = with(density) { BackMinimumFlingVelocity.toPx() }
    val currentOnBack by rememberUpdatedState(onBack)
    var translation by remember { mutableFloatStateOf(0f) }
    var widthPx by remember { mutableFloatStateOf(1f) }
    val shadowPx = with(density) { 10.dp.toPx() }

    LaunchedEffect(enabled) {
        if (!enabled) translation = 0f
    }

    this
        .onSizeChanged { widthPx = it.width.toFloat().coerceAtLeast(1f) }
        .pointerInput(
            enabled,
            edgeWidthPx,
            commitDistancePx,
            minimumFlingDistancePx,
            minimumFlingVelocityPx
        ) {
            if (!enabled) return@pointerInput
            while (true) {
                val drag = awaitPointerEventScope {
                    val down = awaitFirstDown(
                        requireUnconsumed = false,
                        pass = PointerEventPass.Initial
                    )
                    if (down.position.x > edgeWidthPx) return@awaitPointerEventScope null

                    val start = down.position
                    val velocity = VelocityTracker().apply {
                        addPosition(down.uptimeMillis, down.position)
                    }
                    var horizontalDrag = false
                    var lastDeltaX = 0f
                    var lastDeltaY = 0f

                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        velocity.addPosition(change.uptimeMillis, change.position)
                        lastDeltaX = change.position.x - start.x
                        lastDeltaY = change.position.y - start.y

                        if (!horizontalDrag) {
                            val distance = maxOf(abs(lastDeltaX), abs(lastDeltaY))
                            if (distance < viewConfiguration.touchSlop) {
                                if (!change.pressed) break
                                continue
                            }
                            if (lastDeltaX <= 0f ||
                                lastDeltaX <= abs(lastDeltaY) * BackHorizontalDominance) {
                                break
                            }
                            horizontalDrag = true
                        }

                        change.consume()
                        translation = edgeBackVisualOffset(lastDeltaX, widthPx)
                        if (!change.pressed) break
                    }

                    if (!horizontalDrag) null else EdgeBackDrag(
                        deltaX = lastDeltaX,
                        deltaY = lastDeltaY,
                        velocityX = velocity.calculateVelocity().x
                    )
                }

                if (drag == null) continue
                val completes = edgeBackGestureCompleted(
                    deltaX = drag.deltaX,
                    deltaY = drag.deltaY,
                    commitDistancePx = commitDistancePx,
                    velocityX = drag.velocityX,
                    minimumFlingDistancePx = minimumFlingDistancePx,
                    minimumFlingVelocityPx = minimumFlingVelocityPx
                )
                val settle = Animatable(translation)
                if (completes) {
                    settle.animateTo(
                        targetValue = widthPx,
                        animationSpec = tween(
                            durationMillis = BackExitDurationMillis,
                            easing = FastOutSlowInEasing
                        )
                    ) { translation = value }
                    currentOnBack()
                    translation = 0f
                } else {
                    settle.animateTo(
                        targetValue = 0f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        )
                    ) { translation = value }
                }
            }
        }
        .graphicsLayer {
            translationX = translation
            shadowElevation = if (translation > 0f) shadowPx else 0f
        }
}

private data class EdgeBackDrag(val deltaX: Float, val deltaY: Float, val velocityX: Float)

internal fun edgeBackGestureCompleted(
    deltaX: Float,
    deltaY: Float,
    commitDistancePx: Float,
    velocityX: Float = 0f,
    minimumFlingDistancePx: Float = Float.POSITIVE_INFINITY,
    minimumFlingVelocityPx: Float = Float.POSITIVE_INFINITY
): Boolean {
    val isHorizontal = deltaX > 0f && deltaX > abs(deltaY) * BackHorizontalDominance
    val crossedDistance = deltaX >= commitDistancePx
    val deliberateFling = deltaX >= minimumFlingDistancePx && velocityX >= minimumFlingVelocityPx
    return isHorizontal && (crossedDistance || deliberateFling)
}

internal fun edgeBackVisualOffset(deltaX: Float, widthPx: Float): Float =
    deltaX.coerceIn(0f, widthPx.coerceAtLeast(0f))
