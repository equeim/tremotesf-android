// SPDX-FileCopyrightText: 2017-2023 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package org.equeim.tremotesf.rpc

import android.annotation.SuppressLint
import android.content.Context
import androidx.annotation.StringRes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import org.equeim.tremotesf.R
import org.equeim.tremotesf.TremotesfApplication
import org.equeim.tremotesf.rpc.requests.DUPLICATE_TORRENT_RESULT
import org.equeim.tremotesf.ui.AppForegroundTracker
import java.util.concurrent.atomic.AtomicBoolean

@SuppressLint("StaticFieldLeak")
object GlobalRpcClient : RpcClient(CoroutineScope(SupervisorJob() + Dispatchers.Default), TremotesfApplication.instance) {
    data class BackgroundRpcRequestError(val error: RpcRequestError, @StringRes val errorContext: Int)

    val backgroundRpcRequestsErrors: Channel<BackgroundRpcRequestError> = Channel(Channel.UNLIMITED)

    private val connectAutomaticallyWhenInForeground = AtomicBoolean(true)

    init {
        coroutineScope.launch {
            GlobalServers.currentServer
                .distinctUntilChanged { old, new ->
                    if (old != null && new != null) {
                        !old.shouldUpdateConnectionConfiguration(new)
                    } else {
                        old == null && new == null
                    }
                }
                .collect(::setConnectionConfiguration)
        }

        coroutineScope.launch {
            AppForegroundTracker.appInForeground.collect { inForeground ->
                if (inForeground && connectAutomaticallyWhenInForeground.getAndSet(false)) {
                    shouldConnectToServer.value = true
                }
            }
        }
    }

    fun disconnectOnShutdown() {
        shouldConnectToServer.value = false
        connectAutomaticallyWhenInForeground.set(true)
    }

    fun performBackgroundRpcRequest(@StringRes errorContext: Int, block: suspend RpcClient.() -> Unit) {
        @Suppress("DeferredResultUnused")
        performBackgroundRpcRequestAsync(errorContext, block)
    }

    suspend fun awaitBackgroundRpcRequest(@StringRes errorContext: Int, block: suspend RpcClient.() -> Unit): Boolean =
        performBackgroundRpcRequestAsync(errorContext, block).await()

    private fun performBackgroundRpcRequestAsync(
        @StringRes errorContext: Int,
        block: suspend RpcClient.() -> Unit,
    ): Deferred<Boolean> = coroutineScope.async {
        try {
            GlobalRpcClient.block()
            true
        } catch (e: RpcRequestError) {
            backgroundRpcRequestsErrors.send(BackgroundRpcRequestError(e, errorContext))
            false
        }
    }
}

fun RpcRequestError.getErrorString(context: Context): String = when (this) {
    is RpcRequestError.NoConnectionConfiguration -> context.getString(R.string.no_servers)
    is RpcRequestError.BadConnectionConfiguration -> context.getString(R.string.invalid_connection_configuration)
    is RpcRequestError.ConnectionDisabled -> context.getString(R.string.disconnected)
    is RpcRequestError.AuthenticationError -> context.getString(R.string.authentication_error)
    is RpcRequestError.DeserializationError -> context.getString(R.string.parsing_error)
    is RpcRequestError.NetworkError -> context.getString(R.string.connection_error_with_cause, cause)
    is RpcRequestError.UnsuccessfulHttpStatusCode -> context.getString(R.string.connection_error_with_cause, message)
    is RpcRequestError.UnexpectedError -> context.getString(R.string.connection_error)
    is RpcRequestError.Timeout -> context.getString(R.string.timed_out)
    is RpcRequestError.UnsuccessfulResultField -> if (result == DUPLICATE_TORRENT_RESULT) {
        context.getString(R.string.torrent_duplicate)
    } else {
        context.getString(R.string.server_returned_error_result, result)
    }

    is RpcRequestError.UnsupportedServerVersion -> context.getString(R.string.unsupported_server_version, version)
}
