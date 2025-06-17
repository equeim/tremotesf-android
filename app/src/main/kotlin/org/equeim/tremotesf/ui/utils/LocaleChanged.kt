// SPDX-FileCopyrightText: 2017-2025 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package org.equeim.tremotesf.ui.utils

import android.content.ComponentCallbacks
import android.content.Context
import android.content.res.Configuration
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate

fun Context.localeChangedEvents(): Flow<Unit> = callbackFlow {
    var previousLocales = resources.configuration.locales
    val callback = object : ComponentCallbacks {
        override fun onConfigurationChanged(newConfig: Configuration) {
            if (newConfig.locales != previousLocales) {
                previousLocales = newConfig.locales
                trySend(Unit)
            }
        }
        override fun onLowMemory() = Unit
    }
    registerComponentCallbacks(callback)
    awaitClose { unregisterComponentCallbacks(callback) }
}.conflate()
