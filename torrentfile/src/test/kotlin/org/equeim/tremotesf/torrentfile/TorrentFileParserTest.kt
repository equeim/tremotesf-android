// SPDX-FileCopyrightText: 2017-2024 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package org.equeim.tremotesf.torrentfile

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.FileInputStream
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

@OptIn(ExperimentalCoroutinesApi::class)
class TorrentFileParserTest {
    private val dispatcher = StandardTestDispatcher()
    private val dispatchers = TestDispatchers(dispatcher)

    private fun getResource(name: String): FileInputStream {
        val url = assertNotNull(javaClass.getResource(name))
        assertEquals("file", url.protocol)
        return FileInputStream(url.path)
    }

    @BeforeEach
    fun before() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterEach
    fun after() {
        Dispatchers.resetMain()
    }

    @Test
    fun `Parsing single file torrent`() = runTest {
        val actual = TorrentFileParser.parseTorrentFile(getResource(singleFileTorrent), dispatchers)
        assertEquals(singleFileTorrentParsed, actual.torrentFile)
        assertEquals(singleFileTorrentInfoHash, actual.infoHash)
    }

    @Test
    fun `Creating tree for single file torrent`() = runTest {
        val actual =
            TorrentFileParser.createFilesTree(singleFileTorrentParsed, dispatchers)
        assertTreeResultsAreSimilar(singleFileTorrentTreeResult, actual)
    }

    @Test
    fun `Parsing multiple file torrent`() = runTest {
        val actual = TorrentFileParser.parseTorrentFile(getResource(multipleFileTorrent), dispatchers)
        assertEquals(multipleFileTorrentParsed, actual.torrentFile)
        assertEquals(multipleFileTorrentInfoHash, actual.infoHash)
    }

    @Test
    fun `Creating tree for multiple file torrent`() = runTest {
        val actual =
            TorrentFileParser.createFilesTree(multipleFileTorrentParsed, dispatchers)
        assertTreeResultsAreSimilar(multipleFileTorrentTreeResult, actual)
    }

    @Test
    fun `Parsing multiple file torrent with subdirectories`() = runTest {
        val actual = TorrentFileParser.parseTorrentFile(
            getResource(multipleFileTorrentWithSubdirectories),
            dispatchers
        )
        assertEquals(multipleFileTorrentWithSubdirectoriesParsed, actual.torrentFile)
        assertEquals(multipleFileTorrentWithSubdirectoriesInfoHash, actual.infoHash)
    }

    @Test
    fun `Creating tree for multiple file torrent with subdirectories`() =
        runTest {
            val actual =
                TorrentFileParser.createFilesTree(
                    torrentFile = multipleFileTorrentWithSubdirectoriesParsed,
                    dispatchers = dispatchers
                )
            assertTreeResultsAreSimilar(multipleFileTorrentWithSubdirectoriesTreeResult, actual)
        }

    @Test
    fun `Parsing torrent that is too big`() = runTest {
        assertFailsWith<FileIsTooLargeException> {
            TorrentFileParser.parseTorrentFile(getResource(bigTorrent), dispatchers)
        }
    }

    @Test
    fun `Parsing and creating tree for torrent with multiple same top-level files`() = runTest {
        assertFailsWith<FileParseException> {
            val (_, torrentFile) = TorrentFileParser.parseTorrentFile(
                getResource(torrentWithMultipleSameTopLevelFiles),
                dispatchers
            )
            TorrentFileParser.createFilesTree(torrentFile, dispatchers)
        }
    }

    @Test
    fun `Parsing and creating tree for torrent with multiple same files`() = runTest {
        assertFailsWith<FileParseException> {
            val (_, torrentFile) = TorrentFileParser.parseTorrentFile(
                getResource(torrentWithMultipleSameFiles),
                dispatchers
            )
            TorrentFileParser.createFilesTree(torrentFile, dispatchers)
        }
    }

    private fun assertTreeResultsAreSimilar(
        expected: TorrentFilesTreeBuildResult,
        actual: TorrentFilesTreeBuildResult,
    ) {
        assertNodesAreSimilar(expected.rootNode, actual.rootNode)
        assertNodesAreSimilar(expected.files, actual.files)
    }

