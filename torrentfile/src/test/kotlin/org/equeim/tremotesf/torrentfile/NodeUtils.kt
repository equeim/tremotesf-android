package org.equeim.tremotesf.torrentfile

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull

fun assertNodesAreSimilar(expected: TorrentFilesTree.Node, actual: TorrentFilesTree.Node?) {
    assertNotNull(actual)
    assertEquals(expected::class, actual!!::class)
    assertArrayEquals(expected.path, actual.path)
    assertEquals(expected.item, actual.item)
    if (expected is TorrentFilesTree.DirectoryNode && actual is TorrentFilesTree.DirectoryNode) {
        assertNodesAreSimilar(expected.children, actual.children)
    }
}

fun assertNodesAreSimilar(expected: List<TorrentFilesTree.Node>, actual: List<TorrentFilesTree.Node>) {
    expected.asSequence().zip(actual.asSequence()).forEach { (expectedFile, actualFile) ->
        assertNodesAreSimilar(expectedFile, actualFile)
    }
}
