package com.swpp.team5.frameless.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.swpp.team5.frameless.ui.viewmodel.QualifiedReadTracker

@Composable
internal fun ReadingPresence(
    tracker: QualifiedReadTracker,
    articleId: String?,
    identity: String?
) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(tracker, articleId, identity, lifecycle) {
        fun update() = tracker.update(articleId, lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED), identity)
        val observer = LifecycleEventObserver { _, _ -> update() }
        lifecycle.addObserver(observer)
        update()
        onDispose {
            lifecycle.removeObserver(observer)
            tracker.update(null, false, identity)
        }
    }
}
