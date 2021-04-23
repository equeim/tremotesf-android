/*
 * Copyright (C) 2017-2021 Alexey Rochev <equeim@gmail.com>
 *
 * This file is part of Tremotesf.
 *
 * Tremotesf is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Tremotesf is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

@file:Suppress("DEPRECATION")

package org.equeim.tremotesf.data.rpc

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkInfo
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import androidx.annotation.MainThread
import androidx.annotation.RequiresApi
import androidx.core.content.getSystemService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.dropWhile
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.plus
import org.equeim.tremotesf.Application
import org.equeim.tremotesf.ui.AppForegroundTracker
import org.equeim.tremotesf.utils.Logger

object WifiNetworkHelper : Logger {
    private val context = Application.instance
    private val wifiManager by lazy { context.getSystemService<WifiManager>() }
    private val connectivityManager by lazy { context.getSystemService<ConnectivityManager>() }

    private val _observingActiveWifiNetwork = MutableStateFlow(false)
    val observingActiveWifiNetwork: StateFlow<Boolean> by ::_observingActiveWifiNetwork

    private var wifiNetworkObserverScope: CoroutineScope? = null

    val currentWifiSsid: String?
        get() = currentWifiInfo?.knownSsidOrNull

    fun subscribeToForegroundTracker() {
        info("subscribeToForegroundTracker() called")

        val hasServerWithWifiNetwork = Servers.servers.map { servers ->
            val has =
                servers.find { it.autoConnectOnWifiNetworkEnabled && it.autoConnectOnWifiNetworkSSID.isNotBlank() } != null
            if (has) {
                info("There are servers with Wi-Fi networks configured")
            } else {
                info("There are no servers with Wi-Fi networks configured")
            }
            has
        }

        combine(hasServerWithWifiNetwork, AppForegroundTracker.appInForeground, Boolean::and)
            .distinctUntilChanged()
            .dropWhile { !it }
            .onEach { shouldObserveWifiNetworks ->
                _observingActiveWifiNetwork.value = shouldObserveWifiNetworks
                if (shouldObserveWifiNetworks) {
                    startObservingActiveWifiNetwork()
                } else {
                    stopObservingActiveWifiNetwork()
                }
            }
            .launchIn(GlobalScope + Dispatchers.Main)
    }

    @MainThread
    private fun startObservingActiveWifiNetwork() {
        info("startObservingActiveWifiNetwork() called")

        val scope = CoroutineScope(Dispatchers.Main).also { wifiNetworkObserverScope = it }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val connectivityManager = this.connectivityManager
            if (connectivityManager != null) {
                observeActiveWifiNetworkV24(connectivityManager)
                    .onEach { onActiveWifiNetworkChanged() }
                    .launchIn(scope)
            } else {
                error("startObservingActiveWifiNetwork: ConnectivityManager is null")
            }
        } else {
            observeActiveWifiNetworkV16().onEach { onActiveWifiNetworkChanged() }.launchIn(scope)
        }
    }

    @MainThread
    private fun stopObservingActiveWifiNetwork() {
        info("stopObservingActiveWifiNetwork() called")
        wifiNetworkObserverScope?.apply {
            cancel()
            wifiNetworkObserverScope = null
        }
    }

    @RequiresApi(Build.VERSION_CODES.N)
    private fun observeActiveWifiNetworkV24(connectivityManager: ConnectivityManager): Flow<Unit> {
        info("observeActiveWifiNetworkV24() called with: context = $context")
        @Suppress("EXPERIMENTAL_API_USAGE")
        return callbackFlow<Unit> {
            info("observeActiveWifiNetworkV24: registering network callback")
            val callback = DefaultNetworkCallback(channel)
            connectivityManager.registerDefaultNetworkCallback(callback)
            awaitClose {
                info("observeActiveWifiNetworkV24: unregister network callback")
                connectivityManager.unregisterNetworkCallback(callback)
            }
        }.buffer(Channel.CONFLATED)
    }

    // TODO: extract WifiInfo from NetworkCapabilities on Android S
    @RequiresApi(Build.VERSION_CODES.N)
    private class DefaultNetworkCallback(private val channel: SendChannel<Unit>) :
        ConnectivityManager.NetworkCallback(), Logger {
        private var waitingForCapabilities = false

        override fun onAvailable(network: Network) {
            info("onAvailable() called with: network = $network")
            waitingForCapabilities = true
        }

        override fun onLost(network: Network) {
            info("onLost() called with: network = $network")
            waitingForCapabilities = false
        }

        override fun onLosing(network: Network, maxMsToLive: Int) {
            info("onLosing() called with: network = $network, maxMsToLive = $maxMsToLive")
        }

        override fun onUnavailable() {
            info("onUnavailable() called")
        }

        override fun onCapabilitiesChanged(
            network: Network,
            networkCapabilities: NetworkCapabilities
        ) {
            if (!waitingForCapabilities) return

            info("onCapabilitiesChanged: networkCapabilities = $networkCapabilities")

            if (!networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                info("onCapabilitiesChanged: not Wi-Fi network, ignore")
                waitingForCapabilities = false
                return
            }

            if (wifiNetworkCapabilities.all { networkCapabilities.hasCapability(it) }) {
                info("onCapabilitiesChanged: supported Wi-Fi network")
                waitingForCapabilities = false
                channel.offer(Unit)
            } else {
                info("onCapabilitiesChanged: unsupported network, wait")
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.N)
    private val wifiNetworkCapabilities = arrayOf(
        NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED,
        NetworkCapabilities.NET_CAPABILITY_TRUSTED,
        NetworkCapabilities.NET_CAPABILITY_NOT_VPN
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

    private fun observeActiveWifiNetworkV16(): Flow<Unit> {
        info("observeActiveWifiNetworkV16() called with: context = $context")
        @Suppress("EXPERIMENTAL_API_USAGE")
        return callbackFlow<Unit> {
            info("observeActiveWifiNetworkV16: registering receiver")
            val receiver = ConnectivityReceiver(channel)
            context.registerReceiver(
                receiver,
                IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION)
            )
            awaitClose {
                info("observeActiveWifiNetworkV16: unregister receiver")
                context.unregisterReceiver(receiver)
            }
        }.buffer(Channel.CONFLATED)
    }

    private class ConnectivityReceiver(private val channel: SendChannel<Unit>) :
        BroadcastReceiver(), Logger {
        override fun onReceive(context: Context, intent: Intent) {
            val networkInfo =
                intent.getParcelableExtra<NetworkInfo>(ConnectivityManager.EXTRA_NETWORK_INFO)
            info("onReceive: networkInfo = $networkInfo")
            if (networkInfo?.isConnected == true && networkInfo.type == ConnectivityManager.TYPE_WIFI) {
                info("onReceive: Wi-Fi connected")
                channel.offer(Unit)
            }
        }
    }

    @MainThread
    private fun onActiveWifiNetworkChanged() {
        info("onActiveWifiNetworkChanged() called")

        val wifiInfo = currentWifiInfo
        info("onActiveWifiNetworkChanged: current WifiInfo: $currentWifiInfo")

        val ssid = wifiInfo?.knownSsidOrNull
        if (ssid != null) {
            info("onActiveWifiNetworkChanged: SSID = '$ssid'")
            Servers.setCurrentServerFromWifiNetwork(lazyOf(ssid))
        } else {
            error("onActiveWifiNetworkChanged: SSID is null")
        }
    }

    private val currentWifiInfo: WifiInfo?
        get() {
            val wifiManager = this.wifiManager
            return if (wifiManager != null) {
                wifiManager.connectionInfo.also {
                    if (it == null) {
                        error("currentWifiInfo: getConnectionInfo() returned null")
                    }
                }
            } else {
                error("currentWifiInfo: WifiManager is null")
                null
            }
        }

    private val WifiInfo.knownSsidOrNull: String?
        get() = ssid.takeIf { ssid: String? ->
            when {
                ssid == null -> error("knownSsidOrNull: SSID is null")
                ssid.isEmpty() -> error("knownSsidOrNull: SSID is empty")
                ssid == UNKNOWN_SSID -> error("knownSsidOrNull: SSID is unknown")
                else -> return@takeIf true
            }
            false
        }?.removeSsidQuotes()?.takeIf {
            val notBlank = it.isNotBlank()
            if (!notBlank) error("knownSsidOrNull: SSID = '$it' is empty or blank")
            notBlank
        }

    private val UNKNOWN_SSID = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        WifiManager.UNKNOWN_SSID
    } else {
        "<unknown ssid>"
    }

    private const val quoteChar = '"'
    private fun String.removeSsidQuotes(): String? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR1) {
            return this
        }
        return if (startsWith(quoteChar) && endsWith(quoteChar)) {
            drop(1).dropLast(1)
        } else {
            error("removeQuotes: SSID = '$this' is not surrounded by quotation marks, unsupported")
            null
        }
    }
}
