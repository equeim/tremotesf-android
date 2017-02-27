/*
 * Copyright (C) 2017 Alexey Rochev <equeim@gmail.com>
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

import java.net.HttpURLConnection
import java.net.MalformedURLException
import java.net.SocketTimeoutException
import java.net.URL

import javax.net.ssl.HostnameVerifier
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSession
import javax.net.ssl.TrustManagerFactory

import java.security.KeyFactory
import java.security.KeyStore
import java.security.cert.CertificateException
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.interfaces.RSAPrivateKey
import java.security.spec.PKCS8EncodedKeySpec

import java.util.Timer

import kotlin.concurrent.schedule

import android.content.Context
import android.os.AsyncTask
import android.os.Handler
import android.util.Base64

import com.github.kittinunf.fuel.Fuel
import com.github.kittinunf.fuel.core.FuelManager
import com.github.kittinunf.fuel.core.Request
import com.github.kittinunf.fuel.core.ResponseDeserializable

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonSyntaxException

import org.equeim.tremotesf.utils.Logger


// Transmission 2.40+
private const val MINIMUM_RPC_VERSION = 14

private const val SESSION_ID_HEADER = "X-Transmission-Session-Id"

private val gson = Gson()
private fun makeRequestData(method: String, arguments: Map<String, Any>): String {
    return gson.toJson(mapOf("method" to method,
                             "arguments" to arguments))
}

private fun getReplyArguments(jsonObject: JsonObject): JsonObject {
    return jsonObject.getAsJsonObject("arguments")
}

private fun isResultSuccessful(jsonObject: JsonObject): Boolean {
    return jsonObject["result"].asString == "success"
}

object Rpc {
    enum class Status {
        Disconnected,
        Connecting,
        Connected
    }

    enum class Error {
        None,
        NoServers,
        InvalidServerUrl,
        TimedOut,
        ConnectionError,
        Authentication,
        ParsingError,
        ServerIsTooNew,
        ServerIsTooOld,
    }

    private lateinit var context: Context

    //
    // status property
    //
    var status = Status.Disconnected
        private set(value) {
            if (value == field) {
                return
            }

            val wasConnected = connected

            field = value

            if (wasConnected) {
                torrents.clear()
                resetTimer()
            }

            if (value == Status.Disconnected) {
                rpcVersionChecked = false
                serverSettingsUpdated = false
                torrentsUpdated = false
                serverStatsUpdated = false

                activeRequests.forEach { it.cancel() }
                activeRequests.clear()
            }

            for (listener in statusListeners) {
                listener(value)
            }
        }

    val statusString: String
        get() {
            return when (status) {
                Status.Disconnected -> when (error) {
                    Error.None -> context.getString(R.string.disconnected)
                    Error.NoServers -> context.getString(R.string.no_servers)
                    Error.InvalidServerUrl -> context.getString(R.string.invalid_server_url)
                    Error.TimedOut -> context.getString(R.string.timed_out)
                    Error.ConnectionError -> context.getString(R.string.connection_error)
                    Error.Authentication -> context.getString(R.string.authentication_error)
                    Error.ParsingError -> context.getString(R.string.parsing_error)
                    Error.ServerIsTooNew -> context.getString(R.string.server_is_too_new)
                    Error.ServerIsTooOld -> context.getString(R.string.server_is_too_old)
                }
                Status.Connecting -> context.getString(R.string.connecting)
                Status.Connected -> context.getString(R.string.connected)
            }
        }

    private val statusListeners = mutableListOf<(Status) -> Unit>()
    fun addStatusListener(listener: (Status) -> Unit) = statusListeners.add(listener)
    fun removeStatusListener(listener: (Status) -> Unit) = statusListeners.remove(listener)

    val connected: Boolean
        get() {
            return (status == Status.Connected)
        }

    var error = Error.None
        private set(value) {
            if (value != field) {
                field = value
                for (listener in errorListeners) {
                    listener(error)
                }
            }
        }

    val canConnect: Boolean
        get() {
            return when (error) {
                Error.NoServers,
                Error.InvalidServerUrl -> false
                else -> true
            }
        }

    private val errorListeners = mutableListOf<(Error) -> Unit>()
    fun addErrorListener(listener: (Error) -> Unit) = errorListeners.add(listener)
    fun removeErrorListener(listener: (Error) -> Unit) = errorListeners.remove(listener)

    var updateDisabled = false
        set(value) {
            if (value != field) {
                field = value
                if (value) {
                    resetTimer()
                } else if (connected) {
                    updateData()
                }
            }
        }

    var backgroundUpdate = false
        set(value) {
            if (value != field) {
                field = value
                resetTimer()
                if (value) {
                    startTimer()
                } else {
                    updateData()
                }
            }
        }

    private lateinit var url: String
    private var timeout = 0
    private var updateInterval = 0L
    private var backgroundUpdateInterval = 0L
    private var authentication = false
    private lateinit var username: String
    private lateinit var password: String

    private val responseDeserializer = object : ResponseDeserializable<JsonObject> {
        override fun deserialize(content: String): JsonObject? {
            return gson.fromJson(content, JsonObject::class.java)
        }
    }
    private val activeRequests = mutableListOf<Request>()
    private var sessionId = String()

    private var timer = Timer()
    private val mainThreadHandler = Handler()

    private var rpcVersionChecked = false
    private var serverSettingsUpdated = false
    private var torrentsUpdated = false
    private var serverStatsUpdated = false

    val serverSettings = ServerSettings()
    val torrents = mutableListOf<Torrent>()
    val serverStats = ServerStats()

    private val updatedListeners = mutableListOf<() -> Unit>()
    fun addUpdatedListener(listener: () -> Unit) = updatedListeners.add(listener)
    fun removeUpdatedListener(listener: () -> Unit) = updatedListeners.remove(listener)

    var torrentDuplicateListener: (() -> Unit)? = null
    var torrentAddErrorListener: (() -> Unit)? = null

    var torrentFinishedListener: ((Torrent) -> Unit)? = null

    private var initialized = false
    fun init(context: Context) {
        if (!initialized) {
            Servers.addCurrentServerListener { updateServer() }
            initialized = true
            this.context = context
        }
        updateServer()
    }

    fun connect() {
        if (status == Status.Disconnected && canConnect) {
            error = Error.None
            status = Status.Connecting
            getServerSettings()
        }
    }

    fun disconnect() {
        error = Error.None
        status = Status.Disconnected
    }

    fun addTorrentFile(fileData: ByteArray,
                       downloadDirectory: String,
                       wantedFiles: List<Int>,
                       unwantedFiles: List<Int>,
                       lowPriorityFiles: List<Int>,
                       normalPriorityFiles: List<Int>,
                       highPriorityFiles: List<Int>,
                       priority: Int,
                       start: Boolean) {
        if (!connected) {
            return
        }

        object : AsyncTask<Any, Any, String>() {
            override fun doInBackground(vararg params: Any?): String {
                return makeRequestData("torrent-add",
                                       mapOf("metainfo" to String(Base64.encode(fileData,
                                                                                Base64.DEFAULT)),
                                             "download-dir" to downloadDirectory,
                                             "files-wanted" to wantedFiles,
                                             "files-unwanted" to unwantedFiles,
                                             "priority-low" to lowPriorityFiles,
                                             "priority-normal" to normalPriorityFiles,
                                             "priority-high" to highPriorityFiles,
                                             "bandwidthPriority" to priority,
                                             "paused" to !start))
            }

            override fun onPostExecute(result: String) {
                if (connected) {
                    postRequest(result, { jsonObject ->
                        if (isResultSuccessful(jsonObject)) {
                            if (getReplyArguments(jsonObject).has("torrent-duplicate")) {
                                torrentDuplicateListener?.invoke()
                            } else {
                                resetTimer()
                                updateData()
                            }
                        } else {
                            torrentAddErrorListener?.invoke()
                        }
                    })
                }
            }
        }.execute()
    }

    fun addTorrentLink(link: String,
                       downloadDirectory: String,
                       priority: Int,
                       start: Boolean) {
        if (!connected) {
            return
        }

        postRequest(makeRequestData("torrent-add",
                                    mapOf("filename" to link,
                                          "download-dir" to downloadDirectory,
                                          "bandwidthPriority" to priority,
                                          "paused" to !start)),
                    { jsonObject ->
                        if (isResultSuccessful(jsonObject)) {
                            if (getReplyArguments(jsonObject).has("torrent-duplicate")) {
                                torrentDuplicateListener?.invoke()
                            } else {
                                resetTimer()
                                updateData()
                            }
                        } else {
                            torrentAddErrorListener?.invoke()
                        }
                    })
    }

    fun removeTorrents(ids: List<Int>, deleteFiles: Boolean) {
        if (connected) {
            postRequest(makeRequestData("torrent-remove", mapOf("ids" to ids,
                                                                "delete-local-data" to deleteFiles))) {
                resetTimer()
                updateData()
            }
        }
    }

    fun startTorrents(ids: List<Int>) {
        if (connected) {
            postRequest(makeRequestData("torrent-start", mapOf("ids" to ids))) {
                resetTimer()
                updateData()
            }
        }
    }

    fun pauseTorrents(ids: List<Int>) {
        if (connected) {
            postRequest(makeRequestData("torrent-stop", mapOf("ids" to ids))) {
                resetTimer()
                updateData()
            }
        }
    }

    fun checkTorrents(ids: List<Int>) {
        if (connected) {
            postRequest(makeRequestData("torrent-verify", mapOf("ids" to ids))) {
                resetTimer()
                updateData()
            }
        }
    }

    private fun getServerSettings() {
        Logger.d("get server settings")

        postRequest("{\"method\": \"session-get\"}", { jsonObject ->
            Logger.d("got server settings")

            serverSettings.update(getReplyArguments(jsonObject))
            serverSettingsUpdated = true
            if (!rpcVersionChecked) {
                rpcVersionChecked = true
                if (serverSettings.minimumRpcVersion > MINIMUM_RPC_VERSION) {
                    error = Error.ServerIsTooNew
                    status = Status.Disconnected
                } else if (serverSettings.rpcVersion < MINIMUM_RPC_VERSION) {
                    error = Error.ServerIsTooOld
                    status = Status.Disconnected
                } else {
                    getTorrents()
                    getServerStats()
                }
            } else {
                checkIfUpdated()
            }
        })
    }

    fun setSessionProperty(property: String, value: Any) {
        if (connected) {
            postRequest(makeRequestData("session-set", mapOf(property to value)), null)
        }
    }

    private fun getTorrents() {
        Logger.d("get torrents")

        postRequest(
                """
{
    "arguments": {
        "fields": [
            "activityDate",
            "addedDate",
            "bandwidthPriority",
            "comment",
            "creator",
            "dateCreated",
            "doneDate",
            "downloadDir",
            "downloadedEver",
            "downloadLimit",
            "downloadLimited",
            "error",
            "errorString",
            "eta",
            "hashString",
            "haveValid",
            "honorsSessionLimits",
            "id",
            "leftUntilDone",
            "name",
            "peer-limit",
            "peersConnected",
            "peersGettingFromUs",
            "peersSendingToUs",
            "percentDone",
            "queuePosition",
            "rateDownload",
            "rateUpload",
            "recheckProgress",
            "seedIdleLimit",
            "seedIdleMode",
            "seedRatioLimit",
            "seedRatioMode",
            "sizeWhenDone",
            "status",
            "totalSize",
            "trackerStats",
            "uploadedEver",
            "uploadLimit",
            "uploadLimited",
            "uploadRatio"
        ]
    },
    "method": "torrent-get"
}""",
                { jsonObject ->
                    Logger.d("got torrents")

                    object : AsyncTask<Any, Any, List<Torrent>>() {
                        override fun doInBackground(vararg params: Any?): List<Torrent> {
                            val torrentJsons = getReplyArguments(jsonObject).getAsJsonArray("torrents")
                            val newTorrents = mutableListOf<Torrent>()

                            for (jsonElement in torrentJsons) {
                                val torrentJson = jsonElement.asJsonObject
                                val id = torrentJson["id"].asInt

                                var torrent = torrents.find { it.id == id }
                                if (torrent == null) {
                                    torrent = Torrent(id, torrentJson, context)
                                } else {
                                    val progress = torrent.percentDone
                                    torrent.update(torrentJson)
                                    if (torrent.percentDone == 1.0 && progress != 1.0) {
                                        torrentFinishedListener?.invoke(torrent)
                                    }
                                }
                                newTorrents.add(torrent)

                                if (torrent.filesUpdateEnabled) {
                                    torrent.filesUpdated = false
                                    getTorrentFiles(id, true)
                                }

                                if (torrent.peersUpdateEnabled) {
                                    torrent.peersUpdated = false
                                    getTorrentPeers(id, true)
                                }
                            }

                            return newTorrents
                        }

                        override fun onPostExecute(result: List<Torrent>) {
                            if (!torrentsUpdated && this@Rpc.status != Rpc.Status.Disconnected) {
                                torrents.clear()
                                torrents.addAll(result)
                                checkIfTorrentsUpdated()
                                checkIfUpdated()
                            }
                        }
                    }.execute()
                })
    }

    fun getTorrentFiles(id: Int, scheduledUpdate: Boolean) {
        postRequest(
                """
{
    "arguments": {
        "fields": [
            "files",
            "fileStats"
        ],
        "ids": [$id]
    },
    "method": "torrent-get"
}""",
                { jsonObject ->
                    val torrent = torrents.find { it.id == id }
                    if (torrent != null) {
                        val torrentJson = getReplyArguments(jsonObject)
                                .getAsJsonArray("torrents")[0].asJsonObject
                        val files = torrentJson.getAsJsonArray("files")
                        val fileStats = torrentJson.getAsJsonArray("fileStats")
                        torrent.updateFiles(files, fileStats)
                        if (scheduledUpdate) {
                            checkIfTorrentsUpdated()
                            checkIfUpdated()
                        } else {
                            torrent.filesLoadedListener?.invoke()
                        }
                    }
                })
    }

    fun getTorrentPeers(id: Int, scheduledUpdate: Boolean) {
        postRequest(
                """
{
    "arguments": {
        "fields": ["peers"],
        "ids": [$id]
    },
    "method": "torrent-get"
}""",
                { jsonObject ->
                    val torrent = torrents.find { it.id == id }
                    if (torrent != null) {
                        val torrentJson = getReplyArguments(jsonObject)
                                .getAsJsonArray("torrents")[0].asJsonObject
                        torrent.updatePeers(torrentJson.getAsJsonArray("peers"))
                        if (scheduledUpdate) {
                            checkIfTorrentsUpdated()
                            checkIfUpdated()
                        } else {
                            torrent.peersLoadedListener?.invoke()
                        }
                    }
                })
    }

    fun setTorrentProperty(torrentId: Int,
                           property: String,
                           value: Any,
                           updateOnSuccess: Boolean = false) {
        postRequest(makeRequestData("torrent-set",
                                    mapOf("ids" to intArrayOf(torrentId),
                                          property to value)),
                    { jsonObject ->
                        if (updateOnSuccess) {
                            resetTimer()
                            updateData()
                        }
                    })
    }

    fun setTorrentLocation(torrentId: Int,
                           location: String,
                           moveFiles: Boolean) {
        if (!Rpc.connected) {
            return
        }

        postRequest(makeRequestData("torrent-set-location",
                                    mapOf("ids" to intArrayOf(torrentId),
                                          "location" to location,
                                          "move" to moveFiles)),
                    { jsonObject ->
                        resetTimer()
                        updateData()
                    })
    }

    fun renameTorrentFile(torrentId: Int, filePath: String, newName: String) {
        if (!Rpc.connected) {
            return
        }

        postRequest(makeRequestData("torrent-rename-path",
                                    mapOf("ids" to intArrayOf(torrentId),
                                          "path" to filePath,
                                          "name" to newName)),
                    { jsonObject ->
                        val arguments = getReplyArguments(jsonObject)
                        val id = arguments["id"].asInt
                        val torrent = torrents.find { it.id == id }
                        if (torrent != null) {
                            torrent.fileRenamedListener?.invoke(arguments["path"].asString,
                                                                arguments["name"].asString)
                        }
                        resetTimer()
                        updateData()
                    })
    }

    private fun getServerStats() {
        Logger.d("get server stats")

        postRequest("{\"method\": \"session-stats\"}", { jsonObject ->
            serverStats.update(getReplyArguments(jsonObject))
            serverStatsUpdated = true
            checkIfUpdated()
        })
    }

    fun updateData() {
        serverSettingsUpdated = false
        torrentsUpdated = false
        serverStatsUpdated = false
        getServerSettings()
        getTorrents()
        getServerStats()
    }

    private fun checkIfTorrentsUpdated() {
        for (torrent in torrents) {
            if (!torrent.updated) {
                return
            }
        }
        torrentsUpdated = true
    }

    private fun checkIfUpdated() {
        if (serverSettingsUpdated && torrentsUpdated && serverStatsUpdated) {
            if (status == Status.Connecting) {
                status = Status.Connected
            } else {
                for (listener in updatedListeners) {
                    listener()
                }
            }

            if (!updateDisabled) {
                Logger.d("starting update timer")
                startTimer()
            }
        }
    }

    private fun resetTimer() {
        timer.cancel()
        timer = Timer()
    }

    private fun startTimer() {
        timer.schedule(if (backgroundUpdate) backgroundUpdateInterval else updateInterval) {
            if (connected) {
                mainThreadHandler.post { updateData() }
            }
        }
    }

    private fun updateServer() {
        disconnect()

        if (!Servers.hasServers) {
            error = Error.NoServers
            return
        }

        val server = Servers.currentServer!!

        try {
            val scheme = if (server.httpsEnabled) "https" else "http"
            url = URL(scheme, server.address, server.port, server.apiPath).toString()
        } catch (error: MalformedURLException) {
            Logger.e("invalid server url", error)
            this.error = Error.InvalidServerUrl
            return
        }

        FuelManager.instance.socketFactory = HttpsURLConnection.getDefaultSSLSocketFactory()
        FuelManager.instance.hostnameVerifier = HttpsURLConnection.getDefaultHostnameVerifier()
        if (server.clientCertificateEnabled || server.selfSignedSertificateEnabled) {
            val certificateFactory = CertificateFactory.getInstance("X.509")

            var kmf: KeyManagerFactory? = null
            if (server.clientCertificateEnabled) {
                val certEncoded = server.clientCertificate
                        .substringAfter("-----BEGIN CERTIFICATE-----")
                        .substringBefore("-----END CERTIFICATE-----")
                val keyEncoded = server.clientCertificate
                        .substringAfter("-----BEGIN PRIVATE KEY-----")
                        .substringBefore("-----END PRIVATE KEY-----")
                if (certEncoded.isNotEmpty() && keyEncoded.isNotEmpty()) {
                    try {
                        val cert = certificateFactory.generateCertificate(Base64.decode(certEncoded,
                                                                                        0).inputStream()) as X509Certificate
                        val key = KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(
                                Base64.decode(keyEncoded, 0))) as RSAPrivateKey
                        val keyStore = KeyStore.getInstance(KeyStore.getDefaultType())
                        keyStore.load(null)
                        keyStore.setCertificateEntry("cert-alias", cert)
                        keyStore.setKeyEntry("key-alias", key, charArrayOf(), arrayOf(cert))
                        kmf = KeyManagerFactory.getInstance("X509")
                        kmf.init(keyStore, charArrayOf())
                    } catch (error: IllegalArgumentException) {
                        Logger.e("client certificate decoding error: $error")
                    } catch (error: CertificateException) {
                        Logger.e("client certificate parsing error: $error")
                    }
                }
            }

            var tmf: TrustManagerFactory? = null
            if (server.selfSignedSertificateEnabled) {
                val certEncoded = server.selfSignedSertificate
                        .substringAfter("-----BEGIN CERTIFICATE-----")
                        .substringBefore("-----END CERTIFICATE-----")
                if (certEncoded.isNotEmpty()) {
                    try {
                        val cert = certificateFactory.generateCertificate(Base64.decode(certEncoded,
                                                                                        0).inputStream()) as X509Certificate
                        val keyStore = KeyStore.getInstance(KeyStore.getDefaultType())
                        keyStore.load(null)
                        keyStore.setCertificateEntry("cert-alias", cert)
                        tmf = TrustManagerFactory.getInstance("X509")
                        tmf.init(keyStore)
                    } catch (error: IllegalArgumentException) {
                        Logger.e("self-signed certificate decoding error: $error")
                    } catch (error: CertificateException) {
                        Logger.e("self-signed certificate parsing error: $error")
                    }

                    FuelManager.instance.hostnameVerifier = object : HostnameVerifier {
                        private val serverHostname = server.address
                        override fun verify(hostname: String, session: SSLSession?): Boolean {
                            return (hostname == serverHostname)
                        }
                    }
                }
            }

            if (kmf != null || tmf != null) {
                val sslContext = SSLContext.getInstance("TLS")
                sslContext.init(kmf?.keyManagers, tmf?.trustManagers, null)
                FuelManager.instance.socketFactory = sslContext.socketFactory
            }
        }

        authentication = server.authentication
        username = server.username
        password = server.password

        timeout = server.timeout * 1000
        updateInterval = (server.updateInterval * 1000).toLong()
        backgroundUpdateInterval = (server.backgroundUpdateInterval * 1000).toLong()

        connect()
    }

    private fun postRequest(data: String, callOnSuccess: ((JsonObject) -> Unit)?) {
        val request = Fuel.post(url)
                .body(data)
                .header(Pair(SESSION_ID_HEADER, sessionId))
                .timeout(timeout)

        if (authentication) {
            request.authenticate(username, password)
        }

        request.responseObject(responseDeserializer,
                               { request, response, result ->
                                   val (jsonObject, error) = result
                                   if (error == null) {
                                       callOnSuccess?.invoke(jsonObject!!)
                                   } else {
                                       when (response.httpStatusCode) {
                                           HttpURLConnection.HTTP_CONFLICT -> {
                                               val headers = response.httpResponseHeaders
                                               if (headers.containsKey(SESSION_ID_HEADER)) {
                                                   sessionId = headers[SESSION_ID_HEADER]!!.first()
                                                   postRequest(data, callOnSuccess)
                                               } else {
                                                   Logger.e("no session id header")
                                                   this.error = Error.ConnectionError
                                                   status = Status.Disconnected
                                               }
                                           }
                                           HttpURLConnection.HTTP_UNAUTHORIZED -> {
                                               Logger.e("authentication error")
                                               this.error = Error.Authentication
                                               status = Status.Disconnected
                                           }
                                           else -> {
                                               when (error.exception) {
                                                   is JsonSyntaxException -> {
                                                       Logger.e("parsing error: ${error.exception}")
                                                       this.error = Error.ParsingError
                                                   }
                                                   is SocketTimeoutException -> {
                                                       Logger.e("connection timed out: ${error.exception}")
                                                       this.error = Error.TimedOut
                                                   }
                                                   else -> {
                                                       Logger.e("connection error: ${error.exception}")
                                                       this.error = Error.ConnectionError
                                                   }
                                               }
                                               status = Status.Disconnected
                                           }
                                       }
                                   }

                                   activeRequests.remove(request)
                               })

        activeRequests.add(request)
    }
}