package com.swpp.team5.frameless.telemetry

import android.content.Context
import android.os.Bundle
import com.google.firebase.FirebaseApp
import com.google.firebase.analytics.FirebaseAnalytics

/** Sends events to Firebase Analytics (GA4). Numbers stay numbers; codes and flags are sent as text. */
internal class FirebaseTelemetry(private val analytics: FirebaseAnalytics) : Telemetry {
    override fun record(event: String, params: Map<String, Any>) {
        val bundle = Bundle()
        params.forEach { (key, value) ->
            when (value) {
                is Int -> bundle.putLong(key, value.toLong())
                is Long -> bundle.putLong(key, value)
                else -> bundle.putString(key, value.toString())
            }
        }
        analytics.logEvent(event, bundle)
    }

    companion object {
        /** Firebase starts only when the build had a google-services.json; otherwise events are dropped. */
        fun create(context: Context): Telemetry =
            if (FirebaseApp.initializeApp(context) != null) {
                FirebaseTelemetry(FirebaseAnalytics.getInstance(context))
            } else Telemetry.None
    }
}
