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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.equeim.tremotesf.common.DefaultTremotesfDispatchers
import org.equeim.tremotesf.common.TremotesfDispatchers
import timber.log.Timber
import java.io.BufferedReader
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
    private val _servers = MutableStateFlow<List<Server>>(emptyList())
    val servers: StateFlow<List<Server>> by ::_servers

    val hasServers: StateFlow<Boolean> = servers
        .map { it.isNotEmpty() }
        .distinctUntilChanged()
        .stateIn(scope + Dispatchers.Unconfined, SharingStarted.Eagerly, false)

    // currentServer observers should not access servers or hasServers
    private val _currentServerName = MutableStateFlow<String?>(null)
    val currentServer: StateFlow<Server?> = combine(_servers, _currentServerName) { servers, name ->
        servers.find { it.name == name }
    }.stateIn(scope + Dispatchers.Unconfined, SharingStarted.Eagerly, null)

    internal var lastTorrentsProvider: LastTorrentsProvider? = null

    private val json = Json { prettyPrint = true }

    init {
        load()
    }

    private fun load() {
        try {
            val servers = mutableListOf<Server>()

            val fileData =
                context.openFileInput(FILE_NAME).bufferedReader().use(BufferedReader::readText)
            val saveData = Json.decodeFromString(SaveData.serializer(), fileData)
            for (readServer in saveData.servers) {
                Timber.i("Reading server $readServer")
                var server = readServer
                if (server.name.isBlank()) {
                    Timber.e("Server's name is empty, skip")
                    continue
                }
                if (server.port !in Server.portRange) {
                    Timber.e("Server's port is not in range, set default")
                    server = server.copy(port = Server.DEFAULT_PORT)
                }
                if (server.apiPath.isEmpty()) {
                    Timber.e("Server's API path can't be empty, set default")
                    server = server.copy(apiPath = Server.DEFAULT_API_PATH)
                }
                if (server.updateInterval !in Server.updateIntervalRange) {
                    Timber.e("Server's update interval is not in range, set default")
                    server = server.copy(updateInterval = Server.DEFAULT_UPDATE_INTERVAL)
                }
                if (server.timeout !in Server.timeoutRange) {
                    Timber.e("Server's timeout is not in range, set default")
                    server = server.copy(timeout = Server.DEFAULT_TIMEOUT)
                }
                servers.add(server)
            }

            _servers.value = servers
            var shouldSave = false
            _currentServerName.value =
                if (servers.find { it.name == saveData.currentServerName } != null) {
                    saveData.currentServerName
                } else {
                    servers.firstOrNull()?.name.also {
                        if (it != null) shouldSave = true
                    }
                }
            if (shouldSave) {
                save()
            }
        } catch (error: FileNotFoundException) {
            Timber.e(error, "Error opening servers file")
        } catch (error: IOException) {
            Timber.e(error, "Error reading servers file")
        } catch (error: SerializationException) {
            Timber.e(error, "Error deserializing servers file")
        }
    }

    @AnyThread
    private fun save() {
        Timber.i("save() called")
        val saveData = SaveData(_currentServerName.value, _servers.value)
        scope.launch(dispatchers.Main) { save(saveData) }
    }

    @Serializable
    protected data class SaveData(
        @SerialName("current") val currentServerName: String?,
        @SerialName("servers") val servers: List<Server>
    )

    @MainThread
    protected abstract fun save(saveData: SaveData)

    protected fun doSave(data: SaveData) {
        val elapsed = measureTimeMillis {
            try {
                val temp = File.createTempFile(TEMP_FILE_PREFIX, TEMP_FILE_SUFFIX)
                temp.bufferedWriter().use {
                    it.write(
                        json.encodeToString(
                            SaveData.serializer(),
                            data
                        )
                    )
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

        val servers = _servers.value.toMutableList()
        val removeNames = setOfNotNull(newServer.name, previousName)
        servers.removeAll { removeNames.contains(it.name) }
        servers.add(newServer)

        var current = _currentServerName.value
        if (current == null ||
            (previousName != null && newServer.name != previousName && previousName == current)
        ) {
            Timber.d("Setting current server as ${newServer.name}")
            current = newServer.name
        }

        _servers.value = servers
        _currentServerName.value = current

        save()
    }

    fun removeServers(serverNames: Set<String>) {
        Timber.d("removeServers() called with: serverNames = $serverNames")
        val servers = _servers.value.toMutableList()
        servers.removeAll { serverNames.contains(it.name) }
        var current = _currentServerName.value
        if (current != null && servers.find { it.name == current } == null) {
            current = servers.firstOrNull()?.name
        }
        _servers.value = servers
        _currentServerName.value = current
        save()
    }

    fun setCurrentServer(serverName: String) {
        Timber.d("setCurrentServer() called with: serverName = $serverName")
        _currentServerName.value = serverName
        save()
    }

    fun saveCurrentServerLastTorrents() {
        Timber.d("saveCurrentServerLastTorrents() called")
        val current = currentServer.value
        if (current == null) {
            Timber.d("saveCurrentServerLastTorrents: no current server")
            return
        }
        Timber.d("saveCurrentServerLastTorrents: current server = $current")
        val lastTorrents = lastTorrentsProvider?.lastTorrentsForCurrentServer()
        if (lastTorrents == null) {
            Timber.e("saveCurrentServerLastTorrents: failed to get last torrents")
            return
        }
        Timber.i("saveCurrentServerLastTorrents: last torrents count = ${lastTorrents.torrents.size}")
        if (lastTorrents == current.lastTorrents) {
            Timber.d("saveCurrentServerLastTorrents: last torrents did not change")
            return
        }
        _servers.value = servers.value.map { server ->
            if (server.name == current.name) {
                server.copy(lastTorrents = lastTorrents)
            } else {
                server
            }
        }
        save()
    }

    internal fun interface LastTorrentsProvider {
        fun lastTorrentsForCurrentServer(): Server.LastTorrents?
    }
}
