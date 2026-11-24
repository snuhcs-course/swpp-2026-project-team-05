package com.swpp.team5.frameless.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationGesturesTest {
    @Test
    fun commitsOnlyAfterAForwardHorizontalDragCrossesTheThreshold() {
        assertTrue(edgeBackGestureCompleted(deltaX = 73f, deltaY = 20f, commitDistancePx = 72f))
        assertFalse(edgeBackGestureCompleted(deltaX = 71f, deltaY = 0f, commitDistancePx = 72f))
        assertFalse(edgeBackGestureCompleted(deltaX = -90f, deltaY = 0f, commitDistancePx = 72f))
        assertFalse(edgeBackGestureCompleted(deltaX = 80f, deltaY = 75f, commitDistancePx = 72f))
    }

    @Test
    fun aShortDeliberateFlingCanCommitButAWeakDragReturns() {
        assertTrue(edgeBackGestureCompleted(
            deltaX = 30f,
            deltaY = 3f,
            commitDistancePx = 72f,
            velocityX = 950f,
            minimumFlingDistancePx = 24f,
            minimumFlingVelocityPx = 900f
        ))
        assertFalse(edgeBackGestureCompleted(
            deltaX = 30f,
            deltaY = 3f,
            commitDistancePx = 72f,
            velocityX = 850f,
            minimumFlingDistancePx = 24f,
            minimumFlingVelocityPx = 900f
        ))
    }

    @Test
    fun visualOffsetFollowsTheFingerWithinTheSurfaceBounds() {
        assertEquals(0f, edgeBackVisualOffset(-10f, 300f))
        assertEquals(120f, edgeBackVisualOffset(120f, 300f))
        assertEquals(300f, edgeBackVisualOffset(420f, 300f))
    }
}
