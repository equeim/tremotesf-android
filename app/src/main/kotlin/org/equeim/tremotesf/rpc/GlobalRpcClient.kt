// SPDX-FileCopyrightText: 2017-2026 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package org.equeim.tremotesf.rpc

import android.annotation.SuppressLint
import android.content.res.Resources
import android.os.Parcelable
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalResources
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.parcelize.Parcelize
import org.equeim.tremotesf.R
import org.equeim.tremotesf.rpc.requests.TorrentAlreadyExists
import org.equeim.tremotesf.rpc.requests.TorrentNotFound
import org.equeim.tremotesf.ui.AppForegroundTracker
import java.util.concurrent.atomic.AtomicBoolean

@SuppressLint("StaticFieldLeak")
object GlobalRpcClient : RpcClient(CoroutineScope(SupervisorJob() + Dispatchers.Default)) {
    @Parcelize
    data class BackgroundRpcRequestError(val error: RpcRequestError, @param:StringRes val errorContext: Int) : Parcelable

    val backgroundRpcRequestsErrors: Channel<BackgroundRpcRequestError> = Channel(Channel.UNLIMITED)

    private val connectAutomaticallyWhenInForeground = AtomicBoolean(true)

    init {
        // Ensure that connection configuration is set immediately by using CoroutineStart.UNDISPATCHED
        coroutineScope.launch(start = CoroutineStart.UNDISPATCHED) {
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

fun RpcRequestError.getErrorString(resources: Resources): String = when (this) {
    is RpcRequestError.NoConnectionConfiguration -> resources.getString(R.string.no_servers)
    is RpcRequestError.BadConnectionConfiguration -> resources.getString(R.string.invalid_connection_configuration)
    is RpcRequestError.ConnectionDisabled -> resources.getString(R.string.disconnected)
    is RpcRequestError.AuthenticationError -> resources.getString(R.string.authentication_error)
    is RpcRequestError.DeserializationError -> resources.getString(R.string.parsing_error)
    is RpcRequestError.NetworkError -> resources.getString(R.string.connection_error_with_cause, cause)
    is RpcRequestError.UnsuccessfulHttpStatusCode -> resources.getString(R.string.connection_error_with_cause, message)
    is RpcRequestError.UnexpectedError -> resources.getString(R.string.connection_error)
    is RpcRequestError.Timeout -> resources.getString(R.string.timed_out)
    is RpcRequestError.UnsuccessfulResultField -> resources.getString(R.string.server_returned_error_result, result)
    is RpcRequestError.UnsupportedServerVersion -> resources.getString(R.string.unsupported_server_version, version)
    is RpcRequestError.RequestSpecificError -> when (this) {
        is TorrentAlreadyExists -> resources.getString(R.string.torrent_duplicate_not_merging_trackers, torrentName)
        is TorrentNotFound -> resources.getString(R.string.torrent_not_found)
        else -> resources.getString(R.string.connection_error)
    }
}

@Composable
fun RpcRequestError.getErrorString(): String {
    return getErrorString(LocalResources.current)
}
