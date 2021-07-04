package org.equeim.tremotesf.torrentfile

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestCoroutineDispatcher
import kotlinx.coroutines.test.runBlockingTest
import org.equeim.tremotesf.common.TremotesfDispatchers
import org.junit.Assert.assertEquals
import org.junit.Test

@ExperimentalCoroutinesApi
class TorrentFileParserTest {
    private val dispatcher = TestCoroutineDispatcher()

    private val dispatchers = object : TremotesfDispatchers {
        override val Default = dispatcher
        override val IO = dispatcher
        override val Main = dispatcher
    }

    private fun getResource(name: String) = requireNotNull(javaClass.getResourceAsStream(name)) {
        "Resource $name not found"
    }

    @Test
    fun `Parsing single file torrent`() = dispatcher.runBlockingTest {
        val actual = TorrentFileParser.parseFile(getResource(singleFileTorrent), dispatchers)
        assertEquals(singleFileTorrentParsed, actual)
    }

    @Test
    fun `Creating tree for single file torrent`() = dispatcher.runBlockingTest {
        val actual =
            TorrentFileParser.createFilesTree(singleFileTorrentParsed, dispatchers)
        assertTreeResultsAreSimilar(singleFileTorrentTreeResult, actual)
    }

    @Test
    fun `Parsing multiple file torrent`() = dispatcher.runBlockingTest {
        val actual = TorrentFileParser.parseFile(getResource(multipleFileTorrent), dispatchers)
        assertEquals(multipleFileTorrentParsed, actual)
    }

    @Test
    fun `Creating tree for multiple file torrent`() = dispatcher.runBlockingTest {
        val actual =
            TorrentFileParser.createFilesTree(multipleFileTorrentParsed, dispatchers)
        assertTreeResultsAreSimilar(multipleFileTorrentTreeResult, actual)
    }

    @Test
    fun `Parsing multiple file torrent with subdirectories`() = dispatcher.runBlockingTest {
        val actual = TorrentFileParser.parseFile(
            getResource(multipleFileTorrentWithSubdirectories),
            dispatchers
        )
        assertEquals(multipleFileTorrentWithSubdirectoriesParsed, actual)
    }

    @Test
    fun `Creating tree for multiple file torrent with subdirectories`() =
        dispatcher.runBlockingTest {
            val actual =
                TorrentFileParser.createFilesTree(
                    multipleFileTorrentWithSubdirectoriesParsed,
                    dispatchers
                )
            assertTreeResultsAreSimilar(multipleFileTorrentWithSubdirectoriesTreeResult, actual)
        }

    @Test
    fun `Parsing torrent that is too big`() = dispatcher.runBlockingTest {
        try {
            TorrentFileParser.parseFile(getResource(bigTorrent), dispatchers)
        } catch (ignore: FileIsTooLargeException) {
            return@runBlockingTest
        }
        throw AssertionError("FileIsTooLargeException exception is not thrown")
    }

    fun assertTreeResultsAreSimilar(expected: TorrentFilesTreeBuildResult, actual: TorrentFilesTreeBuildResult) {
        assertNodesAreSimilar(expected.rootNode, actual.rootNode)
        assertNodesAreSimilar(expected.files, actual.files)
    }

    private companion object {
        const val singleFileTorrent = "debian-10.9.0-amd64-netinst.iso.torrent"
        val singleFileTorrentParsed by lazy {
            TorrentFileParser.TorrentFile(
                TorrentFileParser.TorrentFile.Info(
                    files = null,
                    length = 353370112,
                    name = "debian-10.9.0-amd64-netinst.iso"
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
                    name = "Fedora-Workstation-Live-x86_64-34"
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
                    name = "foo"
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
    }
}
