package com.swpp.team5.frameless.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Test

class HomeBriefingTest {
    @Test
    fun marketDirectionFollowsNumericChangeIncludingZero() {
        assertEquals(MarketDirection.UP, marketDirection(0.32))
        assertEquals(MarketDirection.DOWN, marketDirection(-0.60))
        assertEquals(MarketDirection.FLAT, marketDirection(0.0))
        assertEquals(MarketDirection.FLAT, marketDirection(-0.0))
    }

    @Test
    fun changeLabelPreservesSignAndNormalizesNegativeZero() {
        assertEquals("+0.32%", marketChangeLabel(0.32))
        assertEquals("-0.60%", marketChangeLabel(-0.6))
        assertEquals("0.00%", marketChangeLabel(-0.0))
    }
}
