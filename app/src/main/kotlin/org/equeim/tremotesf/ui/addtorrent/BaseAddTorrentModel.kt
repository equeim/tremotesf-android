// SPDX-FileCopyrightText: 2017-2025 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package org.equeim.tremotesf.ui.addtorrent

import android.app.Application
import androidx.annotation.CallSuper
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.serialization.saved
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.SavedStateHandleSaveableApi
import androidx.lifecycle.viewmodel.compose.saveable
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.equeim.tremotesf.common.AlphanumericComparator
import org.equeim.tremotesf.rpc.GlobalRpcClient
import org.equeim.tremotesf.rpc.GlobalServers
import org.equeim.tremotesf.rpc.RpcRequestError
import org.equeim.tremotesf.rpc.RpcRequestState
import org.equeim.tremotesf.rpc.normalizePath
import org.equeim.tremotesf.rpc.performRecoveringRequestIntoStateFlow
import org.equeim.tremotesf.rpc.requests.FileSize
import org.equeim.tremotesf.rpc.requests.NormalizedRpcPath
import org.equeim.tremotesf.rpc.requests.getFreeSpaceInDirectory
import org.equeim.tremotesf.rpc.requests.getTorrentsDownloadDirectories
import org.equeim.tremotesf.rpc.requests.getTorrentsLabels
import org.equeim.tremotesf.rpc.requests.serversettings.getDownloadingServerSettings
import org.equeim.tremotesf.rpc.requests.torrentproperties.TorrentLimits
import org.equeim.tremotesf.rpc.toNativeSeparators
import org.equeim.tremotesf.ui.Settings
import org.equeim.tremotesf.ui.components.DownloadDirectoryItem
import org.equeim.tremotesf.ui.components.getInitialAllDownloadDirectories
import org.equeim.tremotesf.ui.components.updateAllDownloadDirectories
import org.equeim.tremotesf.ui.utils.SnapshotStateListSaver
import org.equeim.tremotesf.ui.utils.localeChangedEvents
import timber.log.Timber

