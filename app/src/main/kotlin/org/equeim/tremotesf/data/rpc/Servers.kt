/*
 * Copyright (C) 2017-2020 Alexey Rochev <equeim@gmail.com>
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

package org.equeim.tremotesf.data.rpc

import android.annotation.SuppressLint
import android.content.Context
import android.os.Parcelable
import androidx.annotation.AnyThread

import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import kotlinx.coroutines.MainScope

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

import org.equeim.tremotesf.Application
import org.equeim.tremotesf.ui.AppForegroundTracker
import org.equeim.tremotesf.ui.AppForegroundTracker.dropUntilInForeground
import org.equeim.tremotesf.utils.Logger

import java.io.BufferedReader
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.util.concurrent.atomic.AtomicReference
import kotlin.system.measureTimeMillis


private const val FILE_NAME = "servers.json"
private const val TEMP_FILE_PREFIX = "servers"
private const val TEMP_FILE_SUFFIX = ".json"

private const val MINIMUM_PORT = 0
private const val MAXIMUM_PORT = 65535
private const val DEFAULT_PORT = 9091
private const val DEFAULT_API_PATH = "/transmission/rpc"
private const val MINIMUM_UPDATE_INTERVAL = 1
private const val MAXIMUM_UPDATE_INTERVAL = 3600
private const val DEFAULT_UPDATE_INTERVAL = 5
private const val MINIMUM_TIMEOUT = 5
private const val MAXIMUM_TIMEOUT = 60
private const val DEFAULT_TIMEOUT = 30

@Serializable
@Parcelize
data class Server(@SerialName("name")
                  var name: String = "",
                  @SerialName("address")
                  var address: String = "",
                  @SerialName("port")
                  var port: Int = DEFAULT_PORT,
                  @SerialName("apiPath")
                  var apiPath: String = DEFAULT_API_PATH,

                  @SerialName("proxyType")
                  var proxyType: String = "",
                  @SerialName("proxyHostname")
                  var proxyHostname: String = "",
                  @SerialName("proxyPort")
                  var proxyPort: Int = 0,
                  @SerialName("proxyUser")
                  var proxyUser: String = "",
                  @SerialName("proxyPassword")
                  var proxyPassword: String = "",

                  @SerialName("httpsEnabled")
                  var httpsEnabled: Boolean = false,
                  @SerialName("selfSignedCertificateEnabled")
                  var selfSignedCertificateEnabled: Boolean = false,
                  @SerialName("selfSignedCertificate")
                  var selfSignedCertificate: String = "",
                  @SerialName("clientCertificateEnabled")
                  var clientCertificateEnabled: Boolean = false,
                  @SerialName("clientCertificate")
                  var clientCertificate: String = "",

                  @SerialName("authentication")
                  var authentication: Boolean = false,
                  @SerialName("username")
                  var username: String = "",
                  @SerialName("password")
                  var password: String = "",

                  @SerialName("updateIntervar")
                  var updateInterval: Int = DEFAULT_UPDATE_INTERVAL,
                  @SerialName("timeout")
                  var timeout: Int = DEFAULT_TIMEOUT,

                  @SerialName("lastTorrents")
                  @Volatile
                  var lastTorrents: LastTorrents = LastTorrents(),
                  @SerialName("addTorrentDialogDirectories")
                  @Volatile
                  var addTorrentDialogDirectories: List<String> = emptyList()) : Parcelable, Logger {
    companion object {
        val portRange get() = MINIMUM_PORT..MAXIMUM_PORT
        val updateIntervalRange get() = MINIMUM_UPDATE_INTERVAL..MAXIMUM_UPDATE_INTERVAL
        val timeoutRange get() = MINIMUM_TIMEOUT..MAXIMUM_TIMEOUT

        fun fromNativeProxyType(type: Int): String {
            return when (type) {
                org.equeim.libtremotesf.Server.ProxyType.Default -> "Default"
                org.equeim.libtremotesf.Server.ProxyType.Http -> "HTTP"
                org.equeim.libtremotesf.Server.ProxyType.Socks5 -> "SOCKS5"
                else -> "Default"
            }
        }
    }

    override fun toString() = "Server(name=$name)"

    fun nativeProxyType(): Int {
        return when (proxyType) {
            "", "Default" -> org.equeim.libtremotesf.Server.ProxyType.Default
            "HTTP" -> org.equeim.libtremotesf.Server.ProxyType.Http
            "SOCKS5" -> org.equeim.libtremotesf.Server.ProxyType.Socks5
            else -> {
                warn("Unknown proxy type $proxyType")
                org.equeim.libtremotesf.Server.ProxyType.Default
            }
        }
    }

    @Serializable
    @Parcelize
    data class Torrent(val id: Int,
                       val hashString: String,
                       val name: String,
                       val finished: Boolean) : Parcelable

    @Serializable
    @Parcelize
    data class LastTorrents(val saved: Boolean = false,
                            val torrents: List<Torrent> = emptyList()) : Parcelable
}

@Serializable
data class SaveData(@SerialName("current") val currentServerName: String?,
                    @SerialName("servers") val servers: List<Server>)

@SuppressLint("StaticFieldLeak")
object Servers : Logger {
    private val context = Application.instance

    private val _servers = MutableStateFlow<List<Server>>(emptyList())
    val servers: StateFlow<List<Server>> by ::_servers

    val hasServers: Boolean
        get() {
            return servers.value.isNotEmpty()
        }

    // currentServer observers should not access servers or hasServers
    private val _currentServer = MutableStateFlow<Server?>(null)
    val currentServer: StateFlow<Server?> by ::_currentServer

    init {
        load()

        AppForegroundTracker.appInForeground
            .dropUntilInForeground()
            .onEach { inForeground ->
                if (!inForeground && Rpc.isConnected.value) save()
            }
            .launchIn(MainScope())
    }

    fun setCurrentServer(server: Server?) {
        _currentServer.value = server
        save()
    }

    private fun load() {
        try {
            val servers = mutableListOf<Server>()

            val fileData = context.openFileInput(FILE_NAME).bufferedReader().use(BufferedReader::readText)
            val saveData = Json.decodeFromString(SaveData.serializer(), fileData)
            for (server in saveData.servers) {
                info("Reading server $server")
                if (server.name.isBlank()) {
                    error("Server's name is empty, skip")
                    continue
                }
                if (server.port !in Server.portRange) {
                    error("Server's port is not in range, set default")
                    server.port = DEFAULT_PORT
                }
                if (server.apiPath.isEmpty()) {
                    error("Server's API path can't be empty, set default")
                    server.apiPath = DEFAULT_API_PATH
                }
                if (server.updateInterval !in Server.updateIntervalRange) {
                    error("Server's update interval is not in range, set default")
                    server.updateInterval = DEFAULT_UPDATE_INTERVAL
                }
                if (server.timeout !in Server.timeoutRange) {
                    error("Server's timeout is not in range, set default")
                    server.timeout = DEFAULT_TIMEOUT
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
            info("Error opening servers file", error)
        } catch (error: IOException) {
            error("Error reading servers file", error)
        } catch (error: SerializationException) {
            error("Error deserializing servers file", error)
        }
    }

    @AnyThread
    fun save() {
        info("save() called")
        val currentServer = currentServer.value
        val servers = servers.value

        if (Rpc.isConnected.value) {
            info("save: updating last torrents")
            currentServer?.lastTorrents = Server.LastTorrents(true, Rpc.torrents.value.map {
                Server.Torrent(it.id,
                it.hashString,
                it.name,
                it.isFinished)
            })
            info("save: last torrents count = ${currentServer?.lastTorrents?.torrents?.size}")
        } else {
            info("save: disconnected, not updating last torrents")
        }

        SaveWorker.saveData.set(SaveData(
            currentServer?.name,
            servers.map { it.copy(lastTorrents = it.lastTorrents.copy()) }
        ))

        WorkManager.getInstance(context).enqueueUniqueWork(
                SaveWorker.UNIQUE_WORK_NAME,
                ExistingWorkPolicy.APPEND,
                OneTimeWorkRequest.from(SaveWorker::class.java)
        )
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
}

class SaveWorker(context: Context, workerParameters: WorkerParameters) : Worker(context, workerParameters), Logger {
    companion object {
        const val UNIQUE_WORK_NAME = "ServersSaveWorker"
        val saveData = AtomicReference<SaveData>()
    }

    override fun doWork(): Result {
        val data = saveData.getAndSet(null)
        info("SaveWorker.doWork(), saveData=$data")
        val elapsed = measureTimeMillis {
            if (data != null) {
                try {
                    val temp = File.createTempFile(TEMP_FILE_PREFIX, TEMP_FILE_SUFFIX)
                    temp.bufferedWriter().use {
                        it.write(Json { prettyPrint = true }.encodeToString(SaveData.serializer(), data))
                    }
                    if (!temp.renameTo(Application.instance.getFileStreamPath(FILE_NAME))) {
                        error("Failed to rename temp file")
                    }
                } catch (error: IOException) {
                    error("Failed to save servers file", error)
                } catch (error: SerializationException) {
                    error("Failed to serialize servers", error)
                }
            }
        }
        info("SaveWorker.doWork() return, elapsed time: $elapsed ms")
        return Result.success()
    }

    override fun onStopped() {
        saveData.set(null)
    }
}