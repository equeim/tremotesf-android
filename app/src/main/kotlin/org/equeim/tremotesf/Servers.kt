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

import java.io.FileNotFoundException
import kotlin.system.measureTimeMillis

import android.annotation.SuppressLint
import android.content.Context

import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters

import org.jetbrains.anko.AnkoLogger
import org.jetbrains.anko.error
import org.jetbrains.anko.info

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonIOException
import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import com.google.gson.JsonParser
import com.google.gson.JsonSyntaxException


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

    data class Torrent(val id: Int,
                       val hashString: String,
                       val name: String,
                       val finished: Boolean)

    data class LastTorrents(var saved: Boolean = false,
                            var torrents: MutableList<Torrent> = mutableListOf())
}

@SuppressLint("StaticFieldLeak")
object Servers : AnkoLogger {
    var context: Context? = null
        set(value) {
            field = value
            if (field == null) {
                reset()
            } else {
                load()
            }
        }

    val servers = mutableListOf<Server>()

    private val serversListeners = mutableListOf<() -> Unit>()
    fun addServersListener(listener: () -> Unit) = serversListeners.add(listener)
    fun removeServersListener(listener: () -> Unit) = serversListeners.remove(listener)

    val hasServers: Boolean
        get() {
            return servers.isNotEmpty()
        }

    private val currentServerListeners = mutableListOf<() -> Unit>()
    fun addCurrentServerListener(listener: () -> Unit) = currentServerListeners.add(listener)
    fun removeCurrentServerListener(listener: () -> Unit) = currentServerListeners.remove(listener)

    private var currentServerField: Server? = null
    var currentServer: Server?
        get() = currentServerField
        set(value) {
            if (value !== currentServerField) {
                currentServerField = value
                save()
                for (listener in currentServerListeners) {
                    listener()
                }
            }
        }

    @Volatile private var saveData: SaveData? = null

    private fun load() {
        try {
            val stream = context!!.openFileInput(FILE_NAME)
            try {
                val jsonObject = JsonParser().parse(stream.reader()).asJsonObject

                if (jsonObject.has(SERVERS)) {
                    val gson = Gson()
                    for (jsonElement in jsonObject.getAsJsonArray(SERVERS)) {
                        val server = gson.fromJson(jsonElement, Server::class.java)
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
                }

                if (jsonObject.has(CURRENT)) {
                    val currentServerName = jsonObject[CURRENT].asString
                    currentServerField = servers.find { it.name == currentServerName }
                }

                if (currentServerField == null && servers.isNotEmpty()) {
                    currentServerField = servers.first()
                    save()
                }
            } catch (error: JsonIOException) {
                error("Error parsing servers file", error)
                reset()
            } catch (error: JsonParseException) {
                error("Error parsing servers file", error)
                reset()
            } catch (error: IllegalStateException) {
                error("Error parsing servers file", error)
                reset()
            } catch (error: JsonSyntaxException) {
                error("Error parsing servers file", error)
                reset()
            } finally {
                stream.close()
            }
        } catch (error: FileNotFoundException) {
            info("Servers file not found")
        }
    }

    private fun reset() {
        currentServerField = null
        servers.clear()
    }

    fun save() {
        info("Servers.save()")
        val lastTorrents = currentServerField?.lastTorrents
        if (lastTorrents != null) {
            lastTorrents.torrents.clear()
            for (torrent in Rpc.torrents) {
                lastTorrents.torrents.add(Server.Torrent(torrent.id,
                                                         torrent.hashString,
                                                         torrent.name,
                                                         torrent.isFinished))
            }
            lastTorrents.saved = true
        }

        saveData = SaveData(currentServerField?.name,
                            servers.map { server -> server.copy(lastTorrents = server.lastTorrents.copy(torrents = server.lastTorrents.torrents.toMutableList())) })

        WorkManager.getInstance(context!!).enqueueUniqueWork(
                SaveWorker.UNIQUE_WORK_NAME,
                ExistingWorkPolicy.APPEND,
                OneTimeWorkRequest.from(SaveWorker::class.java)
        )
    }

    fun addServer(newServer: Server) {
        var currentChanged = false

        val overwriteServer = servers.find { it.name == newServer.name }
        if (overwriteServer == null) {
            servers.add(newServer)
            if (servers.size == 1) {
                currentServerField = newServer
                currentChanged = true
            }
        } else {
            newServer.copyTo(overwriteServer)
            if (overwriteServer == currentServerField) {
                currentChanged = true
            }
        }

        save()

        for (listener in serversListeners) {
            listener()
        }

        if (currentChanged) {
            for (listener in currentServerListeners) {
                listener()
            }
        }
    }

    fun setServer(server: Server, newServer: Server) {
        val currentChanged = (server == currentServerField) || (newServer.name == currentServerField!!.name)

        if (newServer.name != server.name) {
            val overwriteServer = servers.find { it.name == newServer.name }
            if (overwriteServer != null) {
                servers.remove(overwriteServer)
                if (overwriteServer == currentServerField) {
                    currentServerField = server
                }
            }
        }

        newServer.copyTo(server)
        save()
        for (listener in serversListeners) {
            listener()
        }
        if (currentChanged) {
            for (listener in currentServerListeners) {
                listener()
            }
        }
    }

    fun removeServers(servers: List<Server>) {
        this.servers.removeAll(servers)

        if (currentServerField in servers) {
            currentServerField = this.servers.firstOrNull()

            for (listener in currentServerListeners) {
                listener()
            }
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
                    val gson = GsonBuilder().setPrettyPrinting().create()
                    val jsonObject = JsonObject()
                    jsonObject.addProperty(CURRENT, data.currentServerName)
                    jsonObject.add(SERVERS, gson.toJsonTree(data.servers))
                    context?.getFileStreamPath(FILE_NAME)?.writeText(gson.toJson(jsonObject))
                }
            }
            info("SaveWorker.doWork() return, elapsed time: $elapsed ms")
            return Result.success()
        }

        override fun onStopped() {
            saveData = null
        }
    }

    private class SaveData(val currentServerName: String?,
                           val servers: List<Server>)
}
