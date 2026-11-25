package com.swpp.team5.frameless.telemetry

/**
 * Product telemetry (specs/features/telemetry.md).
 *
 * Every event has a fixed name, and every parameter is a fixed code, a count, a duration, or a flag. No account,
 * e-mail, search text, pasted link, article title or body, or article id is ever recorded.
 */
internal fun interface Telemetry {
    fun record(event: String, params: Map<String, Any>)

    companion object {
        /** Used when Firebase is not configured (no app/google-services.json): every event is dropped. */
        val None = Telemetry { _, _ -> }
    }
}

/** Event and parameter names, kept in one place so the analytics dashboard and the code agree. */
internal object TelemetryEvent {
    const val SCREEN_VIEW = "screen_view"            // screen_name
    const val CATEGORY_SELECT = "category_select"    // category
    const val SEARCH_SUBMIT = "search_submit"        // filter
    const val ARTICLE_OPEN = "article_open"          // source
    const val ANALYSIS_VIEW = "analysis_view"        // findings, with_comparison, economic, offline
    const val COMPARISON_OPEN = "comparison_open"    // pages
    const val SAVE_TOGGLE = "save_toggle"            // saved
    const val ANALYSIS_TOGGLE = "analysis_toggle"    // enabled
    const val LINK_ANALYSIS_RESULT = "link_analysis_result" // result, code, seconds
    const val TUTORIAL_REPLAY = "tutorial_replay"
    const val TUTORIAL_INTRO_DONE = "tutorial_intro_done"
    const val API_ERROR = "api_error"                // where, code
}
