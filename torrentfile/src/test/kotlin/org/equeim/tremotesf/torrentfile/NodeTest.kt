package org.equeim.tremotesf.torrentfile

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test

class NodeTest {
    @Test
    fun `Add file to node without children`() {
        val rootNode = TorrentFilesTree.DirectoryNode.createRootNode()
        val node = rootNode.addDirectory("0")

        val expectedItem = expectedFileItem(99, intArrayOf(0, 0))
        node.addFile(expectedItem.fileId, expectedItem.name, expectedItem.size, expectedItem.completedSize, expectedItem.wantedState, expectedItem.priority)
        checkLastChild(node, expectedItem)
    }

    @Test
    fun `Add directory to node without children`() {
        val rootNode = TorrentFilesTree.DirectoryNode.createRootNode()
        val node = rootNode.addDirectory("0")

        val expectedItem = expectedDirectoryItem(intArrayOf(0, 0))
        node.addDirectory(expectedItem.name)
        checkLastChild(node, expectedItem)
    }

    @Test
    fun `Add file to node with children`() {
        val rootNode = TorrentFilesTree.DirectoryNode.createRootNode()
        val node = rootNode.addDirectory("0")
        node.addFile(98, "foo", 1, 0, TorrentFilesTree.Item.WantedState.Wanted, TorrentFilesTree.Item.Priority.Normal)

        val expectedItem = expectedFileItem(99, intArrayOf(0, 1))
        node.addFile(expectedItem.fileId, expectedItem.name, expectedItem.size, expectedItem.completedSize, expectedItem.wantedState, expectedItem.priority)
        checkLastChild(node, expectedItem)
    }

    @Test
    fun `Add file with wrong id`() {
        val rootNode = TorrentFilesTree.DirectoryNode.createRootNode()
        val node = rootNode.addDirectory("0")

        val expectedItem = expectedFileItem(-666, intArrayOf(0, 0))
        assertThrows(IllegalArgumentException::class.java) {
            node.addFile(expectedItem.fileId, expectedItem.name, expectedItem.size, expectedItem.completedSize, expectedItem.wantedState, expectedItem.priority)
        }
    }

    @Test
    fun `Add directory to node with children`() {
        val rootNode = TorrentFilesTree.DirectoryNode.createRootNode()
        val node = rootNode.addDirectory("0")
        node.addFile(98, "foo", 1, 0, TorrentFilesTree.Item.WantedState.Wanted, TorrentFilesTree.Item.Priority.Normal)

        val expectedItem = expectedDirectoryItem(intArrayOf(0, 1))
        node.addDirectory(expectedItem.name)
        checkLastChild(node, expectedItem)
    }

    private fun checkLastChild(node: TorrentFilesTree.DirectoryNode, expectedItem: TorrentFilesTree.Item) {
        val lastChild = node.children.last()
        assertEquals(lastChild, node.getChildByItemNameOrNull(expectedItem.name))
        assertEquals(expectedItem, lastChild.item)
        assertArrayEquals(expectedItem.nodePath, lastChild.path)
    }

    @Test
    fun `Recalculating from children`() {
        val rootNode = TorrentFilesTree.DirectoryNode.createRootNode()
        val directory = rootNode.addDirectory("0").apply {
            addFile(98, "foo", 1, 42, TorrentFilesTree.Item.WantedState.Wanted, TorrentFilesTree.Item.Priority.Normal)
            addFile(99, "bar", 1, 42, TorrentFilesTree.Item.WantedState.Wanted, TorrentFilesTree.Item.Priority.Low)
            addDirectory("1")
                .addFile(100, "foo", 1, 42, TorrentFilesTree.Item.WantedState.Wanted, TorrentFilesTree.Item.Priority.Low)
        }


        var item = directory.item
        assertEquals(0, item.size)
        assertEquals(0, item.completedSize)
        assertEquals(TorrentFilesTree.Item.WantedState.Wanted, item.wantedState)
        assertEquals(TorrentFilesTree.Item.Priority.Normal, item.priority)

        directory.recalculateFromChildren()

        assertNotSame(item, directory.item)
        item = directory.item

        assertEquals(2, item.size)
        assertEquals(84, item.completedSize)
        assertEquals(TorrentFilesTree.Item.WantedState.Wanted, item.wantedState)
        assertEquals(TorrentFilesTree.Item.Priority.Mixed, item.priority)
    }

