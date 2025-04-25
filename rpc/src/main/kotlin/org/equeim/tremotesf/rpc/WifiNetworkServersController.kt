// SPDX-FileCopyrightText: 2017-2025 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package org.equeim.tremotesf.rpc

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.content.getSystemService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import timber.log.Timber


class WifiNetworkServersController(
    private val servers: Servers,
    appInForeground: Flow<Boolean>,
    scope: CoroutineScope,
    context: Context,
) {
    private val wifiManager by lazy { context.getSystemService<WifiManager>() }
    private val connectivityManager by lazy { context.getSystemService<ConnectivityManager>() }

    private val _observingActiveWifiNetwork = MutableStateFlow(false)
    val observingActiveWifiNetwork: StateFlow<Boolean> by ::_observingActiveWifiNetwork

    init {
        Timber.i("init")
        val hasServersWithWifiNetwork =
            servers.serversState.map(Servers.ServersState::hasServersWithWifiNetwork).onEach {
                if (it) {
                    Timber.i("There are servers with Wi-Fi networks configured")
                } else {
                    Timber.i("There are no servers with Wi-Fi networks configured")
                }
            }
        scope.launch {
            combine(hasServersWithWifiNetwork, appInForeground, Boolean::and)
                .distinctUntilChanged()
                .collectLatest { shouldObserveWifiNetworks ->
                    _observingActiveWifiNetwork.value = shouldObserveWifiNetworks
                    if (shouldObserveWifiNetworks) {
                        Timber.i("Start observing active Wi-Fi network")
                        observeActiveWifiNetwork().collect(::onCurrentWifiSsidChanged)
                    } else {
                        Timber.i("Don't observe active Wi-Fi network")
                    }
                }
        }
    }

    suspend fun getCurrentWifiSsid(): String? {
        Timber.d("getCurrentWifiSsid() called")
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            observeActiveWifiNetwork().first()
        } else {
            getCurrentWifiInfoFromWifiManager()?.knownSsidOrNull
        }
    }

    private fun observeActiveWifiNetwork(): Flow<String?> {
        Timber.i("observeActiveWifiNetwork() called")
        val connectivityManager = this.connectivityManager
        if (connectivityManager == null) {
            Timber.e("observeActiveWifiNetwork: ConnectivityManager is null")
            return flowOf(null)
        }
        return callbackFlow {
            Timber.i("observeActiveWifiNetwork: registering network callback")
            val request = NetworkRequest.Builder()
                .apply { wifiNetworkCapabilities.forEach(::addCapability) }
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .build()
            val callback = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                NetworkCallback(
                    channel,
                    ConnectivityManager.NetworkCallback.FLAG_INCLUDE_LOCATION_INFO,
                )
            } else {
                NetworkCallback(channel)
            }
            connectivityManager.registerNetworkCallback(request, callback)
            awaitClose {
                Timber.i("observeActiveWifiNetwork: unregister network callback")
                connectivityManager.unregisterNetworkCallback(callback)
            }
        }.buffer(Channel.CONFLATED).distinctUntilChanged()
    }

    private inner class NetworkCallback : ConnectivityManager.NetworkCallback {
        private val wifiSsidChannel: SendChannel<String?>

        constructor(wifiSsidChannel: SendChannel<String?>) : super() {
            this.wifiSsidChannel = wifiSsidChannel
        }

        @RequiresApi(Build.VERSION_CODES.S)
        constructor(
            wifiSsidChannel: SendChannel<String?>,
            flags: Int,
        ) : super(flags) {
            this.wifiSsidChannel = wifiSsidChannel
        }

        override fun onAvailable(network: Network) {
            Timber.i("onAvailable() called with: network = $network")
        }

        override fun onLost(network: Network) {
            Timber.i("onLost() called with: network = $network")
            wifiSsidChannel.trySendLogged(null, "onLost")
        }

        override fun onLosing(network: Network, maxMsToLive: Int) {
            Timber.i("onLosing() called with: network = $network, maxMsToLive = $maxMsToLive")
        }

        override fun onUnavailable() {
            Timber.i("onUnavailable() called")
        }

        override fun onCapabilitiesChanged(
            network: Network,
            networkCapabilities: NetworkCapabilities,
        ) {
            Timber.i("onCapabilitiesChanged: networkCapabilities = $networkCapabilities")
            val ssid = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Timber.i("onCapabilitiesChanged: transportInfo = ${networkCapabilities.transportInfo}")
                networkCapabilities.wifiInfo
            } else {
                getCurrentWifiInfoFromWifiManager()
            }?.knownSsidOrNull
            wifiSsidChannel.trySendLogged(ssid, "onCapabilitiesChanged")
        }
    }

    private fun onCurrentWifiSsidChanged(ssid: String?) {
        Timber.i("onCurrentWifiSsidChanged() called with: ssid = $ssid")
        if (ssid != null) {
            setCurrentServerFromWifiNetwork(ssid)
        }
    }

    suspend fun setCurrentServerFromWifiNetwork() {
        Timber.i("setCurrentServerFromWifiNetwork() called")
        if (servers.serversState.value.hasServersWithWifiNetwork()) {
            val ssid = getCurrentWifiSsid()
            if (ssid != null) {
                setCurrentServerFromWifiNetwork(ssid)
            } else {
                Timber.e("setCurrentServerFromWifiNetwork: SSID is null")
            }
        } else {
            Timber.i("setCurrentServerFromWifiNetwork: there are no servers with Wi-Fi networks configured")
        }
    }

    private fun setCurrentServerFromWifiNetwork(ssid: String) {
        Timber.i("setCurrentServerFromWifiNetwork() called")
        val server = servers.serversState.value.servers.find { it.autoConnectOnWifiNetworkEnabled && it.autoConnectOnWifiNetworkSSID == ssid }
        if (server != null) {
            Timber.i("setCurrentServerFromWifiNetwork: server with name = ${server.name}, address = ${server.address}, port = ${server.port} matches Wi-Fi SSID = '$ssid'")
            servers.setCurrentServer(server.name)
        } else {
            Timber.i("setCurrentServerFromWifiNetwork: no matching servers found")
        }
    }

    @Suppress("DEPRECATION")
    private fun getCurrentWifiInfoFromWifiManager(): WifiInfo? {
        val wifiManager = wifiManager
        return if (wifiManager != null) {
            wifiManager.connectionInfo.also {
                Timber.d("getCurrentWifiInfoFromWifiManager: wifiInfo = $it")
            }
        } else {
            Timber.e("getCurrentWifiInfoFromWifiManager: WifiManager is null")
            null
        }
    }
}

