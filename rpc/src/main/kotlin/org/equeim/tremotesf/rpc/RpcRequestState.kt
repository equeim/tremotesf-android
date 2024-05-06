// SPDX-FileCopyrightText: 2017-2024 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package org.equeim.tremotesf.rpc

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.dropWhile
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.equeim.tremotesf.common.hasSubscribersDebounced
import timber.log.Timber
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

sealed interface RpcRequestState<in T> {
    data object Loading : RpcRequestState<Any?>

    @JvmInline
    value class Error(val error: RpcRequestError) : RpcRequestState<Any?>

    @JvmInline
    value class Loaded<T>(val response: T) : RpcRequestState<T>
}

@OptIn(ExperimentalCoroutinesApi::class)
fun <T> RpcClient.performRecoveringRequest(
    interruptingRefreshRequests: Flow<*> = emptyFlow<Unit>(),
    nonInterruptingRefreshRequests: Flow<*> = emptyFlow<Unit>(),
    performRequest: suspend RpcClient.() -> T,
): Flow<RpcRequestState<T>> =
    combine(
        getConnectionConfiguration(),
        shouldConnectToServer,
        ::getInitialNonRecoverableError
    )
        .transformLatest { initialError ->
            if (initialError != null) {
                emit(initialError)
            } else {
                emit(RpcRequestState.Loading)
                interruptingRefreshRequests.onStart { emit(Unit) }.collectLatest {
                    nonInterruptingRefreshRequests.onStart { emit(Unit) }.collect {
                        actuallyPerformRecoveringRequest(this, performRequest)
                    }
                }
            }
        }

private suspend fun <T> RpcClient.actuallyPerformRecoveringRequest(
    collector: FlowCollector<RpcRequestState<T>>,
    performRequest: suspend RpcClient.() -> T,
) {
    coroutineScope {
        var delayedLoadingOnRetry: Job? = null
        var retryAttempts = 0
        while (currentCoroutineContext().isActive) {
            try {
                val response = performRequest()
                delayedLoadingOnRetry?.cancel()
                collector.emit(RpcRequestState.Loaded(response))
                break
            } catch (e: RpcRequestError) {
                delayedLoadingOnRetry?.cancel()
                when (e) {
                    is RpcRequestError.NoConnectionConfiguration, is RpcRequestError.BadConnectionConfiguration, is RpcRequestError.ConnectionDisabled -> break
                    else -> Unit
                }
                collector.emit(RpcRequestState.Error(e))
                retryAttempts += 1
                val waitFor = (INITIAL_RETRY_INTERVAL * retryAttempts).coerceAtMost(MAX_RETRY_INTERVAL)
                Timber.tag(RpcClient::class.simpleName!!).e("Retrying RPC request after $waitFor")
                delay(waitFor)
                delayedLoadingOnRetry = launch {
                    delay(RETRY_LOADING_STATE_DELAY)
                    collector.emit(RpcRequestState.Loading)
                }
            }
        }
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
fun <T> RpcClient.performPeriodicRequest(
    manualRefreshRequests: Flow<*> = emptyFlow<Unit>(),
    performRequest: suspend RpcClient.() -> T,
): Flow<RpcRequestState<T>> {
    val periodicRefreshRequests = getConnectionConfiguration().transformLatest {
        it?.getOrNull()?.updateInterval?.let { interval ->
            while (currentCoroutineContext().isActive) {
                delay(interval)
                emit(Unit)
            }
        }
    }
    return performRecoveringRequest(
        interruptingRefreshRequests = manualRefreshRequests,
        nonInterruptingRefreshRequests = periodicRefreshRequests,
        performRequest = performRequest
    )
}

private fun getInitialNonRecoverableError(
    configuration: Result<ConnectionConfiguration>?,
    shouldConnectToServer: Boolean,
): RpcRequestState.Error? =
    when {
        configuration == null -> RpcRequestState.Error(RpcRequestError.NoConnectionConfiguration())
        configuration.isFailure -> RpcRequestState.Error(RpcRequestError.BadConnectionConfiguration(configuration.exceptionOrNull() as Exception))
        !shouldConnectToServer -> RpcRequestState.Error(RpcRequestError.ConnectionDisabled())
        else -> null
    }

fun <T> Flow<RpcRequestState<T>>.stateIn(
    rpcClient: RpcClient,
    coroutineScope: CoroutineScope,
    coroutineContext: CoroutineContext = EmptyCoroutineContext
): StateFlow<RpcRequestState<T>> {
    val originalFlow = this
    val stateFlow = MutableStateFlow<RpcRequestState<T>>(
        getInitialNonRecoverableError(
            rpcClient.getConnectionConfiguration().value,
            rpcClient.shouldConnectToServer.value
        ) ?: RpcRequestState.Loading
    )
    coroutineScope.launch(coroutineContext) {
        stateFlow
            .hasSubscribersDebounced()
            .collectLatest { hasSubscribers ->
                if (hasSubscribers) {
                    if (stateFlow.value is RpcRequestState.Loaded) {
                        originalFlow.dropWhile { it is RpcRequestState.Loading }
                    } else {
                        originalFlow
                    }.collect(stateFlow)
                }
            }
    }
    return stateFlow
}

private val INITIAL_RETRY_INTERVAL = 5.seconds
private val MAX_RETRY_INTERVAL = 1.minutes
private val RETRY_LOADING_STATE_DELAY = 500.milliseconds
