/*
 * Copyright (C) 2017-2022 Alexey Rochev <equeim@gmail.com>
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

package org.equeim.tremotesf.torrentfile.rpc

import android.content.Context
import androidx.annotation.AnyThread
import androidx.annotation.MainThread
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToStream
import org.equeim.tremotesf.common.DefaultTremotesfDispatchers
import org.equeim.tremotesf.common.TremotesfDispatchers
import timber.log.Timber
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import kotlin.system.measureTimeMillis


private const val FILE_NAME = "servers.json"
private const val TEMP_FILE_PREFIX = "servers"
private const val TEMP_FILE_SUFFIX = ".json"

abstract class Servers(
    protected val scope: CoroutineScope,
    protected val context: Context,
    private val dispatchers: TremotesfDispatchers = DefaultTremotesfDispatchers
) {
    @Serializable
    data class ServersState(
        @SerialName("servers") val servers: List<Server>,
        @SerialName("current") val currentServerName: String?
    ) {
        val currentServer: Server?
            get() = if (currentServerName != null) {
                servers.find { it.name == currentServerName }
            } else {
                null
            }
    }

    private val _serversState = MutableStateFlow(ServersState(emptyList(), null))
    val serversState: StateFlow<ServersState> by ::_serversState
    val servers: Flow<List<Server>> = _serversState.map { it.servers }.distinctUntilChanged()
    val hasServers: Flow<Boolean> = _serversState.map { it.servers.isNotEmpty() }.distinctUntilChanged()
    val currentServer: Flow<Server?> = _serversState.map { it.currentServer }.distinctUntilChanged()

    internal var lastTorrentsProvider: LastTorrentsProvider? = null

    private val json = Json { prettyPrint = true }

    init {
        load()
    }

    @OptIn(ExperimentalSerializationApi::class)
    private fun load() {
        try {
            val (servers, changed) = context.openFileInput(FILE_NAME).buffered().use {
                json.decodeFromStream(ServersState.serializer(), it)
            }.validateLoaded()
            _serversState.value = servers
            if (changed) {
                save()
            }
        } catch (error: FileNotFoundException) {
            Timber.w(error, "Servers file does not exist")
        } catch (error: IOException) {
            Timber.e(error, "Error reading servers file")
        } catch (error: SerializationException) {
            Timber.e(error, "Error parsing servers file")
        }
    }

    private fun ServersState.validateLoaded(): Pair<ServersState, Boolean> {
        var changed = false
        val servers = this.servers.mapNotNull { server ->
            server.validateLoaded().also {
                if (it !== server) {
                    changed = true
                }
            }
        }
        Timber.i("validateLoaded: current server is ${this.currentServerName}")
        val currentServerName =
            if (this.currentServerName != null && servers.find { it.name == this.currentServerName } != null) {
                this.currentServerName
            } else {
                servers.firstOrNull()?.name
            }
        if (currentServerName != this.currentServerName) {
            Timber.e("validateLoaded: current server changed to $currentServerName")
            changed = true
        }
        return ServersState(servers, currentServerName) to changed
    }

    private fun Server.validateLoaded(): Server? {
        Timber.i("validateLoaded: loading server $this")
        if (name.isBlank()) {
            Timber.e("Server's name is empty, skip")
            return null
        }
        var newServer = this
        if (port !in Server.portRange) {
            Timber.e("validateLoaded: server's port is not in range, set default")
            newServer = newServer.copy(port = Server.DEFAULT_PORT)
        }
        if (apiPath.isEmpty()) {
            Timber.e("validateLoaded: server's API path can't be empty, set default")
            newServer = newServer.copy(apiPath = Server.DEFAULT_API_PATH)
        }
        if (updateInterval !in Server.updateIntervalRange) {
            Timber.e("validateLoaded: server's update interval is not in range, set default")
            newServer = newServer.copy(updateInterval = Server.DEFAULT_UPDATE_INTERVAL)
        }
        if (timeout !in Server.timeoutRange) {
            Timber.e("validateLoaded: server's timeout is not in range, set default")
            newServer = newServer.copy(timeout = Server.DEFAULT_TIMEOUT)
        }
        return newServer
    }

    @AnyThread
    private fun save() {
        Timber.i("save() called")
        val servers = _serversState.value
        scope.launch(dispatchers.Main) { save(servers) }
    }

    @MainThread
    protected abstract fun save(serversState: ServersState)

    @OptIn(ExperimentalSerializationApi::class)
    protected fun doSave(serversState: ServersState) {
        val elapsed = measureTimeMillis {
            try {
                val temp = File.createTempFile(TEMP_FILE_PREFIX, TEMP_FILE_SUFFIX)
                temp.outputStream().buffered().use {
                    json.encodeToStream(ServersState.serializer(), serversState, it)
                }
                if (!temp.renameTo(context.getFileStreamPath(FILE_NAME))) {
                    Timber.e("Failed to rename temp file")
                }
            } catch (error: IOException) {
                Timber.e(error, "Failed to save servers file")
            } catch (error: SerializationException) {
                Timber.e(error, "Failed to serialize servers")
            }
        }
        Timber.i("doSave: elapsed time = $elapsed ms")
    }

    fun addOrReplaceServer(newServer: Server, previousName: String? = null) {
        Timber.d("addOrReplaceServer() called with: newServer = $newServer, previousName = $previousName")

        val state = _serversState.value
        val servers = state.servers.toMutableList()
        val removeNames = setOfNotNull(newServer.name, previousName)
        servers.removeAll { removeNames.contains(it.name) }
        servers.add(newServer)

        var currentServerName = state.currentServerName
        if (currentServerName == null ||
            (previousName != null && newServer.name != previousName && previousName == currentServerName)
        ) {
            Timber.d("Setting current server as ${newServer.name}")
            currentServerName = newServer.name
        }
        _serversState.value = ServersState(servers, currentServerName)
        save()
    }

    fun removeServers(serverNames: Set<String>) {
        Timber.d("removeServers() called with: serverNames = $serverNames")
        val state = _serversState.value
        val servers = state.servers.toMutableList()
        servers.removeAll { serverNames.contains(it.name) }
        var currentServerName = state.currentServerName
        if (currentServerName != null && servers.find { it.name == currentServerName } == null) {
            currentServerName = servers.firstOrNull()?.name
        }
        _serversState.value = ServersState(servers, currentServerName)
        save()
    }

    fun setCurrentServer(serverName: String): Boolean {
        Timber.d("setCurrentServer() called with: serverName = $serverName")
        val state = _serversState.value
        return if (serverName != state.currentServerName) {
            _serversState.value = state.copy(currentServerName = serverName)
            save()
            true
        } else {
            Timber.d("setCurrentServer: current server did not change")
            false
        }
    }

    fun saveCurrentServerLastTorrents() {
        Timber.d("saveCurrentServerLastTorrents() called")
        val state = _serversState.value
        val currentServer = state.currentServer
        if (currentServer == null) {
            Timber.d("saveCurrentServerLastTorrents: no current server")
            return
        }
        Timber.d("saveCurrentServerLastTorrents: current server = $currentServer")
        val lastTorrents = lastTorrentsProvider?.lastTorrentsForCurrentServer()
        if (lastTorrents == null) {
            Timber.e("saveCurrentServerLastTorrents: failed to get last torrents")
            return
        }
        Timber.i("saveCurrentServerLastTorrents: last torrents count = ${lastTorrents.torrents.size}")
        if (lastTorrents == currentServer.lastTorrents) {
            Timber.d("saveCurrentServerLastTorrents: last torrents did not change")
            return
        }
        val servers = state.servers.map { server ->
            if (server.name == currentServer.name) {
                server.copy(lastTorrents = lastTorrents)
            } else {
                server
            }
        }
        _serversState.value = state.copy(servers = servers)
        save()
    }

    internal fun interface LastTorrentsProvider {
        fun lastTorrentsForCurrentServer(): Server.LastTorrents?
    }
}
