// SPDX-FileCopyrightText: 2017-2022 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package org.equeim.tremotesf.torrentfile.rpc

import org.equeim.tremotesf.torrentfile.rpc.requests.NormalizedRpcPath

fun String.normalizePath(serverCapabilities: ServerCapabilities?): NormalizedRpcPath =
    normalizePath(this, serverCapabilities)

/**
 * [this] should be normalized. Paths returned by RpcClient are already normalized
 */
fun NormalizedRpcPath.toNativeSeparators(): String =
    if (serverOs == ServerCapabilities.ServerOs.Windows) {
        value.replace(UNIX_SEPARATOR_CHAR, WINDOWS_SEPARATOR_CHAR)
    } else {
        value
    }

@JvmName("normalizePathImpl")
private fun normalizePath(path: String, serverCapabilities: ServerCapabilities?): NormalizedRpcPath {
    //Timber.d("Normalizing path $path")
    if (path.isEmpty()) {
        //Timber.d("Empty")
        return NormalizedRpcPath(path, null)
    }
    var normalized = path.trim()
    if (normalized.isEmpty()) {
        //Timber.d("Blank")
        return NormalizedRpcPath(normalized, null)
    }
    if (serverCapabilities == null) {
        return NormalizedRpcPath(normalized, null)
    }
    if (serverCapabilities.serverOs == ServerCapabilities.ServerOs.Windows) {
        normalized = normalized.fromNativeWindowsSeparators()
        if (normalized.matches(ABSOLUTE_DOS_FILE_PATH_REGEX)) {
            normalized = normalized.capitalizeWindowsDriveLetter()
        }
    }
    normalized = normalized.collapseRepeatingSeparators(serverCapabilities).dropTrailingSeparator(serverCapabilities)
    //Timber.d("Normalized to $normalized")
    return NormalizedRpcPath(normalized, serverCapabilities.serverOs)
}

private fun String.fromNativeWindowsSeparators(): String =
    replace(WINDOWS_SEPARATOR_CHAR, UNIX_SEPARATOR_CHAR)

private fun String.capitalizeWindowsDriveLetter(): String =
    if (first().isLowerCase()) {
        replaceFirstChar { it.uppercase() }
    } else {
        this
    }

private fun String.collapseRepeatingSeparators(serverCapabilities: ServerCapabilities): String =
    replace(
        when (serverCapabilities.serverOs) {
            ServerCapabilities.ServerOs.UnixLike -> REPEATING_SEPARATORS_REGEX_UNIX
            ServerCapabilities.ServerOs.Windows -> REPEATING_SEPARATORS_REGEX_WINDOWS
        }, UNIX_SEPARATOR_STRING
    )

private val REPEATING_SEPARATORS_REGEX_UNIX = Regex("(//+)")
private val REPEATING_SEPARATORS_REGEX_WINDOWS = Regex("((?!^)//+)")

private fun String.dropTrailingSeparator(serverCapabilities: ServerCapabilities): String {
    val minimumLength = when (serverCapabilities.serverOs) {
        ServerCapabilities.ServerOs.UnixLike -> 1
        ServerCapabilities.ServerOs.Windows -> 3
    }
    return if (length > minimumLength && last() == UNIX_SEPARATOR_CHAR) {
        dropLast(1)
    } else {
        this
    }
}

private val ABSOLUTE_DOS_FILE_PATH_REGEX = Regex("^[A-Za-z]:[\\\\/].*\$")
private const val WINDOWS_SEPARATOR_CHAR = '\\'

private const val UNIX_SEPARATOR_CHAR = '/'
private const val UNIX_SEPARATOR_STRING = "/"

