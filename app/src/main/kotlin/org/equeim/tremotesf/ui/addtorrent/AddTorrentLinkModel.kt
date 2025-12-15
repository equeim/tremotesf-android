// SPDX-FileCopyrightText: 2017-2025 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package org.equeim.tremotesf.ui.addtorrent

import android.app.Application
import android.content.ClipboardManager
import android.net.Uri
import android.os.Parcelable
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
import kotlinx.parcelize.Parcelize
import org.equeim.tremotesf.BuildConfig
import org.equeim.tremotesf.R
import org.equeim.tremotesf.rpc.GlobalRpcClient
import org.equeim.tremotesf.rpc.RpcRequestError
import org.equeim.tremotesf.rpc.requests.addTorrentLink
import org.equeim.tremotesf.rpc.requests.checkIfTorrentsExist
import org.equeim.tremotesf.rpc.requests.torrentproperties.addTorrentTrackers
import org.equeim.tremotesf.torrentfile.MagnetLink
import org.equeim.tremotesf.torrentfile.parseMagnetLink
import org.equeim.tremotesf.ui.Settings
import timber.log.Timber
import java.util.concurrent.atomic.AtomicReference

@OptIn(SavedStateHandleSaveableApi::class)
class AddTorrentLinkModel(
    private val initialUris: List<Uri>,
    savedStateHandle: SavedStateHandle,
    application: Application
) :
    BaseAddTorrentModel(savedStateHandle, application), DragAndDropTarget {

    val torrentLinksText by savedStateHandle.saveable<MutableState<String>> { mutableStateOf("") }

    private val _addTorrentState by savedStateHandle.saveable<MutableState<AddTorrentState?>> { mutableStateOf(null) }
    val addTorrentState: State<AddTorrentState?> by ::_addTorrentState

    val showMergingTrackersMessage: MutableState<MergingTrackersMessage?> = mutableStateOf(null)

    @Parcelize
    private class AskingForMergingTrackersInternalState(
        val magnetLinks: Set<MagnetLink>,
        val askedAfterCheckingInitialState: Boolean
    ) : Parcelable

    private var askingMergingTrackersState: AskingForMergingTrackersInternalState? by savedStateHandle.saveable {
        mutableStateOf(null)
    }

    private var checkingIfTorrentsExistForInitialLinks: Boolean by savedStateHandle.saveable { mutableStateOf(false) }
    private val checkingIfTorrentsExistForInitialLinksJob = AtomicReference<Job>(null)

    init {
        // Restart coroutines after restoring state
        when {
            _addTorrentState.value is AddTorrentState.CheckingIfTorrentExists -> addTorrent()
            checkingIfTorrentsExistForInitialLinks -> checkIfTorrentsExistForInitialLinks()
        }
    }

    override suspend fun setInitialState(initialRpcInputs: InitialRpcInputs) {
        if (!alreadySetInitialState) {
            torrentLinksText.value = getInitialTorrentLinks().joinToString("\n")
            checkIfTorrentsExistForInitialLinks()
        }
        super.setInitialState(initialRpcInputs)
    }

    suspend fun getInitialTorrentLinks(): List<Uri> {
        if (initialUris.isNotEmpty()) return initialUris
        if (!Settings.fillTorrentLinkFromClipboard.get()) {
            Timber.d("Filling torrent link from clipboard is disabled")
            return emptyList()
        }
        Timber.d("Filling torrent link from clipboard")
        val clipboardManager = getApplication<Application>().getSystemService<ClipboardManager>()
        if (clipboardManager == null) {
            Timber.e("ClipboardManager is null")
            return emptyList()
        }
        if (!clipboardManager.hasPrimaryClip()) {
            Timber.d("Clipboard is empty")
            return emptyList()
        }
        if (!TORRENT_LINK_MIME_TYPES.any { clipboardManager.primaryClipDescription?.hasMimeType(it) == true }) {
            Timber.d("Clipboard content has unsupported MIME type")
            return emptyList()
        }
        return clipboardManager
            .primaryClip
            ?.also {
                Timber.d("primaryClip = $it")
            }
            ?.getTorrentUris(getApplication())
            ?.mapNotNull { it.takeIf { it.type == TorrentUri.Type.Link }?.uri }
            .orEmpty()
            .also {
                if (BuildConfig.DEBUG) {
                    Timber.d("Torrent links from clipboard: $it")
                }
            }
    }

    fun addTorrent() {
        Timber.d("addTorrent() called")
        viewModelScope.launch {
            saveAddTorrentParameters()

            checkingIfTorrentsExistForInitialLinksJob.get()?.cancel()
            showMergingTrackersMessage.value = null

            _addTorrentState.value = AddTorrentState.CheckingIfTorrentExists
            var links = extractTorrentLinksFromText()
            val existingTorrents = getExistingTorrents(links.parseMagnetLinks())
            var mergingTrackersMessage: MergingTrackersMessage? = null
            if (existingTorrents.isNotEmpty()) {
                val magnetLinks = existingTorrents.keys
                val torrentNames = existingTorrents.values.toList()
                if (Settings.askForMergingTrackersWhenAddingExistingTorrent.get()) {
                    askForMergingTrackers(
                        magnetLinks = magnetLinks,
                        torrentNames = torrentNames,
                        askedAfterCheckingInitialState = false
                    )
                    return@launch
                }
                links = links - magnetLinks.links()
                val mergeTrackers = Settings.mergeTrackersWhenAddingExistingTorrent.get()
                mergingTrackersMessage =
                    MergingTrackersMessage(merging = mergeTrackers, torrentNames = torrentNames)
                if (mergeTrackers) {
                    Timber.d("Merging trackers")
                    mergeTrackers(magnetLinks)
                } else {
                    Timber.d("Not merging trackers")
                }
            }
            addTorrentLinks(links)
            _addTorrentState.value = AddTorrentState.Finished(mergingTrackersMessage)
        }
    }

    private fun addTorrentLinks(links: Set<String>) {
        for (link in links) {
            GlobalRpcClient.performBackgroundRpcRequest(R.string.add_torrent_error) {
                addTorrentLink(
                    url = link,
                    downloadDirectory = downloadDirectory.value,
                    bandwidthPriority = priority.value,
                    start = startAddedTorrents.value,
                    labels = enabledLabels
                )
            }
        }
    }

    private fun mergeTrackers(magnetLinks: Set<MagnetLink>) {
        for (magnetLink in magnetLinks) {
            GlobalRpcClient.performBackgroundRpcRequest(R.string.merging_trackers_error) {
                addTorrentTrackers(magnetLink.infoHashV1, magnetLink.trackers)
            }
        }
    }

    private fun checkIfTorrentsExistForInitialLinks() {
        Timber.d("checkIfTorrentsExistForInitialLinks() called")
        if (checkingIfTorrentsExistForInitialLinksJob.get() != null) {
            return
        }
        viewModelScope.launch {
            val existingTorrents = getExistingTorrents(extractTorrentLinksFromText().parseMagnetLinks())
            if (existingTorrents.isEmpty()) {
                return@launch
            }
            val magnetLinks = existingTorrents.keys
            val torrentNames = existingTorrents.values.toList()
            if (Settings.askForMergingTrackersWhenAddingExistingTorrent.get()) {
                askForMergingTrackers(
                    magnetLinks = magnetLinks,
                    torrentNames = torrentNames,
                    askedAfterCheckingInitialState = true
                )
                return@launch
            }
            completeCheckingIfTorrentsExistForInitialLinks(
                magnetLinks = magnetLinks,
                torrentNames = torrentNames,
                mergeTrackers = Settings.mergeTrackersWhenAddingExistingTorrent.get()
            )
        }.also { job ->
            if (job.isActive) {
                checkingIfTorrentsExistForInitialLinksJob.set(job)
                checkingIfTorrentsExistForInitialLinks = true
                job.invokeOnCompletion {
                    checkingIfTorrentsExistForInitialLinksJob.compareAndSet(job, null)
                    checkingIfTorrentsExistForInitialLinks = false
                }
            }
        }
    }

    private fun completeCheckingIfTorrentsExistForInitialLinks(magnetLinks: Set<MagnetLink>, torrentNames: List<String>, mergeTrackers: Boolean) {
        val mergingTrackersMessage =
            MergingTrackersMessage(merging = mergeTrackers, torrentNames = torrentNames)
        if (mergeTrackers) {
            Timber.d("Merging trackers")
            mergeTrackers(magnetLinks)
        } else {
            Timber.d("Not merging trackers")
        }
        val newText = removeTorrentLinksForExistingTorrents(magnetLinks)
        if (newText.isBlank()) {
            _addTorrentState.value = AddTorrentState.Finished(mergingTrackersMessage)
        } else {
            _addTorrentState.value = null
            torrentLinksText.value = newText
            showMergingTrackersMessage.value = mergingTrackersMessage
        }
    }

    private suspend fun getExistingTorrents(magnetLinks: List<MagnetLink>): Map<MagnetLink, String> {
        if (magnetLinks.isEmpty()) return emptyMap()
        val existingTorrents = try {
            GlobalRpcClient.checkIfTorrentsExist(magnetLinks.map { it.infoHashV1 })
        } catch (e: RpcRequestError) {
            Timber.e(e, "Failed to check whether torrents exist")
            return emptyMap()
        }
        Timber.d("Existing torrents:\n ${existingTorrents.joinToString("\n ")}")
        if (existingTorrents.isEmpty()) return emptyMap()
        val magnetLinksToNames = mutableMapOf<MagnetLink, String>()
        for (torrent in existingTorrents) {
            magnetLinks.find { it.infoHashV1 == torrent.hashString }?.let {
                magnetLinksToNames.put(it, torrent.name)
            }
        }
        return magnetLinksToNames
    }

    private fun askForMergingTrackers(
        magnetLinks: Set<MagnetLink>,
        torrentNames: List<String>,
        askedAfterCheckingInitialState: Boolean
    ) {
        Timber.d("Ask for merging trackers")
        _addTorrentState.value = AddTorrentState.AskForMergingTrackers(torrentNames)
        askingMergingTrackersState = AskingForMergingTrackersInternalState(
            magnetLinks = magnetLinks,
            askedAfterCheckingInitialState = askedAfterCheckingInitialState
        )
    }

    override fun onMergeTrackersDialogResult(result: MergeTrackersDialogResult) {
        Timber.d("onMergeTrackersDialogResult() called with: result = $result")
        super.onMergeTrackersDialogResult(result)
        val state = _addTorrentState.value
        if (state !is AddTorrentState.AskForMergingTrackers) {
            Timber.e("onMergeTrackersDialogResult: addTorrentState is not AskForMergingTrackers")
            return
        }
        val internalState = askingMergingTrackersState
        if (internalState == null) {
            Timber.e("onMergeTrackersDialogResult: askingMergingTrackersState is null")
            return
        }
        askingMergingTrackersState = null
        if (result !is MergeTrackersDialogResult.ButtonClicked) {
            _addTorrentState.value = null
            return
        }
        if (internalState.askedAfterCheckingInitialState) {
            completeCheckingIfTorrentsExistForInitialLinks(
                magnetLinks = internalState.magnetLinks,
                torrentNames = state.torrentNames,
                mergeTrackers = result.merge
            )
        } else {
            if (result.merge) {
                Timber.d("Merging trackers")
                mergeTrackers(internalState.magnetLinks)
            } else {
                Timber.d("Not merging trackers")
            }
            addTorrentLinks(extractTorrentLinksFromText() - internalState.magnetLinks.links())
            _addTorrentState.value = AddTorrentState.Finished(
                MergingTrackersMessage(
                    merging = result.merge,
                    torrentNames = state.torrentNames
                )
            )
        }
    }

    fun shouldStartDragAndDrop(event: DragAndDropEvent): Boolean {
        val mimeTypes = event.mimeTypes()
        Timber.i("Drag start event mime types = $mimeTypes")
        return TORRENT_LINK_MIME_TYPES.any(mimeTypes::contains)
    }

    override fun onDrop(event: DragAndDropEvent): Boolean {
        val clipData = event.toAndroidDragEvent().clipData
        val torrentLinks = clipData.getTorrentUris(getApplication())
            .mapNotNull { it.takeIf { it.type == TorrentUri.Type.Link }?.uri }
        if (torrentLinks.isNotEmpty()) {
            this.torrentLinksText.value = torrentLinks.joinToString("\n")
            return true
        }
        return false
    }

    private fun extractTorrentLinksFromText(): Set<String> = torrentLinksText.value.lineSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .toSet()
        .also {
            Timber.d("Torrents links in text:\n ${it.joinToString("\n ")}")
        }

    private fun removeTorrentLinksForExistingTorrents(existingTorrentsMagnetLinks: Iterable<MagnetLink>): String {
        val text = StringBuilder(torrentLinksText.value)
        for (magnetLink in existingTorrentsMagnetLinks) {
            val link = magnetLink.uri.toString()
            val index = text.indexOf(link)
            if (index != -1) {
                Timber.d("Removing link $link from text")
                text.delete(index, index + link.length)
                if (text.length > index && text[index] == '\n') {
                    text.deleteCharAt(index)
                }
            }
        }
        return text.toString().also {
            Timber.d("New text: $it")
        }
    }

    private companion object {
        private fun Set<String>.parseMagnetLinks(): List<MagnetLink> = mapNotNull {
            try {
                parseMagnetLink(it.toUri())
            } catch (e: IllegalArgumentException) {
                Timber.d(e, "Failed to parse '$it' as a magnet link")
                null
            }
        }.also {
            Timber.d("Magnet links from text:\n ${it.joinToString("\n ")}")
        }

        private fun Set<MagnetLink>.links(): Set<String> = mapTo(mutableSetOf()) { it.uri.toString() }
    }
}
