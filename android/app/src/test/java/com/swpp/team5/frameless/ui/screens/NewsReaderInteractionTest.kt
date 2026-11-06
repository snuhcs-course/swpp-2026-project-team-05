package com.swpp.team5.frameless.ui.screens

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NewsReaderInteractionTest {
    @Test
    fun detailSheetDismissesOnlyAfterDeliberateDownwardDrag() {
        val threshold = 32f

        assertTrue(shouldDismissDetailSheet(dragDistancePx = 48f, thresholdPx = threshold))
        assertFalse(shouldDismissDetailSheet(dragDistancePx = 20f, thresholdPx = threshold))
        assertFalse(shouldDismissDetailSheet(dragDistancePx = -48f, thresholdPx = threshold))
    }
}
