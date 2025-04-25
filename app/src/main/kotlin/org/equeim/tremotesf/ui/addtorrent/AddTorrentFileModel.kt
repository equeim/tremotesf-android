// SPDX-FileCopyrightText: 2017-2025 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package org.equeim.tremotesf.ui.addtorrent

import android.Manifest
import android.app.Application
import android.content.ContentResolver
import android.content.Context
import android.content.res.AssetFileDescriptor
import android.net.Uri
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.equeim.tremotesf.R
import org.equeim.tremotesf.rpc.GlobalRpcClient
import org.equeim.tremotesf.rpc.RpcRequestError
import org.equeim.tremotesf.rpc.RpcRequestState
import org.equeim.tremotesf.rpc.requests.FileSize
import org.equeim.tremotesf.rpc.requests.addTorrentFile
import org.equeim.tremotesf.rpc.requests.checkIfTorrentExists
import org.equeim.tremotesf.rpc.requests.serversettings.DownloadingServerSettings
import org.equeim.tremotesf.rpc.requests.torrentproperties.TorrentLimits
import org.equeim.tremotesf.rpc.requests.torrentproperties.addTorrentTrackers
import org.equeim.tremotesf.torrentfile.FileIsTooLargeException
import org.equeim.tremotesf.torrentfile.FileParseException
import org.equeim.tremotesf.torrentfile.FileReadException
import org.equeim.tremotesf.torrentfile.TorrentFileParser
import org.equeim.tremotesf.torrentfile.TorrentFilesTree
import org.equeim.tremotesf.ui.Settings
import org.equeim.tremotesf.ui.addtorrent.AddTorrentFragment.AddTorrentState
import org.equeim.tremotesf.ui.utils.RuntimePermissionHelper
import org.equeim.tremotesf.ui.utils.savedState
import timber.log.Timber


interface AddTorrentFileModel {
    enum class ParserStatus {
        None,
        Loading,
        FileIsTooLarge,
        ReadingError,
        ParsingError,
        Loaded
    }

    data class ViewUpdateData(
        val parserStatus: ParserStatus,
        val addTorrentState: AddTorrentState?,
        val downloadingSettings: RpcRequestState<DownloadingServerSettings>,
        val hasStoragePermission: Boolean,
    )

    var rememberedPagerItem: Int

    val needStoragePermission: Boolean
    val storagePermissionHelper: RuntimePermissionHelper?

    val viewUpdateData: Flow<ViewUpdateData>

    val filesTree: TorrentFilesTree
    val torrentName: String
    val renamedFiles: MutableMap<String, String>

    var shouldSetInitialLocalInputs: Boolean
    var shouldSetInitialRpcInputs: Boolean

    suspend fun getInitialDownloadDirectory(settings: DownloadingServerSettings): String
    suspend fun getFreeSpace(directory: String): FileSize?
    fun addTorrentFile(
        downloadDirectory: String,
        bandwidthPriority: TorrentLimits.BandwidthPriority,
        startDownloading: Boolean
    )
    fun onMergeTrackersDialogResult(result: MergingTrackersDialogFragment.Result)
}

