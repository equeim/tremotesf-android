// SPDX-FileCopyrightText: 2017-2024 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package org.equeim.tremotesf.ui.utils

import androidx.annotation.IdRes
import androidx.annotation.StringRes
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.view.marginBottom
import androidx.lifecycle.LifecycleOwner
import com.google.android.material.snackbar.BaseTransientBottomBar
import com.google.android.material.snackbar.BaseTransientBottomBar.BaseCallback.DismissEvent
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.suspendCancellableCoroutine
import org.equeim.tremotesf.ui.NavigationActivity
import org.equeim.tremotesf.ui.applyNavigationBarBottomInset
import kotlin.coroutines.resume

suspend fun CoordinatorLayout.showSnackbar(
    message: CharSequence,
    duration: Int,
    lifecycleOwner: LifecycleOwner,
    activity: NavigationActivity,
    @IdRes anchorViewId: Int = 0,
    @StringRes actionText: Int = 0,
    action: (() -> Unit)? = null,
): SnackbarDismissResult = suspendCancellableCoroutine { continuation ->
    Snackbar.make(this, message, duration).apply {
        if (actionText != 0 && action != null) {
            setAction(actionText) { action() }
        }
        val insetsJob = if (anchorViewId != 0) {
            setAnchorView(anchorViewId)
            null
        } else {
            applyNavigationBarBottomInset(
                paddingViews = emptyMap(),
                marginViews = mapOf(view to view.marginBottom),
                lifecycleOwner = lifecycleOwner,
                activity = activity
            )
        }
        addCallback(object : BaseTransientBottomBar.BaseCallback<Snackbar>() {
            override fun onDismissed(transientBottomBar: Snackbar, @DismissEvent event: Int) {
                insetsJob?.cancel()
                if (continuation.isActive) {
                    continuation.resume(SnackbarDismissResult(event))
                }
            }
        })
        show()
        continuation.invokeOnCancellation { dismiss() }
    }
}

suspend fun CoordinatorLayout.showSnackbar(
    @StringRes message: Int,
    duration: Int,
    lifecycleOwner: LifecycleOwner,
    activity: NavigationActivity,
    @IdRes anchorViewId: Int = 0,
    @StringRes actionText: Int = 0,
    action: (() -> Unit)? = null,
): SnackbarDismissResult =
    showSnackbar(resources.getString(message), duration, lifecycleOwner, activity, anchorViewId, actionText, action)

data class SnackbarDismissResult(@DismissEvent val event: Int)
