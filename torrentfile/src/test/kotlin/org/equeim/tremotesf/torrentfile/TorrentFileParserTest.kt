// SPDX-FileCopyrightText: 2017-2025 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package org.equeim.tremotesf.torrentfile

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
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
        assertEquals(singleFileTorrentInfoHash, actual.infoHashV1)
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
        assertEquals(multipleFileTorrentInfoHash, actual.infoHashV1)
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
        assertEquals(multipleFileTorrentWithSubdirectoriesInfoHash, actual.infoHashV1)
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
    fun `Parsing multiple trackers torrent`() = runTest {
        val actual = TorrentFileParser.parseTorrentFile(
            getResource(multipleTrackersTorrent),
            dispatchers
        )
        assertEquals(multipleTrackersTorrentParsed, actual.torrentFile)
        assertEquals(multipleTrackersTorrentInfoHash, actual.infoHashV1)
    }

    @Test
    fun `Creating tree for multiple trackers torrent`() =
        runTest {
            val actual =
                TorrentFileParser.createFilesTree(
                    torrentFile = multipleTrackersTorrentParsed,
                    dispatchers = dispatchers
                )
            assertTreeResultsAreSimilar(multipleTrackersTorrentTreeResult, actual)
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
                info = TorrentFileParser.TorrentFile.Info(
                    files = null,
                    singleFileSize = 353370112,
                    nameOfDirectoryOrSingleFile = "debian-10.9.0-amd64-netinst.iso",
                ),
                singleTrackerAnnounceUrl = "http://bttracker.debian.org:6969/announce"
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
                info = TorrentFileParser.TorrentFile.Info(
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
                    singleFileSize = null,
                    nameOfDirectoryOrSingleFile = "Fedora-Workstation-Live-x86_64-34",
                ),
                singleTrackerAnnounceUrl = "http://torrent.fedoraproject.org:6969/announce"
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
                info = TorrentFileParser.TorrentFile.Info(
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
                    singleFileSize = null,
                    nameOfDirectoryOrSingleFile = "foo",
                ),
                singleTrackerAnnounceUrl = null
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

        const val multipleTrackersTorrent = "enwiki-20231220-pages-articles-multistream.xml.bz2.torrent"
        const val multipleTrackersTorrentInfoHash = "80fb3b384728e950f2fd09e5929970d3d576270d"
        val multipleTrackersTorrentParsed by lazy {
            TorrentFileParser.TorrentFile(
                info = TorrentFileParser.TorrentFile.Info(
                    files = null,
                    singleFileSize = 22711545577,
                    nameOfDirectoryOrSingleFile = "enwiki-20231220-pages-articles-multistream.xml.bz2",
                ),
                singleTrackerAnnounceUrl = "http://tracker.opentrackr.org:1337/announce",
                trackersAnnounceUrls = listOf(
                    setOf("http://tracker.opentrackr.org:1337/announce"),
                    setOf("udp://tracker.opentrackr.org:1337"),
                    setOf("udp://tracker.openbittorrent.com:80/announce"),
                    setOf("http://fosstorrents.com:6969/announce"),
                    setOf("udp://fosstorrents.com:6969/announce")
                )
            )
        }
        val multipleTrackersTorrentTreeResult by lazy {
            buildTorrentFilesTree {
                addFile(
                    0,
                    listOf("enwiki-20231220-pages-articles-multistream.xml.bz2"),
                    22711545577,
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
