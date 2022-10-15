package org.equeim.tremotesf.ui.utils

import android.app.Activity
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import androidx.core.content.getSystemService
import androidx.fragment.app.Fragment
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay
import timber.log.Timber

fun Activity.hideKeyboard() {
    currentFocus?.let { focus ->
        getSystemService<InputMethodManager>()?.hideSoftInputFromWindow(focus.windowToken, 0)
    }
}

fun Fragment.hideKeyboard() {
    activity?.hideKeyboard()
}

private const val IMM_IS_ACTIVE_CHECK_INTERVAL_MS = 50L

fun EditText.showKeyboard() {
    val imm = context.getSystemService<InputMethodManager>() ?: return
    val lifecycleOwner = findViewTreeLifecycleOwner() ?: return
    val job = lifecycleOwner.lifecycleScope.launchWhenStarted {
        if (requestFocus()) {
            while (isFocused && !imm.isActive(this@showKeyboard)) {
                delay(IMM_IS_ACTIVE_CHECK_INTERVAL_MS)
            }
            if (isFocused) {
                imm.showSoftInput(this@showKeyboard, InputMethodManager.SHOW_IMPLICIT)
            }
        } else {
            Timber.w("showKeyboard: failed to request focus")
        }
    }
    val listener = object : View.OnAttachStateChangeListener {
        override fun onViewAttachedToWindow(v: View) = Unit
        override fun onViewDetachedFromWindow(v: View) = job.cancel()
    }
    job.invokeOnCompletion {
        removeOnAttachStateChangeListener(listener)
    }
    addOnAttachStateChangeListener(listener)
}
