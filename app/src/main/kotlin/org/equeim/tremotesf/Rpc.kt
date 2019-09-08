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

import android.content.Context
import android.widget.Toast

import org.equeim.libtremotesf.JniRpc
import org.equeim.libtremotesf.JniServerSettings
import org.equeim.libtremotesf.ServerStats
import org.equeim.libtremotesf.Torrent

import org.jetbrains.anko.runOnUiThread


class Rpc : JniRpc() {
    companion object {
        val instance by lazy { Rpc() }
    }

    var context: Context? = null
        set(value) {
            field = value
            if (field != null) {
                updateServer()
            }
        }

    val serverSettings: JniServerSettings = serverSettings()
    val serverStats: ServerStats = serverStats()

    val statusString: String
        get() {
            return when (status()) {
                Status.Disconnected -> when (error()) {
                    Error.NoError -> context!!.getString(R.string.disconnected)
                    Error.TimedOut -> context!!.getString(R.string.timed_out)
                    Error.ConnectionError -> context!!.getString(R.string.connection_error)
                    Error.AuthenticationError -> context!!.getString(R.string.authentication_error)
                    Error.ParseError -> context!!.getString(R.string.parsing_error)
                    Error.ServerIsTooNew -> context!!.getString(R.string.server_is_too_new)
                    Error.ServerIsTooOld -> context!!.getString(R.string.server_is_too_old)
                    else -> context!!.getString(R.string.disconnected)
                }
                Status.Connecting -> context!!.getString(R.string.connecting)
                Status.Connected -> context!!.getString(R.string.connected)
                else -> context!!.getString(R.string.disconnected)
            }
        }

    private val statusListeners = mutableListOf<(Int) -> Unit>()
    private val errorListeners = mutableListOf<(Int) -> Unit>()
    private val torrentsUpdatedListeners = mutableListOf<() -> Unit>()
    private val serverStatsUpdatedListeners = mutableListOf<() -> Unit>()

    var torrentAddedListener: ((Int, String, String) -> Unit)? = null
    var torrentFinishedListener: ((Int, String, String) -> Unit)? = null

    var torrentAddDuplicateListener: (() -> Unit)? = null
    var torrentAddErrorListener: (() -> Unit)? = null

    var gotTorrentFilesListener: ((Int) -> Unit)? = null
    var torrentFileRenamedListener: ((Int, String, String) -> Unit)? = null

    var gotTorrentPeersListener: ((Int) -> Unit)? = null

    var gotDownloadDirFreeSpaceListener: ((Long) -> Unit)? = null
    var gotFreeSpaceForPathListener: ((String, Boolean, Long) -> Unit)? = null

    val torrents = mutableListOf<TorrentData>()

    private var disconnectingAfterCurrentServerChanged = false

    init {
        Servers.addCurrentServerListener {
            if (isConnected) {
                disconnectingAfterCurrentServerChanged = true
            }
            if (Servers.hasServers) {
                updateServer()
                connect()
            } else {
                resetServer()
            }
        }
    }

    private fun updateServer() {
        if (Servers.hasServers) {
            val server = Servers.currentServer!!
            setServer(server.name,
                      server.address,
                      server.port,
                      server.apiPath,
                      server.httpsEnabled,
                      server.selfSignedCertificateEnabled,
                      server.selfSignedCertificate.toByteArray(),
                      server.clientCertificateEnabled,
                      server.clientCertificate.toByteArray(),
                      server.authentication,
                      server.username,
                      server.password,
                      server.updateInterval,
                      server.backgroundUpdateInterval,
                      server.timeout)
        } else {
            resetServer()
        }
    }

    fun addStatusListener(listener: (Int) -> Unit) = statusListeners.add(listener)
    fun removeStatusListener(listener: (Int) -> Unit) = statusListeners.remove(listener)

