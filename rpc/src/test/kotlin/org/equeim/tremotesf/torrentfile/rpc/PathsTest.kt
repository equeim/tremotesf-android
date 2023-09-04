// SPDX-FileCopyrightText: 2017-2023 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package org.equeim.tremotesf.torrentfile.rpc

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class PathsTest {
    private data class NormalizeTestCase(val input: String, val onUnix: String, val onWindows: String)

    @Test
    fun checkNormalize() {
        val testCases = listOf(
            NormalizeTestCase(input = "", onUnix = "", onWindows = ""),
            NormalizeTestCase(input = "/", onUnix = "/", onWindows = "/"),
            NormalizeTestCase(input = "//", onUnix = "/", onWindows = "//"),
            NormalizeTestCase(input = "///", onUnix = "/", onWindows = "//"),
            NormalizeTestCase(input = " / ", onUnix = "/", onWindows = "/"),
            NormalizeTestCase(input = "///home//foo", onUnix = "/home/foo", onWindows = "//home/foo"),
            NormalizeTestCase(input = "C:/home//foo", onUnix = "C:/home/foo", onWindows = "C:/home/foo"),
            NormalizeTestCase(input = "C:/home//foo/", onUnix = "C:/home/foo", onWindows = "C:/home/foo"),
            NormalizeTestCase(input = "C:\\home\\foo", onUnix = "C:\\home\\foo", onWindows = "C:/home/foo"),
            NormalizeTestCase(input = "C:\\home\\foo\\", onUnix = "C:\\home\\foo\\", onWindows = "C:/home/foo"),
            NormalizeTestCase(input = "C:\\home\\foo\\\\", onUnix = "C:\\home\\foo\\\\", onWindows = "C:/home/foo"),
            NormalizeTestCase(input = "z:\\home\\foo", onUnix = "z:\\home\\foo", onWindows = "Z:/home/foo"),
            NormalizeTestCase(input = "D:\\", onUnix = "D:\\", onWindows = "D:/"),
            NormalizeTestCase(input = " D:\\ ", onUnix = "D:\\", onWindows = "D:/"),
            NormalizeTestCase(input = "D:\\\\", onUnix = "D:\\\\", onWindows = "D:/"),
            NormalizeTestCase(input = "D:/", onUnix = "D:", onWindows = "D:/"),
            NormalizeTestCase(input = "D://", onUnix = "D:", onWindows = "D:/"),
            NormalizeTestCase(input = "///home//fo\\o", onUnix = "/home/fo\\o", onWindows = "//home/fo/o"),
            NormalizeTestCase(input = "///home//fo  o", onUnix = "/home/fo  o", onWindows = "//home/fo  o"),
            NormalizeTestCase(input = "C:\\home\\fo o", onUnix = "C:\\home\\fo o", onWindows = "C:/home/fo o"),
            NormalizeTestCase(input = "\\\\LOCALHOST\\c$\\home\\foo", onUnix = "\\\\LOCALHOST\\c\$\\home\\foo", onWindows = "//LOCALHOST/c$/home/foo"),
            NormalizeTestCase(input = "\\\\LOCALHOST\\C$\\home\\foo", onUnix = "\\\\LOCALHOST\\C\$\\home\\foo", onWindows = "//LOCALHOST/C$/home/foo"),
            NormalizeTestCase(input = "\\\\.\\c:\\home\\foo", onUnix = "\\\\.\\c:\\home\\foo", onWindows = "//./c:/home/foo"),
            NormalizeTestCase(input = "\\\\.\\C:\\home\\foo", onUnix = "\\\\.\\C:\\home\\foo", onWindows = "//./C:/home/foo"),
            NormalizeTestCase(input = "d:", onUnix = "d:", onWindows = "d:"),
            NormalizeTestCase(input = "d:foo", onUnix = "d:foo", onWindows = "d:foo"),
            NormalizeTestCase(input = "C::\\wtf", onUnix = "C::\\wtf", onWindows = "C::/wtf"),
        )

        val serverOsIsUnix = ServerCapabilities(rpcVersion = MINIMUM_RPC_VERSION, serverOs = ServerCapabilities.ServerOs.UnixLike)
        val serverOsIsWindows = ServerCapabilities(rpcVersion = MINIMUM_RPC_VERSION, serverOs = ServerCapabilities.ServerOs.Windows)
        testCases.forEach {
            assertEquals(it.onUnix, it.input.normalizePath(serverOsIsUnix).value)
            assertEquals(it.onWindows, it.input.normalizePath(serverOsIsWindows).value)
        }
    }

    /*private data class NativeSeparatorsTestCase(val inputPath: String, val expectedNativeSeparatorsPath: String)

    @Test
    fun checkToNativeSeparators() {
        val testCases = listOf(
            NativeSeparatorsTestCase("/", "/"),
            NativeSeparatorsTestCase("/home/foo", "/home/foo"),
            NativeSeparatorsTestCase("C:/", "C:\\"),
            NativeSeparatorsTestCase("C:/home/foo", "C:\\home\\foo"),

            // These are not absolute Windows file paths and are left untouched
            NativeSeparatorsTestCase("d:", "d:"),
            NativeSeparatorsTestCase("d:foo", "d:foo"),
            NativeSeparatorsTestCase("C::/wtf", "C::/wtf"),
            NativeSeparatorsTestCase("\\\\LOCALHOST\\c$\\home\\foo", "\\\\LOCALHOST\\c\$\\home\\foo")
        )
        testCases.forEach {
            println(it)
            assertEquals(it.expectedNativeSeparatorsPath, it.inputPath.toNativeSeparators())
        }
    }*/
}