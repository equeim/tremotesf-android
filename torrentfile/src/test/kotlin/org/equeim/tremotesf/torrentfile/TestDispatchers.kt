// SPDX-FileCopyrightText: 2017-2025 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package org.equeim.tremotesf.torrentfile

import kotlinx.coroutines.test.TestDispatcher
import org.equeim.tremotesf.common.TremotesfDispatchers

class TestDispatchers(dispatcher: TestDispatcher) : TremotesfDispatchers {
    override val Default = dispatcher
    override val IO = dispatcher
    override val Main = dispatcher
    override val Unconfined = dispatcher
}