    private companion object {
        const val singleFileTorrent = "debian-10.9.0-amd64-netinst.iso.torrent"
        const val singleFileTorrentInfoHash = "9f292c93eb0dbdd7ff7a4aa551aaa1ea7cafe004"
        val singleFileTorrentParsed by lazy {
            TorrentFileParser.TorrentFile(
                TorrentFileParser.TorrentFile.Info(
                    files = null,
                    length = 353370112,
                    name = "debian-10.9.0-amd64-netinst.iso",
                )
            )
        }
        val singleFileTorrentTreeResult by lazy {
            buildTorrentFilesTree {
                addFile(
                    0,
                    listOf("debian-10.9.0-amd64-netinst.iso"),
                    353370112,
                    0,
                    TorrentFilesTree.Item.WantedState.Wanted,
                    TorrentFilesTree.Item.Priority.Normal
                )
            }
        }

        const val multipleFileTorrent = "Fedora-Workstation-Live-x86_64-34.torrent"
        const val multipleFileTorrentInfoHash = "2046e45fb6cf298cd25e4c0decbea40c6603d91b"
        val multipleFileTorrentParsed by lazy {
            TorrentFileParser.TorrentFile(
                TorrentFileParser.TorrentFile.Info(
                    files = listOf(
                        TorrentFileParser.TorrentFile.File(
                            1062,
                            listOf("Fedora-Workstation-34-1.2-x86_64-CHECKSUM")
                        ),
                        TorrentFileParser.TorrentFile.File(
                            2007367680,
                            listOf("Fedora-Workstation-Live-x86_64-34-1.2.iso")
                        )
                    ),
                    length = null,
                    name = "Fedora-Workstation-Live-x86_64-34",
                )
            )
        }
        val multipleFileTorrentTreeResult by lazy {
            buildTorrentFilesTree {
                addFile(
                    0,
                    listOf(
                        "Fedora-Workstation-Live-x86_64-34",
                        "Fedora-Workstation-34-1.2-x86_64-CHECKSUM"
                    ),
                    1062,
                    0,
                    TorrentFilesTree.Item.WantedState.Wanted,
                    TorrentFilesTree.Item.Priority.Normal
                )
                addFile(
                    1,
                    listOf(
                        "Fedora-Workstation-Live-x86_64-34",
                        "Fedora-Workstation-Live-x86_64-34-1.2.iso"
                    ),
                    2007367680,
                    0,
                    TorrentFilesTree.Item.WantedState.Wanted,
                    TorrentFilesTree.Item.Priority.Normal
                )
            }
        }

        const val multipleFileTorrentWithSubdirectories = "with_subdirectories.torrent"
        const val multipleFileTorrentWithSubdirectoriesInfoHash = "17566bcae446da4167827f4e5ce970318a6fbd99"
        val multipleFileTorrentWithSubdirectoriesParsed by lazy {
            TorrentFileParser.TorrentFile(
                TorrentFileParser.TorrentFile.Info(
                    files = listOf(
                        TorrentFileParser.TorrentFile.File(
                            153488,
                            listOf("fedora", "Fedora-Workstation-Live-x86_64-34.torrent")
                        ),
                        TorrentFileParser.TorrentFile.File(
                            27506,
                            listOf("debian", "debian-10.9.0-amd64-netinst.iso.torrent")
                        )
                    ),
                    length = null,
                    name = "foo",
                )
            )
        }
        val multipleFileTorrentWithSubdirectoriesTreeResult by lazy {
            buildTorrentFilesTree {
                addFile(
                    0,
                    listOf("foo", "fedora", "Fedora-Workstation-Live-x86_64-34.torrent"),
                    153488,
                    0,
                    TorrentFilesTree.Item.WantedState.Wanted,
                    TorrentFilesTree.Item.Priority.Normal
                )
                addFile(
                    1,
                    listOf("foo", "debian", "debian-10.9.0-amd64-netinst.iso.torrent"),
                    27506,
                    0,
                    TorrentFilesTree.Item.WantedState.Wanted,
                    TorrentFilesTree.Item.Priority.Normal
                )
            }
        }

        const val bigTorrent = "big.torrent"

        const val torrentWithMultipleSameTopLevelFiles = "multiple_same_top_level_files.torrent"
        const val torrentWithMultipleSameFiles = "multiple_same_files.torrent"
    }
}
