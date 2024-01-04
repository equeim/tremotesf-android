// SPDX-FileCopyrightText: 2017-2023 Alexey Rochev <equeim@gmail.com>
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
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.equeim.tremotesf.R
import org.equeim.tremotesf.torrentfile.FileIsTooLargeException
import org.equeim.tremotesf.torrentfile.FileParseException
import org.equeim.tremotesf.torrentfile.FileReadException
import org.equeim.tremotesf.torrentfile.TorrentFileParser
import org.equeim.tremotesf.torrentfile.TorrentFilesTree
import org.equeim.tremotesf.rpc.RpcRequestState
import org.equeim.tremotesf.rpc.requests.FileSize
import org.equeim.tremotesf.rpc.requests.serversettings.DownloadingServerSettings
import org.equeim.tremotesf.ui.utils.RuntimePermissionHelper
import org.equeim.tremotesf.ui.utils.savedState
import timber.log.Timber
import java.util.concurrent.atomic.AtomicReference


interface AddTorrentFileModel {
    enum class ParserStatus {
        None,
        Loading,
        FileIsTooLarge,
        ReadingError,
        ParsingError,
        Loaded
    }

    data class FilePriorities(
        val unwantedFiles: List<Int>,
        val lowPriorityFiles: List<Int>,
        val highPriorityFiles: List<Int>,
    )

    data class ViewUpdateData(
        val parserStatus: ParserStatus,
        val downloadingSettings: RpcRequestState<DownloadingServerSettings>,
        val hasStoragePermission: Boolean,
    )

    var rememberedPagerItem: Int

    val needStoragePermission: Boolean
    val storagePermissionHelper: RuntimePermissionHelper?

    val parserStatus: StateFlow<ParserStatus>
    val viewUpdateData: Flow<ViewUpdateData>

    val filesTree: TorrentFilesTree
    val torrentName: String
    val renamedFiles: MutableMap<String, String>

    var shouldSetInitialLocalInputs: Boolean
    var shouldSetInitialRpcInputs: Boolean

    suspend fun getInitialDownloadDirectory(settings: DownloadingServerSettings): String
    suspend fun getFreeSpace(directory: String): FileSize?
    fun detachFd(): ParcelFileDescriptor?
    fun getFilePriorities(): FilePriorities
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

    override val needStoragePermission = args.uri.scheme == ContentResolver.SCHEME_FILE && storagePermissionHelper != null

    override val parserStatus = MutableStateFlow(AddTorrentFileModel.ParserStatus.None)

    override val viewUpdateData = combine(
        parserStatus,
        downloadingSettings,
        storagePermissionHelper?.permissionGranted ?: flowOf(false)
    ) { parserStatus, rpcStatus, hasPermission ->
        AddTorrentFileModel.ViewUpdateData(
            parserStatus,
            rpcStatus,
            hasPermission
        )
    }

    private var fd: AssetFileDescriptor? = null

    override val filesTree = TorrentFilesTree(viewModelScope)
    override val torrentName: String
        get() = filesTree.rootNode.children.first().item.name

    override val renamedFiles = mutableMapOf<String, String>()

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

    override fun detachFd(): ParcelFileDescriptor? {
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

    override fun getFilePriorities(): AddTorrentFileModel.FilePriorities {
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
                else -> {
                }
            }
        }

        return AddTorrentFileModel.FilePriorities(
            unwantedFiles,
            lowPriorityFiles,
            highPriorityFiles
        )
    }

    private suspend fun doLoad(uri: Uri, context: Context) = withContext(Dispatchers.IO) {
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
        val fdAtomic = AtomicReference(fd)
        try {
            val (rootNode, files) = TorrentFileParser.createFilesTree(fd.fileDescriptor)
            withContext(Dispatchers.Main) {
                this@AddTorrentFileModelImpl.fd = fd
                fdAtomic.set(null)
                this@AddTorrentFileModelImpl.files = files
                filesTree.init(rootNode, savedStateHandle)
                parserStatus.value = AddTorrentFileModel.ParserStatus.Loaded
            }
        } catch (error: FileReadException) {
            parserStatus.value = AddTorrentFileModel.ParserStatus.ReadingError
            return@withContext
        } catch (error: FileIsTooLargeException) {
            parserStatus.value = AddTorrentFileModel.ParserStatus.FileIsTooLarge
            return@withContext
        } catch (error: FileParseException) {
            parserStatus.value = AddTorrentFileModel.ParserStatus.ParsingError
            return@withContext
        } finally {
            fdAtomic.get()?.closeQuietly()
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
