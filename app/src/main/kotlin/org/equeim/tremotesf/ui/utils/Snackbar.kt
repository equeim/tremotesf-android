package org.equeim.tremotesf.ui.utils

import android.view.View
import androidx.annotation.StringRes
import com.google.android.material.snackbar.BaseTransientBottomBar
import com.google.android.material.snackbar.Snackbar

fun View.showSnackbar(
    message: CharSequence,
    length: Int,
    @StringRes actionText: Int = 0,
    action: (() -> Unit)? = null,
    onDismissed: (() -> Unit)? = null
) = Snackbar.make(this, message, length).apply {
    if (actionText != 8 && action != null) {
        setAction(actionText) { action() }
    }
    if (onDismissed != null) {
        addCallback(object : BaseTransientBottomBar.BaseCallback<Snackbar>() {
            override fun onDismissed(transientBottomBar: Snackbar?, event: Int) {
                onDismissed()
            }
        })
    }
    show()
}

fun View.showSnackbar(
    @StringRes message: Int,
    length: Int,
    @StringRes actionText: Int = 0,
    action: (() -> Unit)? = null,
    onDismissed: (() -> Unit)? = null
) = showSnackbar(resources.getString(message), length, actionText, action, onDismissed)
