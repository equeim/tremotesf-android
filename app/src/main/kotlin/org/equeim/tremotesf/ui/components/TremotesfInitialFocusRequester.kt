// SPDX-FileCopyrightText: 2017-2025 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package org.equeim.tremotesf.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester

@Composable
fun rememberTremotesfInitialFocusRequester(): FocusRequester {
    val requester = remember { FocusRequester() }
    var requestedFocus: Boolean by rememberSaveable { mutableStateOf(false) }
    if (!requestedFocus) {
        LaunchedEffect(null) {
            requester.requestFocus()
            requestedFocus = true
        }
    }
    return requester
}
