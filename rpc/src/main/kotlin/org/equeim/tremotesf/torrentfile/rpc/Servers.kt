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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
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

abstract class Servers(protected val scope: CoroutineScope, protected val context: Context, private val dispatchers: TremotesfDispatchers = DefaultTremotesfDispatchers) {
    private val _servers = MutableStateFlow<List<Server>>(emptyList())
    val servers: StateFlow<List<Server>> by ::_servers

    val hasServers: Boolean
        get() {
            return servers.value.isNotEmpty()
        }

    // currentServer observers should not access servers or hasServers
    private val _currentServer = MutableStateFlow<Server?>(null)
    val currentServer: StateFlow<Server?> by ::_currentServer

    internal var lastTorrentsProvider: LastTorrentsProvider? = null

    init {
        load()
    }

    fun setCurrentServer(server: Server?) {
        _currentServer.value = server
        save()
    }

    private fun load() {
        try {
            val servers = mutableListOf<Server>()

            val fileData =
                context.openFileInput(FILE_NAME).bufferedReader().use(BufferedReader::readText)
            val saveData = Json.decodeFromString(SaveData.serializer(), fileData)
            for (server in saveData.servers) {
                Timber.i("Reading server $server")
                if (server.name.isBlank()) {
                    Timber.e("Server's name is empty, skip")
                    continue
                }
                if (server.port !in Server.portRange) {
                    Timber.e("Server's port is not in range, set default")
                    server.port = Server.DEFAULT_PORT
                }
                if (server.apiPath.isEmpty()) {
                    Timber.e("Server's API path can't be empty, set default")
                    server.apiPath = Server.DEFAULT_API_PATH
                }
                if (server.updateInterval !in Server.updateIntervalRange) {
                    Timber.e("Server's update interval is not in range, set default")
                    server.updateInterval = Server.DEFAULT_UPDATE_INTERVAL
                }
                if (server.timeout !in Server.timeoutRange) {
                    Timber.e("Server's timeout is not in range, set default")
                    server.timeout = Server.DEFAULT_TIMEOUT
                }
                servers.add(server)
            }

            _currentServer.value = servers.find { it.name == saveData.currentServerName }
            _servers.value = servers

            if (currentServer.value == null && servers.isNotEmpty()) {
                _currentServer.value = servers.first()
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
    fun save() {
        Timber.i("save() called")
        val currentServer = currentServer.value
        val servers = servers.value

        if (currentServer != null) {
            lastTorrentsProvider?.lastTorrentsForCurrentServer()?.let {
                currentServer.lastTorrents = it
                Timber.i("save: updated last torrents")
            }
            Timber.i("save: last torrents count = ${currentServer.lastTorrents.torrents.size}")
        }

        val saveData = SaveData(
            currentServer?.name,
            servers.map { it.copy(lastTorrents = it.lastTorrents.copy()) }
        )
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
                        Json { prettyPrint = true }.encodeToString(
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

    fun addServer(newServer: Server) {
        val servers = this.servers.value.toMutableList()

        val overwriteServerIndex = servers.indexOfFirst { it.name == newServer.name }
        if (overwriteServerIndex == -1) {
            servers.add(newServer)
        } else {
            servers[overwriteServerIndex] = newServer
        }

        if (servers.size == 1 || newServer.name == currentServer.value?.name) {
            _currentServer.value = newServer
        }

        _servers.value = servers

        save()
    }

    fun setServer(server: Server, newServer: Server) {
        val currentChanged = when (currentServer.value?.name) {
            // editing current
            server.name,
                // overwriting current with another
            newServer.name -> true
            // nope
            else -> false
        }

        val servers = this.servers.value.toMutableList()

        if (newServer.name != server.name) {
            // remove overwritten
            servers.removeAll { it.name == newServer.name }
        }

        servers[servers.indexOf(server)] = newServer

        if (currentChanged) {
            _currentServer.value = newServer
        }

        _servers.value = servers

        save()
    }

    fun removeServers(toRemove: List<Server>) {
        val servers = this.servers.value.toMutableList()
        servers.removeAll(toRemove)

        if (currentServer.value in toRemove) {
            _currentServer.value = servers.firstOrNull()
        }

        _servers.value = servers

        save()
    }

    internal fun interface LastTorrentsProvider {
        fun lastTorrentsForCurrentServer(): Server.LastTorrents?
    }
}