    override fun onConnectedChanged() {
        context!!.runOnUiThread {
            if (isConnected) {
                val notifyOnFinished = Settings.notifyOnFinishedSinceLastConnection
                val notifyOnAdded = Settings.notifyOnAddedSinceLastConnection
                if (notifyOnFinished || notifyOnAdded) {
                    val server = Servers.currentServer
                    if (server != null) {
                        val lastTorrents = server.lastTorrents
                        if (lastTorrents.saved) {
                            val torrents = torrents()
                            for (torrent: Torrent in torrents) {
                                val hashString: String = torrent.hashString()
                                val oldTorrent = lastTorrents.torrents.find { it.hashString == hashString }
                                if (oldTorrent == null) {
                                    if (notifyOnAdded) {
                                        ForegroundService.showAddedNotification(torrent.id(),
                                                                                hashString,
                                                                                torrent.name(),
                                                                                context!!)
                                    }
                                } else {
                                    if (!oldTorrent.finished && (torrent.isFinished) && notifyOnFinished) {
                                        ForegroundService.showFinishedNotification(torrent.id(),
                                                                                   hashString,
                                                                                   torrent.name(),
                                                                                   context!!)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onStatusChanged() {
        context!!.runOnUiThread {
            for (listener in statusListeners) {
                listener(status())
            }
            super.onStatusChanged()
        }
    }

    fun addErrorListener(listener: (Int) -> Unit) = errorListeners.add(listener)
    fun removeErrorListener(listener: (Int) -> Unit) = errorListeners.remove(listener)

    override fun onErrorChanged() {
        context!!.runOnUiThread {
            val error = error()
            for (listener in errorListeners) {
                listener(error)
            }
            if (error == Error.ConnectionError) {
                context?.let { context ->
                    Toast.makeText(context, errorMessage(), Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    fun addTorrentsUpdatedListener(listener: () -> Unit) = torrentsUpdatedListeners.add(listener)
    fun removeTorrentsUpdatedListener(listener: () -> Unit) = torrentsUpdatedListeners.remove(listener)

    override fun onTorrentsUpdated() {
        context!!.runOnUiThread {
            val oldTorrents = torrents.toList()
            torrents.clear()
            val rpcTorrents = torrents()
            for (torrent: Torrent in rpcTorrents) {
                val id = torrent.id()
                val data = oldTorrents.find { it.id == id }
                if (data == null) {
                    torrents.add(TorrentData(torrent, context!!))
                } else {
                    torrents.add(data)
                    data.update()
                }
            }

            for (listener in torrentsUpdatedListeners) {
                listener()
            }
        }
    }

    fun addServerStatsUpdatedListener(listener: () -> Unit) = serverStatsUpdatedListeners.add(listener)
    fun removeServerStatsUpdatedListener(listener: () -> Unit) = serverStatsUpdatedListeners.remove(listener)

    override fun onServerStatsUpdated() {
        context!!.runOnUiThread {
            for (listener in serverStatsUpdatedListeners) {
                listener()
            }
        }
    }

    override fun onTorrentAdded(id: Int, hashString: String, name: String) {
        context!!.runOnUiThread {
            torrentAddedListener?.invoke(id, hashString, name)
        }
    }

    override fun onTorrentFinished(id: Int, hashString: String, name: String) {
        context!!.runOnUiThread {
            torrentFinishedListener?.invoke(id, hashString, name)
        }
    }

    override fun onTorrentAddDuplicate() {
        context!!.runOnUiThread {
            torrentAddDuplicateListener?.invoke()
        }
    }

    override fun onTorrentAddError() {
        context!!.runOnUiThread {
            torrentAddErrorListener?.invoke()
        }
    }

    override fun onGotTorrentFiles(torrentId: Int) {
        context!!.runOnUiThread {
            gotTorrentFilesListener?.invoke(torrentId)
        }
    }

    override fun onTorrentFileRenamed(torrentId: Int, filePath: String, newName: String) {
        context!!.runOnUiThread {
            torrentFileRenamedListener?.invoke(torrentId, filePath, newName)
        }
    }

    override fun onGotTorrentPeers(torrentId: Int) {
        context!!.runOnUiThread {
            gotTorrentPeersListener?.invoke(torrentId)
        }
    }

    override fun onGotDownloadDirFreeSpace(bytes: Long) {
        context!!.runOnUiThread {
            gotDownloadDirFreeSpaceListener?.invoke(bytes)
        }
    }

    override fun onGotFreeSpaceForPath(path: String, success: Boolean, bytes: Long) {
        context!!.runOnUiThread {
            gotFreeSpaceForPathListener?.invoke(path, success, bytes)
        }
    }

    override fun onAboutToDisconnect() {
        context!!.runOnUiThread {
            if (disconnectingAfterCurrentServerChanged) {
                disconnectingAfterCurrentServerChanged = false
            } else {
                Servers.save()
            }
        }
    }
}