private fun Servers.ServersState.hasServersWithWifiNetwork(): Boolean =
    servers.any { it.autoConnectOnWifiNetworkEnabled && it.autoConnectOnWifiNetworkSSID.isNotBlank() }

private val wifiNetworkCapabilities = arrayOf(
    NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED,
    NetworkCapabilities.NET_CAPABILITY_TRUSTED
).let {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        it + arrayOf(
            NetworkCapabilities.NET_CAPABILITY_FOREGROUND,
            NetworkCapabilities.NET_CAPABILITY_NOT_SUSPENDED
        )
    } else {
        it
    }
}

private val NetworkCapabilities.wifiInfo: WifiInfo?
    @RequiresApi(Build.VERSION_CODES.S)
    get() {
        val transportInfo = transportInfo
        if (transportInfo == null) {
            Timber.e("NetworkCapabilities.wifiInfo: transportInfo is null")
            return null
        }
        return if (transportInfo is WifiInfo) {
            transportInfo
        } else {
            Timber.e("NetworkCapabilities.wifiInfo: transportInfo is not a WifiInfo")
            null
        }
    }

private val WifiInfo.knownSsidOrNull: String?
    get() = ssid.takeIf { ssid: String? ->
        when {
            ssid == null -> Timber.e("knownSsidOrNull: SSID is null")
            ssid.isEmpty() -> Timber.e("knownSsidOrNull: SSID is empty")
            ssid == UNKNOWN_SSID -> Timber.e("knownSsidOrNull: SSID is unknown")
            else -> return@takeIf true
        }
        false
    }?.removeSsidQuotes()?.takeIf {
        val notBlank = it.isNotBlank()
        if (!notBlank) Timber.e("knownSsidOrNull: SSID = '$it' is empty or blank")
        notBlank
    }

private val UNKNOWN_SSID = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
    WifiManager.UNKNOWN_SSID
} else {
    "<unknown ssid>"
}

private fun String.removeSsidQuotes(): String? {
    return if (startsWith(QUOTE_CHAR) && endsWith(
            QUOTE_CHAR
        )
    ) {
        drop(1).dropLast(1)
    } else {
        Timber.e("removeQuotes: SSID = '$this' is not surrounded by quotation marks, unsupported")
        null
    }
}

private const val QUOTE_CHAR = '"'

private fun <T> SendChannel<T>.trySendLogged(element: T, context: String) {
    val result = trySend(element)
    if (!result.isSuccess) {
        Timber.e("$context: failed to send notification to channel, result = $result")
    }
}