class AddTorrentFileModelImpl(
    private val args: AddTorrentFileFragmentArgs,
    application: Application,
    private val savedStateHandle: SavedStateHandle,
) : BaseAddTorrentModel(application), AddTorrentFileModel {
    override var rememberedPagerItem: Int by savedState(savedStateHandle, -1)

    override val storagePermissionHelper = if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q) {
        RuntimePermissionHelper(
            Manifest.permission.READ_EXTERNAL_STORAGE,
            R.string.storage_permission_rationale_torrent
        )
    } else {
        null
    }

    override val needStoragePermission =
        args.uri.scheme == ContentResolver.SCHEME_FILE && storagePermissionHelper != null

    private val parserStatus = MutableStateFlow(AddTorrentFileModel.ParserStatus.None)

    private val addTorrentState = MutableStateFlow<AddTorrentState?>(null)

    override val viewUpdateData = combine(
        parserStatus,
        addTorrentState,
        downloadingSettings,
        storagePermissionHelper?.permissionGranted ?: flowOf(false)
    ) { parserStatus, addTorrentLinkState, rpcStatus, hasPermission ->
        AddTorrentFileModel.ViewUpdateData(
            parserStatus,
            addTorrentLinkState,
            rpcStatus,
            hasPermission
        )
    }

    private var fd: AssetFileDescriptor? = null

    override val filesTree = TorrentFilesTree(viewModelScope)
    override lateinit var torrentName: String
        private set

    override val renamedFiles = mutableMapOf<String, String>()

    private lateinit var infoHashV1: String
    private lateinit var trackers: List<Set<String>>
    private lateinit var files: List<TorrentFilesTree.FileNode>

    init {
        if (needStoragePermission) {
            viewModelScope.launch {
                checkNotNull(storagePermissionHelper).permissionGranted.first { it }
                load()
            }
        } else {
            load()
        }
    }

    override fun onCleared() {
        fd?.closeQuietly()
    }

    private fun load() {
        Timber.i("load: loading ${args.uri}")
        if (parserStatus.value == AddTorrentFileModel.ParserStatus.None) {
            parserStatus.value = AddTorrentFileModel.ParserStatus.Loading
            viewModelScope.launch {
                doLoad(args.uri, getApplication())
            }
        }
    }

    private suspend fun doLoad(uri: Uri, context: Context) = withContext(Dispatchers.IO) {
        Timber.d("Parsing torrent file from URI $uri")

        val fd = try {
            context.contentResolver.openAssetFileDescriptor(uri, "r")
        } catch (e: Exception) {
            Timber.e(e, "Failed to open file descriptor")
            parserStatus.value = AddTorrentFileModel.ParserStatus.ReadingError
            return@withContext
        }
        if (fd == null) {
            Timber.e("File descriptor is null")
            parserStatus.value = AddTorrentFileModel.ParserStatus.ReadingError
            return@withContext
        }
        var closeFd = true
        try {
            val parseResult = try {
                TorrentFileParser.parseTorrentFile(fd.fileDescriptor)
            } catch (error: FileReadException) {
                parserStatus.value = AddTorrentFileModel.ParserStatus.ReadingError
                return@withContext
            } catch (error: FileIsTooLargeException) {
                parserStatus.value = AddTorrentFileModel.ParserStatus.FileIsTooLarge
                return@withContext
            } catch (error: FileParseException) {
                parserStatus.value = AddTorrentFileModel.ParserStatus.ParsingError
                return@withContext
            }

            Timber.d("Parsed torrent file from URI $uri, its info hash is ${parseResult.infoHashV1}")
            torrentName = parseResult.name
            infoHashV1 = parseResult.infoHashV1
            trackers = parseResult.trackers

            if (checkIfTorrentExists()) {
                parserStatus.value = AddTorrentFileModel.ParserStatus.None
                return@withContext
            }

            try {
                val (rootNode, files) = TorrentFileParser.createFilesTree(parseResult)
                withContext(Dispatchers.Main) {
                    closeFd = false
                    this@AddTorrentFileModelImpl.fd = fd
                    this@AddTorrentFileModelImpl.files = files
                    filesTree.init(rootNode, savedStateHandle)
                    parserStatus.value = AddTorrentFileModel.ParserStatus.Loaded
                }
            } catch (error: FileParseException) {
                parserStatus.value = AddTorrentFileModel.ParserStatus.ParsingError
                return@withContext
            }
        } finally {
            if (closeFd) {
                fd.closeQuietly()
            }
        }
    }

    override fun addTorrentFile(
        downloadDirectory: String,
        bandwidthPriority: TorrentLimits.BandwidthPriority,
        startDownloading: Boolean
    ) {
        Timber.d(
            "addTorrentFile() called with: downloadDirectory = $downloadDirectory, bandwidthPriority = $bandwidthPriority, startDownloading = $startDownloading"
        )
        val fd = detachFd() ?: return
        val priorities = getFilePriorities()
        val renamedFiles = renamedFiles.toMap()
        viewModelScope.launch {
            addTorrentState.value = AddTorrentState.CheckingIfTorrentExists
            if (!checkIfTorrentExists()) {
                GlobalRpcClient.performBackgroundRpcRequest(R.string.add_torrent_error) {
                    addTorrentFile(
                        torrentFile = fd,
                        downloadDirectory = downloadDirectory,
                        bandwidthPriority = bandwidthPriority,
                        unwantedFiles = priorities.unwantedFiles,
                        highPriorityFiles = priorities.highPriorityFiles,
                        lowPriorityFiles = priorities.lowPriorityFiles,
                        renamedFiles = renamedFiles,
                        start = startDownloading
                    )
                }
                addTorrentState.value = AddTorrentState.AddedTorrent
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
                    addTorrentState.value =
                        AddTorrentState.AskingForMergingTrackers(torrentName)

                Settings.mergeTrackersWhenAddingExistingTorrent.get() ->
                    mergeTrackersWithExistingTorrent(afterAsking = false)

                else -> addTorrentState.value = AddTorrentState.DidNotMergeTrackers(torrentName, afterAsking = false)
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

    override fun onMergeTrackersDialogResult(result: MergingTrackersDialogFragment.Result) {
        Timber.d("onMergeTrackersDialogResult() called with: result = $result")
        if ((result as? MergingTrackersDialogFragment.Result.ButtonClicked)?.merge == true) {
            mergeTrackersWithExistingTorrent(afterAsking = true)
        } else {
            addTorrentState.value = AddTorrentState.DidNotMergeTrackers(torrentName, afterAsking = true)
        }
    }

    private fun mergeTrackersWithExistingTorrent(afterAsking: Boolean) {
        Timber.d("mergeTrackersWithExistingTorrent() called with: afterAsking = $afterAsking")
        val infoHash = this.infoHashV1
        val trackers = this.trackers
        GlobalRpcClient.performBackgroundRpcRequest(R.string.merging_trackers_error) {
            addTorrentTrackers(infoHash, trackers)
        }
        addTorrentState.value = AddTorrentState.MergedTrackers(torrentName, afterAsking)
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
