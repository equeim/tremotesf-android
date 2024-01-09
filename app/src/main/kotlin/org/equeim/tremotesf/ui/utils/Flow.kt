// SPDX-FileCopyrightText: 2017-2024 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package org.equeim.tremotesf.ui.utils

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

fun <T> Flow<T>.launchAndCollectWhenStarted(lifecycleOwner: LifecycleOwner) =
    lifecycleOwner.lifecycleScope.launch { lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) { collect() } }

fun <T> Flow<T>.launchAndCollectWhenStarted(
    lifecycleOwner: LifecycleOwner,
    collector: FlowCollector<T>,
) =
    lifecycleOwner.lifecycleScope.launch { lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) { collect(collector) } }
