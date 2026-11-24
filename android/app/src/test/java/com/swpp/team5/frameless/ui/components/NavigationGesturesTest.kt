package com.swpp.team5.frameless.ui.components

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
}
