// SPDX-FileCopyrightText: 2017-2023 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package org.equeim.tremotesf.common

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

@Suppress("PropertyName")
interface TremotesfDispatchers {
    val Default: CoroutineDispatcher
    val IO: CoroutineDispatcher
    val Main: CoroutineDispatcher
    val Unconfined: CoroutineDispatcher
}

object DefaultTremotesfDispatchers : TremotesfDispatchers {
    override val Default = Dispatchers.Default
    override val IO = Dispatchers.IO
    override val Main = Dispatchers.Main
    override val Unconfined = Dispatchers.Unconfined
}
