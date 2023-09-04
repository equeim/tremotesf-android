// SPDX-FileCopyrightText: 2017-2023 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package org.equeim.tremotesf.torrentfile

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class TorrentFilesTreeBuilderTest {
    @Test
    fun `Build tree with one top-level file`() {
        val expectedFile = expectedFileItem(0, intArrayOf(0))

        val (rootNode, files) = buildTorrentFilesTree {
            addFile(
                expectedFile.fileId,
                listOf(expectedFile.name),
                expectedFile.size,
                expectedFile.completedSize,
                expectedFile.wantedState,
                expectedFile.priority
            )
        }

        assertEquals(expectedFile, (rootNode.children.single() as TorrentFilesTree.FileNode).item)
        assertEquals(expectedFile, files.single().item)
    }

    @Test
    fun `Build tree with a subdirectory`() {
        val expectedDirectory = expectedDirectoryItem(intArrayOf(0))
        val expectedFile = expectedFileItem(0, intArrayOf(0, 0))

        val (rootNode, files) = buildTorrentFilesTree {
            addFile(
                expectedFile.fileId,
                listOf(expectedDirectory.name, expectedFile.name),
                expectedFile.size,
                expectedFile.completedSize,
                expectedFile.wantedState,
                expectedFile.priority
            )
        }

        assertEquals(
            expectedFile,
            ((rootNode.children.single() as TorrentFilesTree.DirectoryNode).children.single() as TorrentFilesTree.FileNode).item
        )
        assertEquals(expectedFile, files.single().item)
    }

    @Test
    fun `Fail to create tree with multiple top-level files`() {
        assertFailsWith<IllegalArgumentException> {
            buildTorrentFilesTree {
                addFile(0, listOf("foo"), 0, 0, TorrentFilesTree.Item.WantedState.Wanted, TorrentFilesTree.Item.Priority.Normal)
                addFile(0, listOf("bar"), 0, 0, TorrentFilesTree.Item.WantedState.Wanted, TorrentFilesTree.Item.Priority.Normal)
            }
        }
    }

    @Test
    fun `Fail to create tree with multiple top-level directories`() {
        assertFailsWith<IllegalArgumentException> {
            buildTorrentFilesTree {
                addFile(0, listOf("foo", "bar"), 0, 0, TorrentFilesTree.Item.WantedState.Wanted, TorrentFilesTree.Item.Priority.Normal)
                addFile(0, listOf("bar", "foo"), 0, 0, TorrentFilesTree.Item.WantedState.Wanted, TorrentFilesTree.Item.Priority.Normal)
            }
        }
    }

    @Test
    fun `Fail to create tree when trying to add file when one of its parent directories was already added as file`() {
        assertFailsWith<IllegalArgumentException> {
            buildTorrentFilesTree {
                addFile(0, listOf("foo", "bar"), 0, 0, TorrentFilesTree.Item.WantedState.Wanted, TorrentFilesTree.Item.Priority.Normal)
                addFile(0, listOf("foo", "bar", "foobar"), 0, 0, TorrentFilesTree.Item.WantedState.Wanted, TorrentFilesTree.Item.Priority.Normal)
            }
        }
    }

    @Test
    fun `Fail to create tree when trying to add file when it was already added as directory`() {
        assertFailsWith<IllegalArgumentException> {
            buildTorrentFilesTree {
                addFile(0, listOf("foo", "bar", "foobar"), 0, 0, TorrentFilesTree.Item.WantedState.Wanted, TorrentFilesTree.Item.Priority.Normal)
                addFile(0, listOf("foo", "bar"), 0, 0, TorrentFilesTree.Item.WantedState.Wanted, TorrentFilesTree.Item.Priority.Normal)
            }
        }
    }
}
