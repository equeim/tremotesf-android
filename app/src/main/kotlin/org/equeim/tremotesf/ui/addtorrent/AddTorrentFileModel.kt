// SPDX-FileCopyrightText: 2017-2025 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package org.equeim.tremotesf.ui.addtorrent

import android.app.Application
import android.content.ContentResolver
import android.content.Context
import android.content.res.AssetFileDescriptor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.os.Parcelable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.core.os.BundleCompat
import androidx.core.os.bundleOf
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.application
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.SavedStateHandleSaveableApi
import androidx.lifecycle.viewmodel.compose.saveable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.parcelize.Parcelize
import org.equeim.tremotesf.R
import org.equeim.tremotesf.rpc.GlobalRpcClient
import org.equeim.tremotesf.rpc.RpcRequestError
import org.equeim.tremotesf.rpc.RpcRequestState
import org.equeim.tremotesf.rpc.requests.addTorrentFile
import org.equeim.tremotesf.rpc.requests.checkIfTorrentsExist
import org.equeim.tremotesf.rpc.requests.torrentproperties.addTorrentTrackers
import org.equeim.tremotesf.torrentfile.FileIsTooLargeException
import org.equeim.tremotesf.torrentfile.FileParseException
import org.equeim.tremotesf.torrentfile.FileReadException
import org.equeim.tremotesf.torrentfile.TorrentFileParser
import org.equeim.tremotesf.torrentfile.TorrentFilesTree
import org.equeim.tremotesf.ui.Settings
import org.equeim.tremotesf.ui.addtorrent.AddTorrentFileModel.LoadingState
import timber.log.Timber


interface AddTorrentFileModel {
    sealed interface LoadingState {
        data object Initial : LoadingState
        data object Loading : LoadingState
        data class Loaded(val torrentName: String) : LoadingState
        enum class FileLoadingError : LoadingState {
            FileIsTooLarge,
            ReadingError,
            ParsingError,
        }

        data class InitialRpcInputsError(val error: RpcRequestError) : LoadingState
        data object Aborted : LoadingState
    }

    val loadingState: StateFlow<LoadingState>
    val needStoragePermission: Boolean
    val filesTree: TorrentFilesTree

    fun load()
    fun addTorrentFile()
    fun onMergeTrackersDialogResult(result: MergeTrackersDialogResult)
}

