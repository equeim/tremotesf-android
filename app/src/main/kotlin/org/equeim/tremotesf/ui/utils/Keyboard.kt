package org.equeim.tremotesf.ui.utils

import android.app.Activity
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import androidx.core.content.getSystemService
import androidx.core.view.postDelayed
import androidx.fragment.app.Fragment

fun Activity.hideKeyboard() {
    currentFocus?.let { focus ->
        getSystemService<InputMethodManager>()?.hideSoftInputFromWindow(focus.windowToken, 0)
    }
}

fun Fragment.hideKeyboard() {
    activity?.hideKeyboard()
}

fun EditText.showKeyboard() {
    context.getSystemService<InputMethodManager>()?.let { imm ->
        if (requestFocus()) {
            // If you call showSoftInput() right after requestFocus()
            // it may sometimes fail. So add a 50ms delay
            postDelayed(50) {
                // Check if we are still focused
                if (isFocused) {
                    imm.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT)
                }
            }
        }
    }
}
