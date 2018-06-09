/*
 * Copyright (C) 2017-2018 Alexey Rochev <equeim@gmail.com>
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

import android.annotation.SuppressLint
import android.content.Context
import android.os.AsyncTask

import org.jetbrains.anko.AnkoLogger
import org.jetbrains.anko.debug
import org.jetbrains.anko.error

import com.google.gson.GsonBuilder
import com.google.gson.JsonIOException
import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import com.google.gson.JsonParser
import com.google.gson.JsonSyntaxException


private const val FILE_NAME = "servers.json"
private const val CURRENT = "current"
private const val SERVERS = "servers"

class Server {
    var name = ""
    var address = ""
    var port = 0
    var apiPath = ""
    var httpsEnabled = false
    var selfSignedCertificateEnabled = false
    var selfSignedCertificate = ""
    var clientCertificateEnabled = false
    var clientCertificate = ""
    var authentication = false
    var username = ""
    var password = ""
    var updateInterval = 0
    var backgroundUpdateInterval = 0
    var timeout = 0
    var lastTorrents = LastTorrents()

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
        other.backgroundUpdateInterval = backgroundUpdateInterval
        other.timeout = timeout
        other.lastTorrents = lastTorrents
    }

    override fun toString() = name

    class LastTorrents {
        var saved = false
        var torrents = mutableListOf<Torrent>()
    }

    data class Torrent(var id: Int = 0,
                       var hashString: String = "",
                       var name: String = "",
                       var finished: Boolean = false)
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

    private val gson = GsonBuilder().setPrettyPrinting().create()

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

    private fun load() {
        try {
            val stream = context!!.openFileInput(FILE_NAME)
            try {
                val jsonObject = JsonParser().parse(stream.reader()).asJsonObject

                if (jsonObject.has(SERVERS)) {
                    for ((i, jsonElement) in jsonObject.getAsJsonArray(SERVERS).withIndex()) {
                        val server = gson.fromJson(jsonElement, Server::class.java)
                        if (server.name.isEmpty()) {
                            error("server's name can't be empty")
                            server.name = i.toString()
                        }
                        if (server.address.isEmpty()) {
                            error("server's address can't be empty")
                            server.address = "example.com"
                        }
                        if (server.port < 0) {
                            error("server's port can't be less than 0")
                            server.port = 9091
                        }
                        if (server.apiPath.isEmpty()) {
                            error("server's API path can't be empty")
                            server.apiPath = "/transmission/rpc"
                        }
                        if (server.updateInterval < 1) {
                            error("server's update interval can't be less than 1")
                            server.updateInterval = 5
                        }
                        if (server.backgroundUpdateInterval < 1) {
                            error("server's background update interval can't be less than 1")
                            server.backgroundUpdateInterval = 60
                        }
                        if (server.timeout < 1) {
                            error("server's timeout can't be less than 1")
                            server.timeout = 30
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
                error("error parsing servers file", error)
                reset()
            } catch (error: JsonParseException) {
                error("error parsing servers file", error)
                reset()
            } catch (error: IllegalStateException) {
                error("error parsing servers file", error)
                reset()
            } catch (error: JsonSyntaxException) {
                error("error parsing servers file", error)
                reset()
            } finally {
                stream.close()
            }
        } catch (error: FileNotFoundException) {
            debug("servers file not found")
        }
    }

    private fun reset() {
        currentServerField = null
        servers.clear()
    }

    fun save() {
        object : AsyncTask<Any, Any, Any?>() {
            override fun doInBackground(vararg params: Any?): Any? {
                debug("saving servers file")
                val jsonObject = JsonObject()
                jsonObject.addProperty(CURRENT, currentServerField?.name)
                jsonObject.add(SERVERS, gson.toJsonTree(servers))
                context?.getFileStreamPath(FILE_NAME)?.writeText(gson.toJson(jsonObject))
                return null
            }
        }.execute()
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
            currentServerField = if (this.servers.isEmpty()) {
                null
            } else {
                this.servers.first()
            }

            for (listener in currentServerListeners) {
                listener()
            }
        }

        for (listener in serversListeners) {
            listener()
        }

        save()
    }
}