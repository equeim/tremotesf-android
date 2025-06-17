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
import android.os.ParcelFileDescriptor
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.SavedStateHandleSaveableApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.equeim.tremotesf.R
import org.equeim.tremotesf.rpc.GlobalRpcClient
import org.equeim.tremotesf.rpc.RpcRequestError
import org.equeim.tremotesf.rpc.requests.addTorrentFile
import org.equeim.tremotesf.rpc.requests.checkIfTorrentExists
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
        data object Aborted: LoadingState
    }

    val loadingState: State<LoadingState>
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

    override val loadingState = mutableStateOf<LoadingState>(LoadingState.Initial)

    private var fd: AssetFileDescriptor? = null

    override val filesTree: TorrentFilesTree = FilesTree()

    private val renamedFiles = mutableMapOf<String, String>()

    private lateinit var torrentName: String
    private lateinit var infoHashV1: String
    private lateinit var trackers: List<Set<String>>
    private lateinit var files: List<TorrentFilesTree.FileNode>

    private val _addTorrentState = mutableStateOf<AddTorrentState?>(null)
    val addTorrentState: State<AddTorrentState?> by ::_addTorrentState

    override fun onCleared() {
        fd?.closeQuietly()
    }

    override fun load() {
        if (loadingState.value == LoadingState.Initial) {
            Timber.i("load: loading $uri")
            loadingState.value = LoadingState.Loading
            viewModelScope.launch {
                doLoad(uri, getApplication())
            }
        } else {
            Timber.e("load: loadingState is not Initial")
        }
    }

    private suspend fun doLoad(uri: Uri, context: Context) = withContext(Dispatchers.IO) {
        Timber.d("Parsing torrent file from URI $uri")

        val fd = try {
            context.contentResolver.openAssetFileDescriptor(uri, "r")
        } catch (e: Exception) {
            Timber.e(e, "Failed to open file descriptor")
            loadingState.value = LoadingState.FileLoadingError.ReadingError
            return@withContext
        }
        if (fd == null) {
            Timber.e("File descriptor is null")
            loadingState.value = LoadingState.FileLoadingError.ReadingError
            return@withContext
        }
        var closeFd = true
        try {
            val parseResult = try {
                TorrentFileParser.parseTorrentFile(fd.fileDescriptor)
            } catch (_: FileReadException) {
                loadingState.value = LoadingState.FileLoadingError.ReadingError
                return@withContext
            } catch (_: FileIsTooLargeException) {
                loadingState.value = LoadingState.FileLoadingError.FileIsTooLarge
                return@withContext
            } catch (_: FileParseException) {
                loadingState.value = LoadingState.FileLoadingError.ParsingError
                return@withContext
            }

            Timber.d("Parsed torrent file from URI $uri, its info hash is ${parseResult.infoHashV1}")
            torrentName = parseResult.name
            infoHashV1 = parseResult.infoHashV1
            trackers = parseResult.trackers

            if (checkIfTorrentExists()) {
                loadingState.value = LoadingState.Aborted
                return@withContext
            }

            try {
                val (rootNode, files) = TorrentFileParser.createFilesTree(parseResult)
                withContext(Dispatchers.Main) {
                    closeFd = false
                    this@AddTorrentFileModelImpl.fd = fd
                    this@AddTorrentFileModelImpl.files = files
                    filesTree.init(rootNode, savedStateHandle)
                    loadingState.value = LoadingState.Loaded(torrentName)
                }
            } catch (_: FileParseException) {
                loadingState.value = LoadingState.FileLoadingError.ParsingError
                return@withContext
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
        val priorities = getFilePriorities()
        val renamedFiles = renamedFiles.toMap()
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
                _addTorrentState.value = AddTorrentState.AddedTorrent
            }
        }
    }

    private suspend fun checkIfTorrentExists(): Boolean {
        val alreadyExists = try {
            GlobalRpcClient.checkIfTorrentExists(infoHashV1) != null
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
                        AddTorrentState.AskForMergingTrackers(torrentName)

                Settings.mergeTrackersWhenAddingExistingTorrent.get() ->
                    mergeTrackersWithExistingTorrent(afterAsking = false)

                else -> _addTorrentState.value = AddTorrentState.DidNotMergeTrackers(torrentName, showMessage = true)
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

    data class FilePriorities(
        val unwantedFiles: List<Int>,
        val lowPriorityFiles: List<Int>,
        val highPriorityFiles: List<Int>,
    )

    private fun getFilePriorities(): FilePriorities {
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

        return FilePriorities(
            unwantedFiles,
            lowPriorityFiles,
            highPriorityFiles
        )
    }

    override fun onMergeTrackersDialogResult(result: MergeTrackersDialogResult) {
        super.onMergeTrackersDialogResult(result)
        Timber.d("onMergeTrackersDialogResult() called with: result = $result")
        if ((result as? MergeTrackersDialogResult.ButtonClicked)?.merge == true) {
            mergeTrackersWithExistingTorrent(afterAsking = true)
        } else {
            _addTorrentState.value = AddTorrentState.DidNotMergeTrackers(torrentName, showMessage = false)
        }
    }

    private fun mergeTrackersWithExistingTorrent(afterAsking: Boolean) {
        Timber.d("mergeTrackersWithExistingTorrent() called with: afterAsking = $afterAsking")
        val infoHash = this.infoHashV1
        val trackers = this.trackers
        GlobalRpcClient.performBackgroundRpcRequest(R.string.merging_trackers_error) {
            addTorrentTrackers(infoHash, trackers)
        }
        _addTorrentState.value = AddTorrentState.MergedTrackers(torrentName, afterAsking)
    }

    private inner class FilesTree : TorrentFilesTree(viewModelScope) {
        override fun onFileRenamed(path: String, newName: String) {
            renamedFiles[path] = newName
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
    }
}