@OptIn(SavedStateHandleSaveableApi::class)
abstract class BaseAddTorrentModel(
    savedStateHandle: SavedStateHandle,
    application: Application
) : AndroidViewModel(application) {
    protected data class InitialRpcInputs(
        val downloadDirectory: NormalizedRpcPath,
        val torrentsDownloadDirectories: Set<NormalizedRpcPath>,
        val startAddedTorrents: Boolean,
        val allLabels: Set<String>,
    )

    val initialRpcInputs: StateFlow<RpcRequestState<Any>> =
        GlobalRpcClient.performRecoveringRequestIntoStateFlow(viewModelScope) {
            coroutineScope {
                val settings = async { getDownloadingServerSettings() }
                val torrentsDownloadDirectories = async { getTorrentsDownloadDirectories() }
                val labels = async { getTorrentsLabels() }
                setInitialState(
                    InitialRpcInputs(
                        downloadDirectory = settings.await().downloadDirectory,
                        torrentsDownloadDirectories = torrentsDownloadDirectories.await(),
                        startAddedTorrents = settings.await().startAddedTorrents,
                        allLabels = labels.await()
                    )
                )
            }
        }

    val downloadDirectory by savedStateHandle.saveable<MutableState<String>> { mutableStateOf("") }
    val allDownloadDirectories by savedStateHandle.saveable<SnapshotStateList<DownloadDirectoryItem>>(
        saver = SnapshotStateListSaver()
    ) { SnapshotStateList() }


    sealed interface DownloadDirectoryFreeSpace {
        data class FreeSpace(val size: FileSize) : DownloadDirectoryFreeSpace
        data object Error : DownloadDirectoryFreeSpace
    }

    val downloadDirectoryFreeSpace: StateFlow<DownloadDirectoryFreeSpace?> = snapshotFlow { downloadDirectory.value }
        .map { if (it.isNotBlank()) getFreeSpace(it) else null }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val startAddedTorrents by savedStateHandle.saveable<MutableState<Boolean>> { mutableStateOf(false) }

    val priority by savedStateHandle.saveable<MutableState<TorrentLimits.BandwidthPriority>> {
        mutableStateOf(TorrentLimits.BandwidthPriority.Normal)
    }

    val enabledLabels by savedStateHandle.saveable<SnapshotStateList<String>>(
        saver = SnapshotStateListSaver()
    ) { SnapshotStateList() }

    private val _allLabels = mutableStateOf<List<String>>(emptyList())
    val allLabels: State<List<String>> by ::_allLabels

    val shouldShowLabels: StateFlow<Boolean> = GlobalRpcClient.serverCapabilitiesFlow.map {
        it?.supportsLabels == true
    }.stateIn(viewModelScope, SharingStarted.Eagerly, true)

    protected var alreadySetInitialState: Boolean by savedStateHandle.saved { false }

    private var comparator = AlphanumericComparator()

    init {
        viewModelScope.launch {
            application.localeChangedEvents().collect {
                comparator = AlphanumericComparator()
                if (alreadySetInitialState) {
                    allDownloadDirectories.sortWith(compareBy(comparator, DownloadDirectoryItem::directory))
                    _allLabels.value = _allLabels.value.sortedWith(comparator)
                }
            }
        }
    }

    @CallSuper
    protected open suspend fun setInitialState(initialRpcInputs: InitialRpcInputs) {
        if (!alreadySetInitialState) {
            enabledLabels.sort()
            downloadDirectory.value = getInitialDownloadDirectory(initialRpcInputs.downloadDirectory)
            allDownloadDirectories.addAll(
                getInitialAllDownloadDirectories(
                    downloadDirectoryFromServerSettings = initialRpcInputs.downloadDirectory,
                    torrentsDownloadDirectories = initialRpcInputs.torrentsDownloadDirectories,
                    comparator = comparator
                )
            )
            startAddedTorrents.value = getInitialStartAfterAdding(initialRpcInputs.startAddedTorrents)
            priority.value = getInitialPriority()
            enabledLabels.addAll(getInitialLabels())
        } else {
            val updated = updateAllDownloadDirectories(
                restoredAllDownloadDirectories = allDownloadDirectories,
                downloadDirectoryFromServerSettings = initialRpcInputs.downloadDirectory,
                torrentsDownloadDirectories = initialRpcInputs.torrentsDownloadDirectories,
                comparator = comparator
            )
            allDownloadDirectories.clear()
            allDownloadDirectories.addAll(updated)
        }
        _allLabels.value = initialRpcInputs.allLabels.sortedWith(comparator)
        alreadySetInitialState = true
    }

    private suspend fun getInitialDownloadDirectory(downloadDirectoryFromServerSettings: NormalizedRpcPath): String {
        return if (Settings.rememberAddTorrentParameters.get()) {
            GlobalServers.serversState.value.currentServer
                ?.lastDownloadDirectory
                ?.takeIf { it.isNotEmpty() }
                ?.normalizePath(GlobalRpcClient.serverCapabilities)
                ?: downloadDirectoryFromServerSettings
        } else {
            downloadDirectoryFromServerSettings
        }.toNativeSeparators()
    }

    private suspend fun getInitialStartAfterAdding(startAfterAddingFromServerSettings: Boolean): Boolean =
        if (Settings.rememberAddTorrentParameters.get()) {
            when (Settings.lastAddTorrentStartAfterAdding.get()) {
                Settings.StartTorrentAfterAdding.Start -> true
                Settings.StartTorrentAfterAdding.DontStart -> false
                Settings.StartTorrentAfterAdding.Unknown -> startAfterAddingFromServerSettings
            }
        } else {
            startAfterAddingFromServerSettings
        }

    private suspend fun getInitialPriority(): TorrentLimits.BandwidthPriority =
        if (Settings.rememberAddTorrentParameters.get()) {
            Settings.lastAddTorrentPriority.get()
        } else {
            TorrentLimits.BandwidthPriority.Normal
        }

    private suspend fun getInitialLabels(): List<String> = if (Settings.rememberAddTorrentParameters.get()) {
        Settings.lastAddTorrentLabels.get().sortedWith(comparator)
    } else {
        emptyList()
    }

    private suspend fun getFreeSpace(directory: String): DownloadDirectoryFreeSpace = try {
        DownloadDirectoryFreeSpace.FreeSpace(GlobalRpcClient.getFreeSpaceInDirectory(directory))
    } catch (e: RpcRequestError) {
        Timber.e(e, "Failed to get free space for directory $directory")
        DownloadDirectoryFreeSpace.Error
    }

    @CallSuper
    open fun onMergeTrackersDialogResult(result: MergeTrackersDialogResult) {
        if ((result as? MergeTrackersDialogResult.ButtonClicked)?.doNotAskAgain == true) {
            @OptIn(DelicateCoroutinesApi::class)
            GlobalScope.launch {
                Settings.askForMergingTrackersWhenAddingExistingTorrent.set(false)
                Settings.mergeTrackersWhenAddingExistingTorrent.set(result.merge)
            }
        }
    }

    protected fun saveAddTorrentParameters() {
        saveLastDownloadDirectories()
        @OptIn(DelicateCoroutinesApi::class)
        GlobalScope.launch {
            Settings.lastAddTorrentStartAfterAdding.set(
                if (startAddedTorrents.value) {
                    Settings.StartTorrentAfterAdding.Start
                } else {
                    Settings.StartTorrentAfterAdding.DontStart
                }
            )
            Settings.lastAddTorrentPriority.set(priority.value)
            Settings.lastAddTorrentLabels.set(enabledLabels.toSet())
        }
    }

    private fun saveLastDownloadDirectories() {
        val serverCapabilities = GlobalRpcClient.serverCapabilities
        val directories = allDownloadDirectories.mapTo(ArrayList(allDownloadDirectories.size + 1)) {
            it.directory.normalizePath(serverCapabilities).value
        }
        val normalizedDownloadDirectory = downloadDirectory.value.normalizePath(serverCapabilities).value
        if (!directories.contains(normalizedDownloadDirectory)) {
            directories.add(normalizedDownloadDirectory)
        }
        val currentServer = GlobalServers.serversState.value.currentServer
        if (currentServer != null &&
            (currentServer.lastDownloadDirectories != directories
                    || currentServer.lastDownloadDirectory != normalizedDownloadDirectory)
        ) {
            GlobalServers.addOrReplaceServer(
                currentServer.copy(
                    lastDownloadDirectories = directories,
                    lastDownloadDirectory = normalizedDownloadDirectory
                )
            )
        }
    }
}
