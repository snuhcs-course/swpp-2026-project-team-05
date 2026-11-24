package com.swpp.team5.frameless.ui.viewmodel

import com.swpp.team5.frameless.data.AppearanceMode
import com.swpp.team5.frameless.data.AppearanceStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppearanceViewModelTest {
    @Test
    fun systemModeTracksDeviceWhileExplicitModesOverrideIt() {
        assertTrue(AppearanceMode.SYSTEM.usesDarkTheme(systemDark = true))
        assertFalse(AppearanceMode.SYSTEM.usesDarkTheme(systemDark = false))
        assertFalse(AppearanceMode.LIGHT.usesDarkTheme(systemDark = true))
        assertTrue(AppearanceMode.DARK.usesDarkTheme(systemDark = false))
    }

    @Test
    fun selectionIsPersistedAndReflectedImmediately() {
        val store = FakeAppearanceStore(AppearanceMode.SYSTEM)
        val viewModel = AppearanceViewModel(store)

        viewModel.select(AppearanceMode.DARK)

        assertEquals(AppearanceMode.DARK, viewModel.mode)
        assertEquals(AppearanceMode.DARK, store.mode)
    }

    private class FakeAppearanceStore(override var mode: AppearanceMode) : AppearanceStore
}
