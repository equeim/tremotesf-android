// SPDX-FileCopyrightText: 2017-2025 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package org.equeim.tremotesf.ui.utils

import android.app.Activity
import android.view.inputmethod.InputMethodManager
import androidx.core.content.getSystemService

fun Activity.hideKeyboard() {
    currentFocus?.let { focus ->
        getSystemService<InputMethodManager>()?.hideSoftInputFromWindow(focus.windowToken, 0)
    }
}
