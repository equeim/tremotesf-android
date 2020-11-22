/*
 * Copyright (C) 2017-2020 Alexey Rochev <equeim@gmail.com>
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

package org.equeim.tremotesf.data.torrentfile

import org.benjamin.Bdecoder

import org.equeim.tremotesf.utils.Logger

import java.io.FileDescriptor
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream

typealias TorrentFileFile = Map<String, Any>

@Suppress("UNCHECKED_CAST")
val TorrentFileFile.path
    get() = this["path"] as List<String>

val TorrentFileFile.length
    get() = this["length"] as Long


class FileReadException(cause: Throwable) : Exception(cause)
class FileIsTooLargeException : Exception()
class FileParseException(cause: Throwable) : Exception(cause)

object TorrentFileParser : Logger {
    // 10 MiB
    private const val MAX_FILE_SIZE = 10 * 1024 * 1024

    data class ParseResult(val rootDirectory: String?,
                           val files: List<TorrentFileFile>)

    fun parse(fd: FileDescriptor) = parseFile(FileInputStream(fd).buffered())

    private fun parseFile(inputStream: InputStream): ParseResult {
        if (inputStream.available() > MAX_FILE_SIZE) {
            error("File is too large")
            throw FileIsTooLargeException()
        }

        val torrentFileMap = try {
            Bdecoder(Charsets.UTF_8, inputStream).decodeDict()
        } catch (error: IOException) {
            error("Failed to read file", error)
            throw FileReadException(error)
        } catch (error: Exception) {
            error("Failed to parse bencode structure", error)
            throw FileParseException(error)
        }

        return try {
            val infoMap = torrentFileMap["info"] as Map<*, *>
            if (infoMap.containsKey("files")) {
                @Suppress("UNCHECKED_CAST")
                ParseResult(infoMap["name"] as String,
                            infoMap["files"] as List<TorrentFileFile>)
            } else {
                ParseResult(null,
                            listOf(mapOf("path" to listOf(infoMap["name"] as String),
                                         "length" to infoMap["length"] as Long)))
            }
        } catch (error: NullPointerException) {
            error("Decoded map doesn't contain required key", error)
            throw FileParseException(error)
        } catch (error: ClassCastException) {
            error("Decoded map doesn't contain required key", error)
            throw FileParseException(error)
        }
    }
}