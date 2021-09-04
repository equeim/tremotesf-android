package org.equeim.tremotesf.torrentfile

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame

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

fun expectedFileItem(fileId: Int, nodePath: IntArray): TorrentFilesTree.Item {
    return TorrentFilesTree.Item(fileId, fileId.toString(), 666, 7, TorrentFilesTree.Item.WantedState.Wanted, TorrentFilesTree.Item.Priority.Normal, nodePath)
}

fun expectedDirectoryItem(nodePath: IntArray): TorrentFilesTree.Item {
    return TorrentFilesTree.Item(name = "42${nodePath.contentToString()}", nodePath = nodePath)
}

fun TorrentFilesTree.Node.getAllNodes(): Sequence<TorrentFilesTree.Node> {
    return if (this is TorrentFilesTree.DirectoryNode) {
        sequenceOf(this) + children.asSequence()
    } else {
        sequenceOf(this)
    }
}

class NodesThatMustChangeHelper(val nodes: List<TorrentFilesTree.Node>) {
    constructor(nodes: Sequence<TorrentFilesTree.Node>) : this(nodes.toList())
    constructor(vararg nodes: TorrentFilesTree.Node) : this(nodes.toList())

    private val oldItems = nodes.map { it.item }

    fun assertThatItemsChanged(additionalChecks: (TorrentFilesTree.Item) -> Unit = {}) = nodes.asSequence().zip(oldItems.asSequence()).forEach { (node, oldItem) ->
        assertNotSame(oldItem, node.item)
        assertNotEquals(oldItem, node.item)
        additionalChecks(node.item)
    }
}

class NodesThatMustNotChangeHelper(val nodes: List<TorrentFilesTree.Node>) {
    constructor(nodes: Sequence<TorrentFilesTree.Node>) : this(nodes.toList())
    constructor(vararg nodes: TorrentFilesTree.Node) : this(nodes.toList())

    private val oldItems = nodes.map { it.item }

    fun assertThatItemsAreNotChanged() = nodes.asSequence().zip(oldItems.asSequence()).forEach { (node, oldItem) ->
        assertSame(oldItem, node.item)
        assertEquals(oldItem, node.item)
    }
}
