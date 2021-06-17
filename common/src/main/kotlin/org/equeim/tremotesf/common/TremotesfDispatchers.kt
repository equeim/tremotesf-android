package org.equeim.tremotesf.common

import kotlinx.coroutines.Dispatchers
import kotlin.coroutines.CoroutineContext

interface TremotesfDispatchers {
    val Default: CoroutineContext
    val IO: CoroutineContext
    val Main: CoroutineContext
}

object DefaultTremotesfDispatchers : TremotesfDispatchers {
    override val Default = Dispatchers.Default
    override val IO = Dispatchers.IO
    override val Main = Dispatchers.Main
}
