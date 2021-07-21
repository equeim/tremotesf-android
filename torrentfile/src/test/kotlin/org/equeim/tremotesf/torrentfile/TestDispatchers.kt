package org.equeim.tremotesf.torrentfile

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestCoroutineDispatcher
import org.equeim.tremotesf.common.TremotesfDispatchers
import kotlin.coroutines.CoroutineContext

@ExperimentalCoroutinesApi
class TestDispatchers constructor(dispatcher: TestCoroutineDispatcher) : TremotesfDispatchers {
    override val Default = dispatcher
    override val IO = dispatcher
    override val Main = dispatcher
    override val Unconfined = dispatcher
}
