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

import java.io.FileNotFoundException
import java.io.IOException
import java.lang.ref.WeakReference

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.os.AsyncTask

import androidx.lifecycle.ViewModel

import org.benjamin.Bdecoder

import org.equeim.tremotesf.utils.Logger
import org.equeim.tremotesf.utils.NonNullMutableLiveData


class AddTorrentFileModel : ViewModel() {
    companion object {
        // 10 MiB
        const val MAX_FILE_SIZE = 10 * 1024 * 1024
    }

    enum class ParserStatus {
        None,
        Loading,
        FileIsTooLarge,
        ReadingError,
        ParsingError,
        Loaded
    }

    data class FilePriorities(val wantedFiles: List<Int>,
                              val unwantedFiles: List<Int>,
                              val lowPriorityFiles: List<Int>,
                              val normalPriorityFiles: List<Int>,
                              val highPriorityFiles: List<Int>)

    val status = NonNullMutableLiveData(ParserStatus.None)

    lateinit var fileData: ByteArray
        private set

    val rootDirectory = BaseTorrentFilesAdapter.Directory()
    val torrentName: String
        get() = rootDirectory.children.first().name

    private lateinit var files: List<BaseTorrentFilesAdapter.File>

    fun load(uri: Uri) {
        if (status.value == ParserStatus.None) {
            status.value = ParserStatus.Loading
            TreeCreationTask(Application.instance, uri, this).execute()
        }
    }

    fun getFilePriorities(): FilePriorities {
        val wantedFiles = mutableListOf<Int>()
        val unwantedFiles = mutableListOf<Int>()
        val lowPriorityFiles = mutableListOf<Int>()
        val normalPriorityFiles = mutableListOf<Int>()
        val highPriorityFiles = mutableListOf<Int>()

        for (file in files) {
            val id = file.id
            if (file.wantedState == BaseTorrentFilesAdapter.Item.WantedState.Wanted) {
                wantedFiles.add(id)
            } else {
                unwantedFiles.add(id)
            }
            when (file.priority) {
                BaseTorrentFilesAdapter.Item.Priority.Low -> lowPriorityFiles
                BaseTorrentFilesAdapter.Item.Priority.Normal -> normalPriorityFiles
                BaseTorrentFilesAdapter.Item.Priority.High -> highPriorityFiles
                BaseTorrentFilesAdapter.Item.Priority.Mixed -> normalPriorityFiles
            }.add(id)
        }

        return FilePriorities(wantedFiles,
                              unwantedFiles,
                              lowPriorityFiles,
                              normalPriorityFiles,
                              highPriorityFiles)
    }

    @SuppressLint("StaticFieldLeak")
    private class TreeCreationTask(private val context: Context,
                                   private val uri: Uri,
                                   model: AddTorrentFileModel) : AsyncTask<Any, Any, ParserStatus>(), Logger {
        private val model = WeakReference(model)
        private lateinit var fileData: ByteArray
        private lateinit var rootDirectoryChild: BaseTorrentFilesAdapter.Item
        private lateinit var files: List<BaseTorrentFilesAdapter.File>

        override fun doInBackground(vararg params: Any?): ParserStatus {
            try {
                val stream = context.contentResolver.openInputStream(uri)
                if (stream == null) {
                    error("openInputStream() returned null")
                    return ParserStatus.ReadingError
                }

                try {
                    val size = stream.available()

                    if (size > MAX_FILE_SIZE) {
                        error("Torrent file is too large")
                        return ParserStatus.FileIsTooLarge
                    }

                    fileData = stream.readBytes()

                    return try {
                        createTree(Bdecoder(Charsets.UTF_8,
                                            fileData.inputStream()).decodeDict())
                        ParserStatus.Loaded
                    } catch (error: IllegalStateException) {
                        error("Error parsing torrent file", error)
                        ParserStatus.ParsingError
                    } catch (error: ClassCastException) {
                        error("Error parsing torrent file", error)
                        ParserStatus.ParsingError
                    }
                } catch (error: IOException) {
                    error("Error reading torrent file", error)
                    return ParserStatus.ReadingError
                } catch (error: SecurityException) {
                    error("Error reading torrent file", error)
                    return ParserStatus.ReadingError
                } finally {
                    stream.close()
                }
            } catch (error: FileNotFoundException) {
                error("File not found", error)
                return ParserStatus.ReadingError
            }
        }

        override fun onPostExecute(result: ParserStatus) {
            model.get()?.let { model ->
                if (result == ParserStatus.Loaded) {
                    model.fileData = fileData
                    rootDirectoryChild.parentDirectory = model.rootDirectory
                    model.rootDirectory.addChild(rootDirectoryChild)
                    model.files = files
                }
                model.status.value = result
            }
        }

        @Suppress("UNCHECKED_CAST")
        private fun createTree(torrentFileMap: Map<String, Any>) {
            val files = mutableListOf<BaseTorrentFilesAdapter.File>()

            val infoMap = torrentFileMap["info"] as Map<String, Any>

            if (infoMap.contains("files")) {
                val torrentDirectory = BaseTorrentFilesAdapter.Directory(0,
                                                                         null,
                                                                         infoMap["name"] as String)

                val filesMaps = infoMap["files"] as List<Map<String, Any>>
                for ((fileIndex, fileMap) in filesMaps.withIndex()) {
                    var directory = torrentDirectory

                    val pathParts = fileMap["path"] as List<String>
                    for ((partIndex, part) in pathParts.withIndex()) {
                        if (partIndex == pathParts.lastIndex) {
                            val file = BaseTorrentFilesAdapter.File(directory.children.size,
                                                                    directory,
                                                                    part,
                                                                    fileIndex)
                            file.size = fileMap["length"] as Long
                            directory.addChild(file)
                            files.add(file)
                        } else {
                            var childDirectory = directory.childrenMap[part]
                                    as BaseTorrentFilesAdapter.Directory?
                            if (childDirectory == null) {
                                childDirectory = BaseTorrentFilesAdapter.Directory(directory.children.size,
                                                                                   directory,
                                                                                   part)
                                directory.addChild(childDirectory)
                            }
                            directory = childDirectory
                        }
                    }
                }

                rootDirectoryChild = torrentDirectory
            } else {
                val file = BaseTorrentFilesAdapter.File(0,
                                                        null,
                                                        infoMap["name"] as String,
                                                        0)
                file.size = infoMap["length"] as Long
                files.add(file)
                rootDirectoryChild = file
            }

            rootDirectoryChild.setWanted(true)
            this.files = files
        }
    }
}
