// SPDX-FileCopyrightText: 2017-2025 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package org.equeim.tremotesf.rpc

import androidx.compose.runtime.Immutable
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
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.dropWhile
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.equeim.tremotesf.common.hasSubscribersDebounced
import timber.log.Timber
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

@Immutable
sealed interface RpcRequestState<out T> {
    data object Loading : RpcRequestState<Nothing>

    @JvmInline
    value class Error(val error: RpcRequestError) : RpcRequestState<Nothing>

    @JvmInline
    value class Loaded<T>(val response: T) : RpcRequestState<T>
}

@OptIn(ExperimentalCoroutinesApi::class)
fun <T> RpcClient.performRecoveringRequest(
    performRequest: suspend RpcClient.() -> T,
): Flow<RpcRequestState<T>> =
    startRequestFromScratchEvents()
        .transformLatest {
            if (it.nonRecoverableError != null) {
                emit(it.nonRecoverableError)
            } else {
                emit(RpcRequestState.Loading)
                actuallyPerformRecoveringRequest(performRequest, this)
            }
        }

fun <T> RpcClient.performRecoveringRequestIntoStateFlow(
    scope: CoroutineScope,
    performRequest: suspend RpcClient.() -> T,
): StateFlow<RpcRequestState<T>> = performRecoveringRequest(performRequest).stateInRpcRequest(this, scope)

@OptIn(ExperimentalCoroutinesApi::class)
fun <T> RpcClient.performPeriodicRequest(
    manualRefreshRequests: Flow<*> = emptyFlow<Unit>(),
    performRequest: suspend RpcClient.() -> T,
): Flow<RpcRequestState<T>> {
    return channelFlow {
        var lastEmittedState: RpcRequestState<T>? = null
        merge(
            startRequestFromScratchEvents(),
            manualRefreshRequests.map { ManualRefresh() }
        ).transformLatest {
            (it as? StartRequestFromScratch)?.nonRecoverableError?.let { error ->
                emit(error)
                return@transformLatest
            }
            // If user triggered manual refresh and we are already loaded then we don't want to emit Loading state
            if (!(it is ManualRefresh && lastEmittedState is RpcRequestState.Loaded)) {
                emit(RpcRequestState.Loading)
            }
            while (currentCoroutineContext().isActive) {
                actuallyPerformRecoveringRequest(performRequest, this)
                val updateInterval = getConnectionConfiguration().value?.getOrNull()?.updateInterval
                if (updateInterval != null) {
                    delay(updateInterval)
                } else {
                    break
                }
            }
        }.collect {
            lastEmittedState = it
            send(it)
        }
    }
}

private class ManualRefresh

fun <T> RpcClient.performPeriodicRequestIntoStateFlow(
    scope: CoroutineScope,
    manualRefreshRequests: Flow<*> = emptyFlow<Unit>(),
    performRequest: suspend RpcClient.() -> T,
): StateFlow<RpcRequestState<T>> =
    performPeriodicRequest(manualRefreshRequests, performRequest).stateInRpcRequest(this, scope)

fun <T> Flow<RpcRequestState<T>>.stateInRpcRequest(
    rpcClient: RpcClient,
    scope: CoroutineScope
): StateFlow<RpcRequestState<T>> {
    @Suppress("UnusedFlow")
    val originalFlow = this
    val stateFlow = MutableStateFlow<RpcRequestState<T>>(rpcClient.getInitialRequestState())
    scope.launch {
        stateFlow
            .hasSubscribersDebounced()
            .collectLatest { hasSubscribers ->
                // Don't reset state after we lost subscribers and skip loading state when we are subscribed to again while data was loaded
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

private suspend fun <T> RpcClient.actuallyPerformRecoveringRequest(
    performRequest: suspend RpcClient.() -> T,
    collector: FlowCollector<RpcRequestState<T>>
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
                if (e.isRecoverable) {
                    collector.emit(RpcRequestState.Error(e))
                    retryAttempts += 1
                    val waitFor = (INITIAL_RETRY_INTERVAL * retryAttempts).coerceAtMost(MAX_RETRY_INTERVAL)
                    Timber.tag(RpcClient::class.simpleName!!).e("Retrying RPC request after $waitFor")
                    delay(waitFor)
                    delayedLoadingOnRetry = launch {
                        delay(RETRY_LOADING_STATE_DELAY)
                        collector.emit(RpcRequestState.Loading)
                    }
                } else {
                    // Just wait until requestRestartEvents in performRecoveringRequest/performPeriodicRequest emits new value with nonRecoverableError
                    delay(Duration.INFINITE)
                }
            }
        }
    }
}

private class StartRequestFromScratch(val nonRecoverableError: RpcRequestState.Error? = null)

private fun RpcClient.startRequestFromScratchEvents(): Flow<StartRequestFromScratch> = combine(
    getConnectionConfiguration(),
    shouldConnectToServer
) { configuration, shouldConnectToServer ->
    StartRequestFromScratch(getInitialNonRecoverableError(configuration, shouldConnectToServer))
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

private fun <T> RpcClient.getInitialRequestState(): RpcRequestState<T> = getInitialNonRecoverableError(
    getConnectionConfiguration().value,
    shouldConnectToServer.value
) ?: RpcRequestState.Loading

private val INITIAL_RETRY_INTERVAL = 5.seconds
private val MAX_RETRY_INTERVAL = 1.minutes
private val RETRY_LOADING_STATE_DELAY = 500.milliseconds
