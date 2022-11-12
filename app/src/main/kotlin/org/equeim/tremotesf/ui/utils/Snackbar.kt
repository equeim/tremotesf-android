package org.equeim.tremotesf.ui.utils

import androidx.annotation.StringRes
import androidx.coordinatorlayout.widget.CoordinatorLayout
import com.google.android.material.snackbar.BaseTransientBottomBar
import com.google.android.material.snackbar.Snackbar

fun CoordinatorLayout.showSnackbar(
    message: CharSequence,
    length: Int,
    @StringRes actionText: Int = 0,
    action: (() -> Unit)? = null,
    onDismissed: ((Snackbar) -> Unit)? = null
) = Snackbar.make(this, message, length).apply {
    if (actionText != 0 && action != null) {
        setAction(actionText) { action() }
    }
    if (onDismissed != null) {
        addCallback(object : BaseTransientBottomBar.BaseCallback<Snackbar>() {
            override fun onDismissed(transientBottomBar: Snackbar, event: Int) {
                onDismissed(transientBottomBar)
            }
        })
    }
    show()
}

fun CoordinatorLayout.showSnackbar(
    @StringRes message: Int,
    length: Int,
    @StringRes actionText: Int = 0,
    action: (() -> Unit)? = null,
    onDismissed: ((Snackbar) -> Unit)? = null
) = showSnackbar(resources.getString(message), length, actionText, action, onDismissed)
