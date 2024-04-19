// SPDX-FileCopyrightText: 2017-2024 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package org.equeim.tremotesf.ui.addtorrent

import android.app.Application
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.net.Uri
import androidx.core.content.getSystemService
import androidx.core.net.toUri
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.equeim.tremotesf.BuildConfig
import org.equeim.tremotesf.R
import org.equeim.tremotesf.rpc.GlobalRpcClient
import org.equeim.tremotesf.rpc.RpcRequestError
import org.equeim.tremotesf.rpc.requests.addTorrentLink
import org.equeim.tremotesf.rpc.requests.checkIfTorrentExists
import org.equeim.tremotesf.rpc.requests.torrentproperties.TorrentLimits
import org.equeim.tremotesf.rpc.requests.torrentproperties.addTorrentTrackers
import org.equeim.tremotesf.torrentfile.MagnetLink
import org.equeim.tremotesf.torrentfile.parseMagnetLink
import org.equeim.tremotesf.ui.Settings
import org.equeim.tremotesf.ui.addtorrent.AddTorrentFragment.AddTorrentState
import timber.log.Timber
import java.util.concurrent.atomic.AtomicReference

class AddTorrentLinkModel(private val initialUri: Uri?, application: Application) :
    BaseAddTorrentModel(application) {
    private val _addTorrentState = MutableStateFlow<AddTorrentState?>(null)
    val addTorrentState: StateFlow<AddTorrentState?> by ::_addTorrentState

    private var magnetLinkForExistingTorrent: MagnetLink? = null
    private var existingTorrentName: String? = null

    private val checkingIfTorrentExistsForInitialLink = AtomicReference<Job>(null)

    suspend fun getInitialTorrentLink(): String? {
        initialUri?.let { return it.toString() }
        if (!Settings.fillTorrentLinkFromKeyboard.get()) {
            Timber.d("Filling torrent link from clipboard is disabled")
            return null
        }
        Timber.d("Filling torrent link from clipboard")
        val clipboardManager = getApplication<Application>().getSystemService<ClipboardManager>()
        if (clipboardManager == null) {
            Timber.e("ClipboardManager is null")
            return null
        }
        if (!clipboardManager.hasPrimaryClip()) {
            Timber.d("Clipboard is empty")
            return null
        }
        if (!TORRENT_LINK_MIME_TYPES.any { clipboardManager.primaryClipDescription?.hasMimeType(it) == true }) {
            Timber.d("Clipboard content has unsupported MIME type")
            return null
        }
        return clipboardManager
            .primaryClip
            ?.getTorrentUri(getApplication())
            ?.takeIf { it.type == TorrentUri.Type.Link }
            ?.uri
            ?.toString()
            .also {
                if (BuildConfig.DEBUG) {
                    Timber.d("Torrent link from clipboard is $it")
                }
            }
    }

    fun acceptDragStartEvent(clipDescription: ClipDescription): Boolean {
        Timber.i("Drag start event mime types = ${clipDescription.mimeTypes()}")
        return TORRENT_LINK_MIME_TYPES.any(clipDescription::hasMimeType)
    }

    fun getTorrentLinkFromDropEvent(clipData: ClipData): String? {
        return clipData.getTorrentUri(getApplication())
            ?.takeIf { it.type == TorrentUri.Type.Link }
            ?.uri
            ?.toString()
    }

    fun addTorrentLink(
        torrentLink: String,
        downloadDirectory: String,
        priority: TorrentLimits.BandwidthPriority,
        startDownloading: Boolean
    ) {
        Timber.d("addTorrentLink() called with: torrentLink = $torrentLink")
        checkingIfTorrentExistsForInitialLink.get()?.cancel()
        val magnetLink = try {
            parseMagnetLink(torrentLink.toUri())
        } catch (e: IllegalArgumentException) {
            Timber.e(e, "Failed to parse '$torrentLink' as a magnet link")
            null
        }
        viewModelScope.launch {
            if (magnetLink != null) {
                _addTorrentState.value = AddTorrentState.CheckingIfTorrentExists
                if (checkIfTorrentExists(magnetLink)) {
                    return@launch
                }
            }
            GlobalRpcClient.performBackgroundRpcRequest(R.string.add_torrent_error) {
                addTorrentLink(torrentLink, downloadDirectory, priority, startDownloading)
            }
            _addTorrentState.value = AddTorrentState.AddedTorrent
        }
    }

    fun checkIfTorrentExistsForInitialLink(torrentLink: String) {
        Timber.d("checkIfTorrentExistsForInitialLink() called with: torrentLink = $torrentLink")
        if (checkingIfTorrentExistsForInitialLink.get() != null) {
            return
        }
        val magnetLink = try {
            parseMagnetLink(torrentLink.toUri())
        } catch (e: IllegalArgumentException) {
            Timber.e(e, "Failed to parse '$torrentLink' as a magnet link")
            return
        }
        viewModelScope.launch {
            checkIfTorrentExists(magnetLink)
        }.also { job ->
            if (job.isActive) {
                checkingIfTorrentExistsForInitialLink.set(job)
                job.invokeOnCompletion {
                    checkingIfTorrentExistsForInitialLink.compareAndSet(
                        job,
                        null
                    )
                }
            }
        }
    }

    private suspend fun checkIfTorrentExists(magnetLink: MagnetLink): Boolean {
        val existingTorrentName = try {
            GlobalRpcClient.checkIfTorrentExists(magnetLink.infoHashV1)
        } catch (e: RpcRequestError) {
            Timber.e(
                e,
                "checkIfTorrentExists: failed to check whether torrent with info hash ${magnetLink.infoHashV1} exists"
            )
            null
        }
        if (existingTorrentName != null) {
            this.magnetLinkForExistingTorrent = magnetLink
            this.existingTorrentName = existingTorrentName
            when {
                Settings.askForMergingTrackersWhenAddingExistingTorrent.get() ->
                    _addTorrentState.value =
                        AddTorrentState.AskingForMergingTrackers(existingTorrentName)

                Settings.mergeTrackersWhenAddingExistingTorrent.get() ->
                    mergeTrackersWithExistingTorrent(afterAsking = false)

                else -> _addTorrentState.value = AddTorrentState.DidNotMergeTrackers(afterAsking = false)
            }
        }
        return existingTorrentName != null
    }

    fun onMergeTrackersDialogResult(result: MergingTrackersDialogFragment.Result) {
        Timber.d("onMergeTrackersDialogResult() called with: result = $result")
        when (result) {
            is MergingTrackersDialogFragment.Result.ButtonClicked -> if (result.merge) {
                mergeTrackersWithExistingTorrent(afterAsking = true)
            } else {
                _addTorrentState.value = AddTorrentState.DidNotMergeTrackers(afterAsking = true)
            }
            is MergingTrackersDialogFragment.Result.Cancelled -> _addTorrentState.value = null
        }
    }

    private fun mergeTrackersWithExistingTorrent(afterAsking: Boolean) {
        Timber.d("mergeTrackersWithExistingTorrent() called with: afterAsking = $afterAsking")
        val magnetLink = magnetLinkForExistingTorrent
        if (magnetLink == null) {
            Timber.e("mergeTrackersWithExistingTorrent: magnetLinkForExistingTorrent must not be null")
            return
        }
        val existingTorrentName = existingTorrentName
        if (existingTorrentName == null) {
            Timber.e("mergeTrackersWithExistingTorrent: existingTorrentName must not be null")
            return
        }
        GlobalRpcClient.performBackgroundRpcRequest(R.string.merging_trackers_error) {
            addTorrentTrackers(magnetLink.infoHashV1, magnetLink.trackers)
        }
        _addTorrentState.value = AddTorrentState.MergedTrackers(existingTorrentName, afterAsking)
    }
}
