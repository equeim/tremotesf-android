/*
 * Copyright (C) 2017-2019 Alexey Rochev <equeim@gmail.com>
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

package org.equeim.tremotesf

import java.io.BufferedReader
import java.io.FileNotFoundException
import java.io.IOException
import kotlin.system.measureTimeMillis

import android.annotation.SuppressLint
import android.content.Context

import androidx.lifecycle.MutableLiveData
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters

import org.jetbrains.anko.AnkoLogger
import org.jetbrains.anko.error
import org.jetbrains.anko.info

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonConfiguration
import kotlinx.serialization.json.JsonException


private const val FILE_NAME = "servers.json"
private const val CURRENT = "current"
private const val SERVERS = "servers"

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
data class Server(var name: String = "",
                var address: String = "",
                var port: Int = DEFAULT_PORT,
                var apiPath: String = DEFAULT_API_PATH,
                var httpsEnabled: Boolean = false,
                var selfSignedCertificateEnabled: Boolean = false,
                var selfSignedCertificate: String = "",
                var clientCertificateEnabled: Boolean = false,
                var clientCertificate: String = "",
                var authentication: Boolean = false,
                var username: String = "",
                var password: String = "",
                var updateInterval: Int = DEFAULT_UPDATE_INTERVAL,
                var timeout: Int = DEFAULT_TIMEOUT,
                var lastTorrents: LastTorrents = LastTorrents(),
                var addTorrentDialogDirectories: Array<String> = arrayOf()) {
    companion object {
        val portRange get() = MINIMUM_PORT..MAXIMUM_PORT
        val updateIntervalRange get() = MINIMUM_UPDATE_INTERVAL..MAXIMUM_UPDATE_INTERVAL
        val timeoutRange get() = MINIMUM_TIMEOUT..MAXIMUM_TIMEOUT
    }

    fun copyTo(other: Server) {
        other.name = name
        other.address = address
        other.port = port
        other.apiPath = apiPath
        other.httpsEnabled = httpsEnabled
        other.selfSignedCertificateEnabled = selfSignedCertificateEnabled
        other.selfSignedCertificate = selfSignedCertificate
        other.clientCertificateEnabled = clientCertificateEnabled
        other.clientCertificate = clientCertificate
        other.authentication = authentication
        other.username = username
        other.password = password
        other.updateInterval = updateInterval
        other.timeout = timeout
        other.lastTorrents = lastTorrents
        other.addTorrentDialogDirectories = addTorrentDialogDirectories
    }

    override fun toString() = name

    @Serializable
    data class Torrent(val id: Int,
                       val hashString: String,
                       val name: String,
                       val finished: Boolean)

    @Serializable
    data class LastTorrents(var saved: Boolean = false,
                            var torrents: MutableList<Torrent> = mutableListOf())
}

@SuppressLint("StaticFieldLeak")
object Servers : AnkoLogger {
    private val context = Application.instance

    val servers = mutableListOf<Server>()

    private val serversListeners = mutableListOf<() -> Unit>()
    fun addServersListener(listener: () -> Unit) = serversListeners.add(listener)
    fun removeServersListener(listener: () -> Unit) = serversListeners.remove(listener)

    val hasServers: Boolean
        get() {
            return servers.isNotEmpty()
        }

    private var saveOnCurrentChanged = false
    val currentServer = MutableLiveData<Server>(null)

    @Volatile private var saveData: SaveData? = null

    init {
        load()
        currentServer.observeForever {
            if (saveOnCurrentChanged) save()
        }
        saveOnCurrentChanged = true
    }

    private fun setCurrentServer(server: Server?) {
        saveOnCurrentChanged = false
        currentServer.value = server
        saveOnCurrentChanged = true
    }

    private fun load() {
        try {
            val fileData = context.openFileInput(FILE_NAME).bufferedReader().use(BufferedReader::readText)
            val saveData = Json(JsonConfiguration.Stable).parse(SaveData.serializer(), fileData)
            for (server in saveData.servers) {
                info("Reading server \"${server}\"")
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

            currentServer.value = servers.find { it.name == saveData.currentServerName }

            if (currentServer.value == null && servers.isNotEmpty()) {
                currentServer.value = servers.first()
                save()
            }
        } catch (error: FileNotFoundException) {
            info("Error opening servers file", error)
        } catch (error: IOException) {
            error("Error reading servers file", error)
            reset()
        } catch (error: JsonException) {
            error("Error parsing servers file", error)
            reset()
        } catch (error: SerializationException) {
            error("Error deserializing servers file", error)
            reset()
        }
    }

    private fun reset() {
        currentServer.value = null
        servers.clear()
    }

    fun save() {
        info("Servers.save()")
        val lastTorrents = currentServer.value?.lastTorrents
        if (lastTorrents != null) {
            lastTorrents.torrents.clear()
            Rpc.torrents.value?.let {
                for (torrent in it) {
                    lastTorrents.torrents.add(Server.Torrent(torrent.id,
                                                             torrent.hashString,
                                                             torrent.name,
                                                             torrent.isFinished))
                }
            }
            lastTorrents.saved = true
        }

        saveData = SaveData(currentServer.value?.name,
                            servers.map { server -> server.copy(lastTorrents = server.lastTorrents.copy(torrents = server.lastTorrents.torrents.toMutableList())) })

        WorkManager.getInstance(context).enqueueUniqueWork(
                SaveWorker.UNIQUE_WORK_NAME,
                ExistingWorkPolicy.APPEND,
                OneTimeWorkRequest.from(SaveWorker::class.java)
        )
    }

    fun addServer(newServer: Server) {
        var newCurrent: Server? = null

        val overwriteServer = servers.find { it.name == newServer.name }
        if (overwriteServer == null) {
            servers.add(newServer)
            if (servers.size == 1) {
                newCurrent = newServer
            }
        } else {
            newServer.copyTo(overwriteServer)
            if (overwriteServer.name == currentServer.value?.name) {
                newCurrent = overwriteServer
            }
        }

        if (newCurrent != null) {
            setCurrentServer(newCurrent)
        }

        for (listener in serversListeners) {
            listener()
        }

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

        if (newServer.name != server.name) {
            servers.removeAll { it.name == newServer.name }
        }

        newServer.copyTo(server)

        if (currentChanged) {
            setCurrentServer(server)
        }

        for (listener in serversListeners) {
            listener()
        }

        save()
    }

    fun removeServers(toRemove: List<Server>) {
        servers.removeAll(toRemove)

        if (currentServer.value in toRemove) {
            setCurrentServer(servers.firstOrNull())
        }

        for (listener in serversListeners) {
            listener()
        }

        save()
    }

    class SaveWorker(context: Context, workerParameters: WorkerParameters) : Worker(context, workerParameters) {
        companion object {
            const val UNIQUE_WORK_NAME = "ServersSaveWorker"
        }

        override fun doWork(): Result {
            val data = saveData
            saveData = null
            info("SaveWorker.doWork(), saveData=$data")
            val elapsed = measureTimeMillis {
                if (data != null) {
                    try {
                        context.openFileOutput(FILE_NAME, Context.MODE_PRIVATE).bufferedWriter().use { writer ->
                            writer.write(Json(JsonConfiguration.Stable.copy(prettyPrint = true)).stringify(SaveData.serializer(), data))
                        }
                    } catch (error: FileNotFoundException) {
                        error("Failed to open servers file", error)
                    } catch (error: IOException) {
                        error("Failed to save servers file", error)
                    } catch (error: JsonException) {
                        error("Failed to encode servers to JSON", error)
                    } catch (error: SerializationException) {
                        error("Failed to serialize servers", error)
                    }
                }
            }
            info("SaveWorker.doWork() return, elapsed time: $elapsed ms")
            return Result.success()
        }

        override fun onStopped() {
            saveData = null
        }
    }

    @Serializable
    private class SaveData(@SerialName(CURRENT) val currentServerName: String?,
                           @SerialName(SERVERS) val servers: List<Server>)
}
