// SPDX-FileCopyrightText: 2017-2024 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package org.equeim.tremotesf.common

import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlin.time.Duration.Companion.ZERO
import kotlin.time.Duration.Companion.seconds

fun MutableSharedFlow<*>.hasSubscribers(): Flow<Boolean> = subscriptionCount.map { it > 0 }.distinctUntilChanged()

@OptIn(FlowPreview::class)
fun MutableSharedFlow<*>.hasSubscribersDebounced(): Flow<Boolean> = hasSubscribers()
    .debounce { hasSubscribers ->
        if (!hasSubscribers) NO_SUBSCRIBERS_DELAY else ZERO
    }
    .distinctUntilChanged()

private val NO_SUBSCRIBERS_DELAY = 1.seconds
