// SPDX-FileCopyrightText: 2017-2025 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package org.equeim.tremotesf.ui.addtorrent

import android.app.Application
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.net.Uri
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.mimeTypes
import androidx.compose.ui.draganddrop.toAndroidDragEvent
import androidx.core.content.getSystemService
import androidx.core.net.toUri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.SavedStateHandleSaveableApi
import androidx.lifecycle.viewmodel.compose.saveable
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.equeim.tremotesf.BuildConfig
import org.equeim.tremotesf.R
import org.equeim.tremotesf.rpc.GlobalRpcClient
import org.equeim.tremotesf.rpc.RpcRequestError
import org.equeim.tremotesf.rpc.requests.addTorrentLink
import org.equeim.tremotesf.rpc.requests.checkIfTorrentExists
import org.equeim.tremotesf.rpc.requests.torrentproperties.addTorrentTrackers
import org.equeim.tremotesf.torrentfile.MagnetLink
import org.equeim.tremotesf.torrentfile.parseMagnetLink
import org.equeim.tremotesf.ui.Settings
import timber.log.Timber
import java.util.concurrent.atomic.AtomicReference

@OptIn(SavedStateHandleSaveableApi::class)
class AddTorrentLinkModel(private val initialUri: Uri?, savedStateHandle: SavedStateHandle, application: Application) :
    BaseAddTorrentModel(savedStateHandle, application), DragAndDropTarget {

    val torrentLink by savedStateHandle.saveable<MutableState<String>> { mutableStateOf("") }

    private val _addTorrentState by savedStateHandle.saveable<MutableState<AddTorrentState?>> { mutableStateOf(null) }
    val addTorrentState: State<AddTorrentState?> by ::_addTorrentState

    private val checkingIfTorrentExistsForInitialLink = AtomicReference<Job>(null)

    init {
        // Restart the coroutine after restoring state
        if (_addTorrentState.value != null) {
            _addTorrentState.value = null
            addTorrentLink()
        }
    }

    override suspend fun setInitialState(initialRpcInputs: InitialRpcInputs) {
        if (!alreadySetInitialState) {
            torrentLink.value = getInitialTorrentLink().orEmpty()
            checkIfTorrentExistsForInitialLink()
        }
        super.setInitialState(initialRpcInputs)
    }

    suspend fun getInitialTorrentLink(): String? {
        initialUri?.let { return it.toString() }
        if (!Settings.fillTorrentLinkFromClipboard.get()) {
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

    fun addTorrentLink() {
        Timber.d("addTorrentLink() called")
        saveAddTorrentParameters()
        checkingIfTorrentExistsForInitialLink.get()?.cancel()
        val magnetLink = try {
            parseMagnetLink(torrentLink.value.toUri())
        } catch (e: IllegalArgumentException) {
            Timber.d(e, "Failed to parse '$torrentLink' as a magnet link")
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
                addTorrentLink(
                    url = torrentLink.value,
                    downloadDirectory = downloadDirectory.value,
                    bandwidthPriority = priority.value,
                    start = startAddedTorrents.value,
                    labels = enabledLabels
                )
            }
            _addTorrentState.value = AddTorrentState.AddedTorrent
        }
    }

    private fun checkIfTorrentExistsForInitialLink() {
        Timber.d("checkIfTorrentExistsForInitialLink() called")
        if (checkingIfTorrentExistsForInitialLink.get() != null) {
            return
        }
        if (torrentLink.value.isEmpty()) {
            Timber.d("checkIfTorrentExistsForInitialLink: no initial torrent link")
            return
        }
        val magnetLink = try {
            parseMagnetLink(torrentLink.value.toUri())
        } catch (e: IllegalArgumentException) {
            Timber.d(e, "checkIfTorrentExistsForInitialLink: failed to parse '${torrentLink.value}' as magnet link")
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
            when {
                Settings.askForMergingTrackersWhenAddingExistingTorrent.get() ->
                    _addTorrentState.value =
                        AddTorrentState.AskForMergingTrackers(existingTorrentName)

                Settings.mergeTrackersWhenAddingExistingTorrent.get() ->
                    mergeTrackersWithExistingTorrent(
                        magnetLink = magnetLink,
                        torrentName = existingTorrentName,
                        showMessage = true
                    )

                else -> _addTorrentState.value =
                    AddTorrentState.DidNotMergeTrackers(torrentName = existingTorrentName, showMessage = true)
            }
        }
        return existingTorrentName != null
    }

    override fun onMergeTrackersDialogResult(result: MergeTrackersDialogResult) {
        Timber.d("onMergeTrackersDialogResult() called with: result = $result")
        super.onMergeTrackersDialogResult(result)
        val torrentName = (_addTorrentState.value as? AddTorrentState.AskForMergingTrackers)?.torrentName
        if (torrentName == null) {
            Timber.e("onMergeTrackersDialogResult: addTorrentState is not AskForMergingTrackers")
            return
        }
        val magnetLink = try {
            parseMagnetLink(torrentLink.value.toUri())
        } catch (e: IllegalArgumentException) {
            Timber.d(e, "Failed to parse '$torrentLink' as a magnet link")
            return
        }
        when (result) {
            is MergeTrackersDialogResult.ButtonClicked -> {
                if (result.merge) {
                    mergeTrackersWithExistingTorrent(
                        magnetLink = magnetLink,
                        torrentName = torrentName,
                        showMessage = false
                    )
                } else {
                    _addTorrentState.value =
                        AddTorrentState.DidNotMergeTrackers(torrentName = torrentName, showMessage = false)
                }
            }

            is MergeTrackersDialogResult.Cancelled -> _addTorrentState.value = null
        }
    }

    private fun mergeTrackersWithExistingTorrent(
        magnetLink: MagnetLink,
        torrentName: String,
        showMessage: Boolean
    ) {
        Timber.d(
            "mergeTrackersWithExistingTorrent() called with: magnetLink = $magnetLink, torrentName = $torrentName, showMessage = $showMessage"
        )
        GlobalRpcClient.performBackgroundRpcRequest(R.string.merging_trackers_error) {
            addTorrentTrackers(magnetLink.infoHashV1, magnetLink.trackers)
        }
        _addTorrentState.value = AddTorrentState.MergedTrackers(torrentName, showMessage = showMessage)
    }

    fun shouldStartDragAndDrop(event: DragAndDropEvent): Boolean {
        val mimeTypes = event.mimeTypes()
        Timber.i("Drag start event mime types = $mimeTypes")
        return TORRENT_LINK_MIME_TYPES.any(mimeTypes::contains)
    }

    override fun onDrop(event: DragAndDropEvent): Boolean {
        val clipData = event.toAndroidDragEvent().clipData
        val torrentLink = clipData.getTorrentUri(getApplication())
            ?.takeIf { it.type == TorrentUri.Type.Link }
            ?.uri
            ?.toString()
        if (torrentLink != null) {
            this.torrentLink.value = torrentLink
            return true
        }
        return false
    }
}