    @Test
    fun `Initially calculate from children recursively`() {
        val (directory, nestedDirectory) = createDirectoryWithSubdirectory()

        val directoryItem = directory.item
        val nestedDirectoryItem = nestedDirectory.item
        for (item in listOf(directoryItem, nestedDirectoryItem)) {
            assertEquals(0, item.size)
            assertEquals(0, item.completedSize)
            assertEquals(TorrentFilesTree.Item.WantedState.Wanted, item.wantedState)
            assertEquals(TorrentFilesTree.Item.Priority.Normal, item.priority)
        }

        directory.initiallyCalculateFromChildrenRecursively()

        assertSame(nestedDirectoryItem, nestedDirectory.item)
        assertEquals(2, nestedDirectoryItem.size)
        assertEquals(708, nestedDirectoryItem.completedSize)
        assertEquals(TorrentFilesTree.Item.WantedState.Mixed, nestedDirectoryItem.wantedState)
        assertEquals(TorrentFilesTree.Item.Priority.Low, nestedDirectoryItem.priority)

        assertSame(directoryItem, directory.item)
        assertEquals(4, directoryItem.size)
        assertEquals(792, directoryItem.completedSize)
        assertEquals(TorrentFilesTree.Item.WantedState.Mixed, directoryItem.wantedState)
        assertEquals(TorrentFilesTree.Item.Priority.Mixed, directoryItem.priority)
    }

    @Test
    fun `Set wanted state recursively`() {
       checkSetWantedStateOrPriority(
           operation = { setItemWantedRecursively(false, it) },
           itemAssert = { assertEquals(TorrentFilesTree.Item.WantedState.Unwanted, wantedState) }
       )
    }

    @Test
    fun `Set priority recursively`() {
        checkSetWantedStateOrPriority(
            operation = { setItemPriorityRecursively(TorrentFilesTree.Item.Priority.High, it) },
            itemAssert = { assertEquals(TorrentFilesTree.Item.Priority.High, priority) }
        )
    }

    private fun checkSetWantedStateOrPriority(operation: TorrentFilesTree.Node.(MutableList<Int>) -> Unit, itemAssert: TorrentFilesTree.Item.() -> Unit) {
        val (directory, _) = createDirectoryWithSubdirectory()
        directory.initiallyCalculateFromChildrenRecursively()

        fun getItems(node: TorrentFilesTree.Node, items: MutableList<TorrentFilesTree.Item>) {
            items.add(node.item)
            (node as? TorrentFilesTree.DirectoryNode)?.children?.forEach { getItems(it, items) }
        }
        val oldItems = mutableListOf<TorrentFilesTree.Item>()
        getItems(directory, oldItems)

        val ids = mutableListOf<Int>()
        directory.operation(ids)

        val newItems = mutableListOf<TorrentFilesTree.Item>()
        getItems(directory, newItems)

        oldItems.asSequence().zip(newItems.asSequence()).forEach { (oldItem, newItem) ->
            assertNotSame(oldItem, newItem)
            newItem.itemAssert()
        }

        assertEquals(setOf(98, 99, 100, 101), ids.toSet())
    }

    private fun createDirectoryWithSubdirectory(): Pair<TorrentFilesTree.DirectoryNode, TorrentFilesTree.DirectoryNode> {
        val rootNode = TorrentFilesTree.DirectoryNode.createRootNode()

        val directory = rootNode.addDirectory("0").apply {
            addFile(98, "foo", 1, 42, TorrentFilesTree.Item.WantedState.Wanted, TorrentFilesTree.Item.Priority.Normal)
            addFile(99, "bar", 1, 42, TorrentFilesTree.Item.WantedState.Wanted, TorrentFilesTree.Item.Priority.Low)
        }
        val nestedDirectory = directory.addDirectory("1").apply {
            addFile(100, "foo", 1, 42, TorrentFilesTree.Item.WantedState.Wanted, TorrentFilesTree.Item.Priority.Low)
            addFile(101, "bar", 1, 666, TorrentFilesTree.Item.WantedState.Unwanted, TorrentFilesTree.Item.Priority.Low)
        }
        return directory to nestedDirectory
    }
}
