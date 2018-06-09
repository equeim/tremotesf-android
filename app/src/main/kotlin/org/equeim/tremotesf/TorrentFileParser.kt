/*
 * Copyright (C) 2017-2018 Alexey Rochev <equeim@gmail.com>
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

import java.io.IOException

import android.content.Context
import android.net.Uri
import android.os.AsyncTask

import org.jetbrains.anko.AnkoLogger
import org.jetbrains.anko.error

import org.benjamin.Bdecoder


class TorrentFileParser : AnkoLogger {
    enum class Status {
        None,
        Loading,
        FileIsTooLarge,
        ReadingError,
        ParsingError,
        Loaded
    }

    class FilesData(val wantedFiles: List<Int>,
                    val unwantedFiles: List<Int>,
                    val lowPriorityFiles: List<Int>,
                    val normalPriorityFiles: List<Int>,
                    val highPriorityFiles: List<Int>)

    var status = Status.None
        private set(value) {
            field = value
            statusListener?.invoke(value)
        }

    var statusListener: ((Status) -> Unit)? = null

    lateinit var fileData: ByteArray
        private set

    val rootDirectory = BaseTorrentFilesAdapter.Directory()
    val torrentName: String
        get() = rootDirectory.children.first().name
    private val files = mutableListOf<BaseTorrentFilesAdapter.File>()

    fun load(uri: Uri, context: Context) {
        status = Status.Loading

        object : AsyncTask<Any, Any, Status>() {
            override fun doInBackground(vararg params: Any?): TorrentFileParser.Status {
                val stream = context.contentResolver.openInputStream(uri)
                try {
                    val size = stream.available()

                    if (size > 10 * 1024 * 1024) {
                        error("torrent file is too large")
                        return TorrentFileParser.Status.FileIsTooLarge
                    }

                    fileData = stream.readBytes()

                    return try {
                        createTree(Bdecoder(Charsets.UTF_8,
                                            fileData.inputStream()).decodeDict())
                        TorrentFileParser.Status.Loaded
                    } catch (error: IllegalStateException) {
                        error("error parsing torrent file", error)
                        TorrentFileParser.Status.ParsingError
                    }
                } catch (error: IOException) {
                    error("error reading torrent file", error)
                    return TorrentFileParser.Status.ReadingError
                } catch (error: SecurityException) {
                    error("error reading torrent file", error)
                    return TorrentFileParser.Status.ReadingError
                } finally {
                    stream.close()
                }
            }

            override fun onPostExecute(result: TorrentFileParser.Status) {
                this@TorrentFileParser.status = result
            }
        }.execute()
    }

    private fun createTree(torrentFileMap: Map<String, Any>) {
        @Suppress("UNCHECKED_CAST")
        val infoMap = torrentFileMap["info"] as Map<String, Any>

        if (infoMap.contains("files")) {
            val torrentDirectory = BaseTorrentFilesAdapter.Directory(0,
                                                                     rootDirectory,
                                                                     infoMap["name"] as String)
            rootDirectory.addChild(torrentDirectory)

            @Suppress("UNCHECKED_CAST")
            val filesMaps = infoMap["files"] as List<Map<String, Any>>
            for ((fileIndex, fileMap) in filesMaps.withIndex()) {
                var directory = torrentDirectory

                @Suppress("UNCHECKED_CAST")
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
        } else {
            val file = BaseTorrentFilesAdapter.File(0,
                                                    rootDirectory,
                                                    infoMap["name"] as String,
                                                    0)
            file.size = infoMap["length"] as Long
            rootDirectory.addChild(file)
            files.add(file)
        }

        rootDirectory.children.first().setWanted(true)
    }

    fun getFilesData(): FilesData {
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

        return FilesData(wantedFiles,
                         unwantedFiles,
                         lowPriorityFiles,
                         normalPriorityFiles,
                         highPriorityFiles)
    }
}
