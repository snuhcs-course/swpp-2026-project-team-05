package com.swpp.team5.frameless.data

import android.content.Context

/** 튜토리얼을 이미 봤는지 기기 단위로 기억한다. 계정과 무관하다. */
internal interface TutorialStore {
    var introSeen: Boolean
    var readerCoachSeen: Boolean
}

internal class SharedPrefsTutorialStore(context: Context) : TutorialStore {
    private val preferences = context.applicationContext
        .getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    override var introSeen: Boolean
        get() = preferences.getBoolean(INTRO_SEEN, false)
        set(value) = preferences.edit().putBoolean(INTRO_SEEN, value).apply()

    override var readerCoachSeen: Boolean
        get() = preferences.getBoolean(READER_COACH_SEEN, false)
        set(value) = preferences.edit().putBoolean(READER_COACH_SEEN, value).apply()

    private companion object {
        const val PREFERENCES = "frameless_tutorial"
        const val INTRO_SEEN = "intro_seen"
        const val READER_COACH_SEEN = "reader_coach_seen"
    }
}
