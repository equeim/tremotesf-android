// SPDX-FileCopyrightText: 2017-2024 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package org.equeim.tremotesf.rpc

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToStream
import kotlinx.serialization.json.okio.decodeFromBufferedSource
import okio.buffer
import okio.source
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
) {
    @Serializable
    data class ServersState(
        @SerialName("servers") val servers: List<Server>,
        @SerialName("current") val currentServerName: String?,
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

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    init {
        load()
    }

    @OptIn(ExperimentalSerializationApi::class)
    private fun load() {
        try {
            val (servers, changed) = context.openFileInput(FILE_NAME).use {
                json.decodeFromBufferedSource(ServersState.serializer(), it.source().buffer())
            }.validateLoaded()
            _serversState.value = servers
            if (changed) {
                save()
            }
        } catch (error: FileNotFoundException) {
            Timber.d("Servers file does not exist")
        } catch (error: IOException) {
            Timber.e(error, "Error reading servers file")
        } catch (error: SerializationException) {
            Timber.e(error, "Error parsing servers file")
        } catch (error: Exception) {
            Timber.e(error, "Unexpected error when parsing error file")
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

    private fun save() {
        Timber.i("save() called")
        save(_serversState.value)
    }

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
        _serversState.update { state ->
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
            ServersState(servers, currentServerName)
        }
        save()
    }

    fun removeServers(serverNames: Set<String>) {
        Timber.d("removeServers() called with: serverNames = $serverNames")
        _serversState.update { state ->
            val servers = state.servers.toMutableList()
            servers.removeAll { serverNames.contains(it.name) }
            var currentServerName = state.currentServerName
            if (currentServerName != null && servers.find { it.name == currentServerName } == null) {
                currentServerName = servers.firstOrNull()?.name
            }
            ServersState(servers, currentServerName)
        }
        save()
    }

    fun setCurrentServer(serverName: String): Boolean {
        Timber.d("setCurrentServer() called with: serverName = $serverName")
        val oldState = _serversState.getAndUpdate { state ->
            if (serverName != state.currentServerName && state.servers.find { it.name == serverName } != null) {
                state.copy(currentServerName = serverName)
            } else {
                state
            }
        }
        return if (oldState.currentServerName != serverName) {
            save()
            true
        } else {
            Timber.d("setCurrentServer: current server did not change")
            false
        }
    }

    fun saveCurrentServerTorrentsFinishedState(newFinishedState: Map<Server.TorrentHashString, Server.TorrentFinishedState>) {
        Timber.d("Saving finished state for ${newFinishedState.size} torrents")
        _serversState.update { state ->
            val currentServer = state.currentServer
            if (currentServer != null) {
                Timber.d("saveCurrentServerTorrentsFinishedState: current server = $currentServer")
                state.copy(
                    servers = state.servers.map {
                        if (it.name == currentServer.name) {
                            it.copy(lastTorrentsFinishedState = newFinishedState)
                        } else {
                            it
                        }
                    }
                )
            } else {
                Timber.e("saveCurrentServerTorrentsFinishedState: no current server")
                state
            }
        }
        save()
    }
}
