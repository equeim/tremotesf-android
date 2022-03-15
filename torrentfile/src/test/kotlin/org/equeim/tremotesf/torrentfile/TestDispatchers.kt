package org.equeim.tremotesf.torrentfile

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestCoroutineDispatcher
import kotlinx.coroutines.test.TestDispatcher
import org.equeim.tremotesf.common.TremotesfDispatchers

@OptIn(ExperimentalCoroutinesApi::class)
class TestDispatchers constructor(dispatcher: TestDispatcher) : TremotesfDispatchers {
    override val Default = dispatcher
    override val IO = dispatcher
    override val Main = dispatcher
    override val Unconfined = dispatcher
}
