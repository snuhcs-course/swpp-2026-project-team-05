package com.swpp.team5.frameless.data

import android.content.Context

/** App-wide appearance preference. SYSTEM remains the default so the OS setting is respected. */
internal enum class AppearanceMode {
    SYSTEM,
    LIGHT,
    DARK;

    fun usesDarkTheme(systemDark: Boolean): Boolean = when (this) {
        SYSTEM -> systemDark
        LIGHT -> false
        DARK -> true
    }
}

internal interface AppearanceStore {
    var mode: AppearanceMode
}

/** Device-local preference: appearance does not require an account or server round trip. */
internal class SharedPrefsAppearanceStore(context: Context) : AppearanceStore {
    private val preferences = context.applicationContext
        .getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    override var mode: AppearanceMode
        get() = preferences.getString(MODE, null)
            ?.let { stored -> AppearanceMode.entries.firstOrNull { it.name == stored } }
            ?: AppearanceMode.SYSTEM
        set(value) = preferences.edit().putString(MODE, value.name).apply()

    private companion object {
        const val PREFERENCES = "frameless_appearance"
        const val MODE = "mode"
    }
}
