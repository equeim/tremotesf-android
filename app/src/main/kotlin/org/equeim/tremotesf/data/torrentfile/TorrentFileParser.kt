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

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import org.equeim.bencode.Bencode

import org.equeim.tremotesf.utils.Logger

import java.io.FileDescriptor
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream


class FileReadException(cause: Throwable) : Exception(cause)
class FileIsTooLargeException : Exception()
class FileParseException(cause: Throwable) : Exception(cause)

@Serializable
data class TorrentFile(val info: Info) {
    @Serializable
    data class Info(
        val files: List<File>? = null,
        val length: Long? = null,
        val name: String
    )

    @Serializable
    data class File(val length: Long, val path: List<String>)
}

object TorrentFileParser : Logger {
    // 10 MiB
    private const val MAX_FILE_SIZE = 10 * 1024 * 1024

    fun parse(fd: FileDescriptor) = parseFile(FileInputStream(fd).buffered())

    private fun parseFile(inputStream: InputStream): TorrentFile {
        if (inputStream.available() > MAX_FILE_SIZE) {
            error("File is too large")
            throw FileIsTooLargeException()
        }
        return try {
            Bencode.decode(inputStream)
        } catch (error: IOException) {
            error("Failed to read file", error)
            throw FileReadException(error)
        } catch (error: SerializationException) {
            error("Failed to parse bencode structure", error)
            throw FileParseException(error)
        }
    }
}