@OptIn(SavedStateHandleSaveableApi::class)
class AddTorrentFileModelImpl(
    private val uri: Uri,
    application: Application,
    private val savedStateHandle: SavedStateHandle,
) : BaseAddTorrentModel(savedStateHandle, application), AddTorrentFileModel {
    override val needStoragePermission: Boolean =
        uri.scheme == ContentResolver.SCHEME_FILE && Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q

    private val torrentLoadingState = MutableStateFlow<LoadingState>(LoadingState.Initial)
    override val loadingState: StateFlow<LoadingState> = combine(torrentLoadingState, initialRpcInputs) { torrentLoadingState, initialRpcInputs ->
        if (torrentLoadingState is LoadingState.Loaded) {
            when (initialRpcInputs) {
                is RpcRequestState.Error -> LoadingState.InitialRpcInputsError(initialRpcInputs.error)
                is RpcRequestState.Loading -> LoadingState.Loading
                is RpcRequestState.Loaded -> torrentLoadingState
            }
        } else {
            torrentLoadingState
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), LoadingState.Initial)

    private var fd: AssetFileDescriptor? = null

    override val filesTree: TorrentFilesTree = FilesTree()

    private val renamedFiles by savedStateHandle.saveable<MutableList<RenamedFile>> { mutableListOf() }

    @Parcelize
    private data class RenamedFile(
        val path: TorrentFilesTree.NodePath,
        val originalNamePath: String,
        val newName: String
    ) : Parcelable

    private lateinit var torrentName: String
    private lateinit var infoHashV1: String
    private lateinit var trackers: List<Set<String>>
    private lateinit var files: List<TorrentFilesTree.FileNode>

    private val _addTorrentState = mutableStateOf<AddTorrentState?>(null)
    val addTorrentState: State<AddTorrentState?> by ::_addTorrentState

    init {
        savedStateHandle.setSavedStateProvider(CHANGED_FILE_PRIORITIES_KEY) { bundleOf("" to getChangedFilePriorities()) }
    }

    override fun onCleared() {
        fd?.closeQuietly()
    }

    override fun load() {
        if (!torrentLoadingState.compareAndSet(LoadingState.Initial, LoadingState.Loading)) {
            Timber.e("load: loadingState is not Initial")
            return
        }
        viewModelScope.launch {
            Timber.i("load: loading $uri")
            torrentLoadingState.value = doLoad(uri, application)
        }
    }

    private suspend fun doLoad(uri: Uri, context: Context): LoadingState = withContext(Dispatchers.IO) {
        Timber.d("Parsing torrent file from URI $uri")

        val fd = try {
            context.contentResolver.openAssetFileDescriptor(uri, "r")
        } catch (e: Exception) {
            Timber.e(e, "Failed to open file descriptor")
            return@withContext LoadingState.FileLoadingError.ReadingError
        }
        if (fd == null) {
            Timber.e("File descriptor is null")
            return@withContext LoadingState.FileLoadingError.ReadingError
        }
        var closeFd = true
        try {
            val parseResult = try {
                TorrentFileParser.parseTorrentFile(fd.fileDescriptor)
            } catch (_: FileReadException) {
                return@withContext LoadingState.FileLoadingError.ReadingError
            } catch (_: FileIsTooLargeException) {
                return@withContext LoadingState.FileLoadingError.FileIsTooLarge
            } catch (_: FileParseException) {
                return@withContext LoadingState.FileLoadingError.ParsingError
            }

            Timber.d("Parsed torrent file from URI $uri, its info hash is ${parseResult.infoHashV1}")
            torrentName = parseResult.name
            infoHashV1 = parseResult.infoHashV1
            trackers = parseResult.trackers

            checkIfTorrentExists()
            if (checkIfTorrentExists()) {
                return@withContext LoadingState.Aborted
            }

            return@withContext try {
                val (rootNode, files) = TorrentFileParser.createFilesTree(parseResult)
                withContext(Dispatchers.Main) {
                    closeFd = false
                    this@AddTorrentFileModelImpl.fd = fd
                    this@AddTorrentFileModelImpl.files = files
                    filesTree.init(rootNode, savedStateHandle)
                    // Restore saved state
                    savedStateHandle.get<Bundle>(CHANGED_FILE_PRIORITIES_KEY)?.let { bundle ->
                        BundleCompat.getParcelable(bundle, "", ChangedFilePriorities::class.java)?.let { priorities ->
                            if (renamedFiles.isNotEmpty() ||
                                priorities.unwantedFiles.isNotEmpty() ||
                                priorities.lowPriorityFiles.isNotEmpty() ||
                                priorities.highPriorityFiles.isNotEmpty()
                            ) {
                                filesTree.restoreChangedProperties(
                                    renamedFiles = renamedFiles.map { it.path to it.newName },
                                    unwantedFiles = priorities.unwantedFiles.map(files::get),
                                    lowPriorityFiles = priorities.lowPriorityFiles.map(files::get),
                                    highPriorityFiles = priorities.highPriorityFiles.map(files::get)
                                )
                            }
                        }
                    }
                    LoadingState.Loaded(torrentName)
                }
            } catch (_: FileParseException) {
                LoadingState.FileLoadingError.ParsingError
            }
        } finally {
            if (closeFd) {
                fd.closeQuietly()
            }
        }
    }

    override fun addTorrentFile() {
        Timber.d("addTorrentFile() called")
        saveAddTorrentParameters()
        val fd = detachFd() ?: return
        val priorities = getChangedFilePriorities()
        val renamedFiles = renamedFiles.associate { it.originalNamePath to it.newName }
        viewModelScope.launch {
            _addTorrentState.value = AddTorrentState.CheckingIfTorrentExists
            if (!checkIfTorrentExists()) {
                GlobalRpcClient.performBackgroundRpcRequest(R.string.add_torrent_error) {
                    addTorrentFile(
                        torrentFile = fd,
                        downloadDirectory = downloadDirectory.value,
                        bandwidthPriority = priority.value,
                        unwantedFiles = priorities.unwantedFiles,
                        highPriorityFiles = priorities.highPriorityFiles,
                        lowPriorityFiles = priorities.lowPriorityFiles,
                        renamedFiles = renamedFiles,
                        start = startAddedTorrents.value,
                        labels = enabledLabels
                    )
                }
                _addTorrentState.value = AddTorrentState.Finished()
            }
        }
    }

    private suspend fun checkIfTorrentExists(): Boolean {
        val alreadyExists = try {
            GlobalRpcClient.checkIfTorrentsExist(listOf(infoHashV1)).singleOrNull()?.hashString == infoHashV1
        } catch (e: RpcRequestError) {
            Timber.e(
                e,
                "checkIfTorrentExists: failed to check whether torrent with info hash $infoHashV1 exists"
            )
            false
        }
        if (alreadyExists) {
            when {
                Settings.askForMergingTrackersWhenAddingExistingTorrent.get() ->
                    _addTorrentState.value =
                        AddTorrentState.AskForMergingTrackers(listOf(torrentName))

                Settings.mergeTrackersWhenAddingExistingTorrent.get() ->
                    mergeTrackersWithExistingTorrent(showMessage = true)

                else -> _addTorrentState.value = AddTorrentState.Finished(MergingTrackersMessage(merging = false, torrentNames = listOf(torrentName)))
            }
        }
        return alreadyExists
    }

    private fun detachFd(): ParcelFileDescriptor? {
        Timber.i("detachFd() called")
        val fd = this.fd
        return if (fd != null) {
            Timber.i("detachFd: detaching file descriptor")
            this.fd = null
            fd.parcelFileDescriptor
        } else {
            Timber.e("detachFd: file descriptor is already detached")
            null
        }
    }

    @Parcelize
    private data class ChangedFilePriorities(
        val unwantedFiles: List<Int>,
        val lowPriorityFiles: List<Int>,
        val highPriorityFiles: List<Int>,
    ) : Parcelable

    private fun getChangedFilePriorities(): ChangedFilePriorities {
        val unwantedFiles = mutableListOf<Int>()
        val lowPriorityFiles = mutableListOf<Int>()
        val highPriorityFiles = mutableListOf<Int>()

        for (file in files) {
            val item = file.item
            val id = item.fileId
            if (item.wantedState == TorrentFilesTree.Item.WantedState.Unwanted) {
                unwantedFiles.add(id)
            }
            when (item.priority) {
                TorrentFilesTree.Item.Priority.Low -> lowPriorityFiles.add(id)
                TorrentFilesTree.Item.Priority.High -> highPriorityFiles.add(id)
                else -> Unit
            }
        }

        return ChangedFilePriorities(
            unwantedFiles,
            lowPriorityFiles,
            highPriorityFiles
        )
    }

    override fun onMergeTrackersDialogResult(result: MergeTrackersDialogResult) {
        super.onMergeTrackersDialogResult(result)
        Timber.d("onMergeTrackersDialogResult() called with: result = $result")
        if ((result as? MergeTrackersDialogResult.ButtonClicked)?.merge == true) {
            mergeTrackersWithExistingTorrent(showMessage = false)
        } else {
            _addTorrentState.value = AddTorrentState.Finished()
        }
    }

    private fun mergeTrackersWithExistingTorrent(showMessage: Boolean) {
        Timber.d("mergeTrackersWithExistingTorrent() called with: showMessage = $showMessage")
        val infoHash = this.infoHashV1
        val trackers = this.trackers
        GlobalRpcClient.performBackgroundRpcRequest(R.string.merging_trackers_error) {
            addTorrentTrackers(infoHash, trackers)
        }
        _addTorrentState.value = AddTorrentState.Finished(
            if (showMessage) {
                MergingTrackersMessage(merging = true, torrentNames = listOf(torrentName))
            } else {
                null
            }
        )
    }

    private inner class FilesTree : TorrentFilesTree(viewModelScope) {
        override fun onFileRenamed(path: NodePath, originalNamePath: String, newName: String) {
            renamedFiles.add(RenamedFile(path, originalNamePath, newName))
        }
    }

    private companion object {
        fun AssetFileDescriptor.closeQuietly() {
            try {
                Timber.i("closeQuietly: closing file descriptor")
                close()
            } catch (e: Exception) {
                Timber.e(e, "closeQuietly: failed to close file descriptor")
            }
        }

        const val CHANGED_FILE_PRIORITIES_KEY = "changedFilePriorities"
    }
}
