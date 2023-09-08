// SPDX-FileCopyrightText: 2017-2023 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package org.equeim.tremotesf.torrentfile

import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.reflect.KClass
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class TorrentFilesTreeTest {
    private val dispatcher = StandardTestDispatcher()
    private val dispatchers = TestDispatchers(dispatcher)

    @BeforeEach
    fun before() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterEach
    fun after() {
        Dispatchers.resetMain()
    }

    @Test
    fun `Check initial initialized state`() = runTest {
        val rootNode = TorrentFilesTree.DirectoryNode.createRootNode()
        val fileItem = expectedFileItem(0, intArrayOf(0))
        rootNode.addFile(
            fileItem.fileId,
            fileItem.name,
            fileItem.size,
            fileItem.completedSize,
            fileItem.wantedState,
            fileItem.priority
        )

        testWithTree(rootNode) {
            assertSame(rootNode, currentNodePublic)
            assertEquals(listOf(fileItem), items.value)
        }
    }

    @Test
    fun `Navigate up when tree is not initialized`() = runTest {
        testWithUnitializedTree { assertFalse(navigateUp()) }
    }

    @Test
    fun `Navigate up when we are in the root directory`() = runTest {
        val rootNode = TorrentFilesTree.DirectoryNode.createRootNode()
        rootNode.addFile(
            0,
            "42",
            666,
            0,
            TorrentFilesTree.Item.WantedState.Wanted,
            TorrentFilesTree.Item.Priority.Normal
        )

        testWithTree(rootNode) {
            assertFalse(navigateUp())
        }
    }

    @Test
    fun `Navigate down when we are in the root directory`() = runTest {
        val rootNode = TorrentFilesTree.DirectoryNode.createRootNode()
        val directory = rootNode.addDirectory("666")
        val fileItem = expectedFileItem(0, intArrayOf(0, 0))
        directory.addFile(
            fileItem.fileId,
            fileItem.name,
            fileItem.size,
            fileItem.completedSize,
            fileItem.wantedState,
            fileItem.priority
        )
        testWithTree(rootNode) {
            navigateDown(directory.item)
            runCurrent()
            assertSame(directory, currentNodePublic)
            assertEquals(listOf(null, fileItem), items.value)
        }
    }

    @Test
    fun `Navigate down to unknown item`() = runTest {
        val rootNode = TorrentFilesTree.DirectoryNode.createRootNode()
        val directory = rootNode.addDirectory("666")
        val fileItem = expectedFileItem(0, intArrayOf(0, 0))
        directory.addFile(
            fileItem.fileId,
            fileItem.name,
            fileItem.size,
            fileItem.completedSize,
            fileItem.wantedState,
            fileItem.priority
        )
        val unknownItem = expectedDirectoryItem(intArrayOf(42, 777))
        testWithTree(rootNode) {
            navigateDown(unknownItem)
            runCurrent()
            assertSame(rootNode, currentNodePublic)
            assertEquals(listOf(directory.item), items.value)
        }
    }

    @Test
    fun `Navigate up when we are in the top-level directory`() = runTest {
        val rootNode = TorrentFilesTree.DirectoryNode.createRootNode()
        val directory = rootNode.addDirectory("666")
        val fileItem = expectedFileItem(0, intArrayOf(0, 0))
        directory.addFile(
            fileItem.fileId,
            fileItem.name,
            fileItem.size,
            fileItem.completedSize,
            fileItem.wantedState,
            fileItem.priority
        )
        testWithTree(rootNode) {
            navigateDown(directory.item)
            runCurrent()
            assertTrue(navigateUp())
            runCurrent()

            assertSame(rootNode, currentNodePublic)
            assertEquals(listOf(directory.item), items.value)
        }
    }

    @Test
    fun `Navigate up when we are in the subdirectory`() = runTest {
        val rootNode = TorrentFilesTree.DirectoryNode.createRootNode()
        val directory = rootNode.addDirectory("666")
        val subdirectory = directory.addDirectory("Foo")
        val fileItem = expectedFileItem(0, intArrayOf(0, 0, 0))
        subdirectory.addFile(
            fileItem.fileId,
            fileItem.name,
            fileItem.size,
            fileItem.completedSize,
            fileItem.wantedState,
            fileItem.priority
        )
        testWithTree(rootNode) {
            navigateDown(directory.item)
            runCurrent()
            navigateDown(subdirectory.item)
            runCurrent()
            assertTrue(navigateUp())
            runCurrent()

            assertSame(directory, currentNodePublic)
            assertEquals(listOf(null, subdirectory.item), items.value)
        }
    }

    @Test
    fun `Update current items and sort them`() = runTest {
        val rootNode = TorrentFilesTree.DirectoryNode.createRootNode()
        val directory = rootNode.addDirectory("666")
        val fileItems =
            listOf(expectedFileItem(0, intArrayOf(0, 0)), expectedFileItem(1, intArrayOf(0, 1)))
        val fileNodes = fileItems.map {
            directory.addFile(
                it.fileId,
                it.name,
                it.size,
                it.completedSize,
                it.wantedState,
                it.priority
            )
        }
        testWithTree(rootNode) {
            navigateDown(directory.item)
            runCurrent()

            assertEquals(listOf(null) + fileItems, items.value)

            val newFileItems = listOf(
                fileItems[0].copy(name = "ZZZ"), fileItems[1].copy(name = "AAA")
            )
            newFileItems.forEachIndexed { index, item -> fileNodes[index].item = item }

            updateItemsWithSortingPublic()

            assertEquals(listOf(null, newFileItems[1], newFileItems[0]), items.value)
        }
    }

    @Test
    fun `Set items wanted state`() = runTest {
        setItemsWantedOrPriority(TorrentFilesTree.Item.WantedState::class)
    }

    @Test
    fun `Set items priority`() = runTest {
        setItemsWantedOrPriority(TorrentFilesTree.Item.Priority::class)
    }

    private fun <T : Any> TestScope.setItemsWantedOrPriority(type: KClass<T>) {
        val rootNode = TorrentFilesTree.DirectoryNode.createRootNode()
        val directory = rootNode.addDirectory("666")

        val otherFile = directory.addFile(
            42,
            "",
            0,
            0,
            TorrentFilesTree.Item.WantedState.Wanted,
            TorrentFilesTree.Item.Priority.Normal
        )
        val otherFileItem = otherFile.item
        val otherFileItemCopy = otherFile.item.copy()

        val subdirectory = directory.addDirectory("Foo")
        val fileItems =
            listOf(expectedFileItem(0, intArrayOf(0, 0)), expectedFileItem(1, intArrayOf(0, 1)))
        for (item in fileItems) {
            subdirectory.addFile(
                item.fileId,
                item.name,
                item.size,
                item.completedSize,
                item.wantedState,
                item.priority
            )
        }

        val mustChange = NodesThatMustChangeHelper(subdirectory.getAllNodes())

        testWithTree(rootNode) {
            navigateDown(directory.item)
            runCurrent()

            val oldItems = items.value!!

            var callbackCalled = false
            lateinit var checkItem: (TorrentFilesTree.Item) -> Unit
            lateinit var checkParentDirectoryItem: (TorrentFilesTree.Item) -> Unit

            if (type == TorrentFilesTree.Item.WantedState::class) {
                onSetFilesWantedCallback = { ids, wanted ->
                    if (ids.toList() == fileItems.map { it.fileId } && !wanted) {
                        callbackCalled = true
                    }
                }
                checkItem = { assertEquals(TorrentFilesTree.Item.WantedState.Unwanted, it.wantedState) }
                checkParentDirectoryItem = { assertEquals(TorrentFilesTree.Item.WantedState.Mixed, it.wantedState) }

                setItemsWanted(listOf(subdirectory.path.last()), false)
            } else if (type == TorrentFilesTree.Item.Priority::class) {
                onSetFilesPriorityCallback = { ids, priority ->
                    if (ids.toList() == fileItems.map { it.fileId } && priority == TorrentFilesTree.Item.Priority.Low) {
                        callbackCalled = true
                    }
                }
                checkItem = { assertEquals(TorrentFilesTree.Item.Priority.Low, it.priority) }
                checkParentDirectoryItem = { assertEquals(TorrentFilesTree.Item.Priority.Mixed, it.priority) }

                setItemsPriority(listOf(subdirectory.path.last()), TorrentFilesTree.Item.Priority.Low)
            }
            runCurrent()

            assertTrue(callbackCalled)

            mustChange.assertThatItemsChanged(checkItem)

            assertSame(otherFileItem, otherFile.item)
            assertEquals(otherFileItemCopy, otherFile.item)

            oldItems.asSequence().zip(items.value!!.asSequence()).forEach { (old, new) ->
                when {
                    old == null -> assertNull(new)
                    old.nodePath.contentEquals(subdirectory.path) -> checkItem(new!!)
                    else -> {
                        assertSame(old, new)
                    }
                }
            }

            var node = rootNode
            for (i in subdirectory.path.dropLast(1)) {
                node = node.children[i] as TorrentFilesTree.DirectoryNode
                checkParentDirectoryItem(node.item)
            }
        }
    }

    @Test
    fun `Recalculate node and its parents when node is top-level file node`() = runTest {
        val rootNode = TorrentFilesTree.DirectoryNode.createRootNode()

        val file = rootNode.addFile(
            0,
            "",
            0,
            0,
            TorrentFilesTree.Item.WantedState.Wanted,
            TorrentFilesTree.Item.Priority.Normal
        )

        val mustNotChange = NodesThatMustNotChangeHelper(rootNode.getAllNodes())

        testWithTree(rootNode) {
            val recalculated = recalculateNodeAndItsParents(file)
            assertTrue(recalculated.isEmpty())
            mustNotChange.assertThatItemsAreNotChanged()
        }
    }

    @Test
    fun `Recalculate node and its parents when node is a directory node`() = runTest {
        val rootNode = TorrentFilesTree.DirectoryNode.createRootNode()

        val directory = rootNode.addDirectory("dir")
        val subdirectory = directory.addDirectory("subdir")
        val file1 = subdirectory.addFile(
            0,
            "file1",
            0,
            0,
            TorrentFilesTree.Item.WantedState.Wanted,
            TorrentFilesTree.Item.Priority.Normal
        )
        val file2 = subdirectory.addFile(
            1,
            "file2",
            0,
            0,
            TorrentFilesTree.Item.WantedState.Wanted,
            TorrentFilesTree.Item.Priority.Normal
        )
        val subdirectory2 = directory.addDirectory("subdir2")
        val file3 = subdirectory2.addFile(
            1,
            "file3",
            1,
            1,
            TorrentFilesTree.Item.WantedState.Wanted,
            TorrentFilesTree.Item.Priority.Normal
        )

        testWithTree(rootNode) {
            file1.item = file1.item.copy(
                size = 42,
                completedSize = 1,
                wantedState = TorrentFilesTree.Item.WantedState.Unwanted,
                priority = TorrentFilesTree.Item.Priority.Low
            )
            file2.item = file2.item.copy(
                size = 777,
                completedSize = 88,
                wantedState = TorrentFilesTree.Item.WantedState.Wanted,
                priority = TorrentFilesTree.Item.Priority.Low
            )

            val mustChange = NodesThatMustChangeHelper(directory, subdirectory)
            val mustNotChange = NodesThatMustNotChangeHelper(rootNode, file1, file2, subdirectory2, file3)

            val recalculated = recalculateNodeAndItsParents(subdirectory)
            assertEquals(mustChange.nodes.toSet(), recalculated)
            mustChange.assertThatItemsChanged()
            mustNotChange.assertThatItemsAreNotChanged()
            with(subdirectory.item) {
                assertEquals(819, size)
                assertEquals(89, completedSize)
                assertEquals(TorrentFilesTree.Item.WantedState.Mixed, wantedState)
                assertEquals(TorrentFilesTree.Item.Priority.Low, priority)
            }
            with(directory.item) {
                assertEquals(820, size)
                assertEquals(90, completedSize)
                assertEquals(TorrentFilesTree.Item.WantedState.Mixed, wantedState)
                assertEquals(TorrentFilesTree.Item.Priority.Mixed, priority)
            }
        }
    }

    @Test
    fun `Recalculate node and its parents when node is a file node`() = runTest {
        val rootNode = TorrentFilesTree.DirectoryNode.createRootNode()

        val directory = rootNode.addDirectory("dir")
        val subdirectory = directory.addDirectory("subdir")
        val file1 = subdirectory.addFile(
            0,
            "file1",
            0,
            0,
            TorrentFilesTree.Item.WantedState.Wanted,
            TorrentFilesTree.Item.Priority.Normal
        )
        val file2 = subdirectory.addFile(
            1,
            "file2",
            0,
            0,
            TorrentFilesTree.Item.WantedState.Wanted,
            TorrentFilesTree.Item.Priority.Normal
        )
        val subdirectory2 = directory.addDirectory("subdir2")
        val file3 = subdirectory2.addFile(
            1,
            "file3",
            1,
            1,
            TorrentFilesTree.Item.WantedState.Wanted,
            TorrentFilesTree.Item.Priority.Normal
        )

        testWithTree(rootNode) {
            file1.item = file1.item.copy(
                size = 42,
                completedSize = 1,
                wantedState = TorrentFilesTree.Item.WantedState.Unwanted,
                priority = TorrentFilesTree.Item.Priority.Low
            )

            val mustChange = NodesThatMustChangeHelper(directory, subdirectory)
            val mustNotChange = NodesThatMustNotChangeHelper(rootNode, file1, file2, subdirectory2, file3)

            val recalculated = recalculateNodeAndItsParents(subdirectory)
            assertEquals(mustChange.nodes.toSet(), recalculated)
            mustChange.assertThatItemsChanged()
            mustNotChange.assertThatItemsAreNotChanged()
            with(subdirectory.item) {
                assertEquals(42, size)
                assertEquals(1, completedSize)
                assertEquals(TorrentFilesTree.Item.WantedState.Mixed, wantedState)
                assertEquals(TorrentFilesTree.Item.Priority.Mixed, priority)
            }
            with(directory.item) {
                assertEquals(43, size)
                assertEquals(2, completedSize)
                assertEquals(TorrentFilesTree.Item.WantedState.Mixed, wantedState)
                assertEquals(TorrentFilesTree.Item.Priority.Mixed, priority)
            }
        }
    }

    @Test
    fun `Rename file when path is correct and it is in current items`() = runTest {
        val rootNode = TorrentFilesTree.DirectoryNode.createRootNode()

        val directory = rootNode.addDirectory("dir")
        val subdirectory = directory.addDirectory("subdir")
        subdirectory.addFile(
            0,
            "file1",
            0,
            0,
            TorrentFilesTree.Item.WantedState.Wanted,
            TorrentFilesTree.Item.Priority.Normal
        )
        val file2 = subdirectory.addFile(
            1,
            "file2",
            0,
            0,
            TorrentFilesTree.Item.WantedState.Wanted,
            TorrentFilesTree.Item.Priority.Normal
        )

        renameFileCorrectly(rootNode, file2, "dir/subdir/file2") {
            navigateDown(directory.item)
            runCurrent()
            navigateDown(subdirectory.item)
            runCurrent()
        }
    }

    @Test
    fun `Rename file when path is correct and it is not in current items`() = runTest {
        val rootNode = TorrentFilesTree.DirectoryNode.createRootNode()

        val directory = rootNode.addDirectory("dir")
        val subdirectory = directory.addDirectory("subdir")
        subdirectory.addFile(
            0,
            "file1",
            0,
            0,
            TorrentFilesTree.Item.WantedState.Wanted,
            TorrentFilesTree.Item.Priority.Normal
        )
        val file2 = subdirectory.addFile(
            1,
            "file2",
            0,
            0,
            TorrentFilesTree.Item.WantedState.Wanted,
            TorrentFilesTree.Item.Priority.Normal
        )

        renameFileCorrectly(rootNode, file2, "dir/subdir/file2") {
            navigateDown(directory.item)
            runCurrent()
        }
    }

    @Test
    fun `Rename directory when path is correct and it is in current items`() = runTest {
        val rootNode = TorrentFilesTree.DirectoryNode.createRootNode()

        val directory = rootNode.addDirectory("dir")
        val subdirectory = directory.addDirectory("subdir")
        subdirectory.addFile(
            0,
            "file1",
            0,
            0,
            TorrentFilesTree.Item.WantedState.Wanted,
            TorrentFilesTree.Item.Priority.Normal
        )
        subdirectory.addFile(
            1,
            "file2",
            0,
            0,
            TorrentFilesTree.Item.WantedState.Wanted,
            TorrentFilesTree.Item.Priority.Normal
        )

        renameFileCorrectly(rootNode, subdirectory, "dir/subdir") {
            navigateDown(directory.item)
            runCurrent()
        }
    }

    @Test
    fun `Rename directory when path is correct and it is not in current items`() = runTest {
        val rootNode = TorrentFilesTree.DirectoryNode.createRootNode()

        val directory = rootNode.addDirectory("dir")
        val subdirectory = directory.addDirectory("subdir")
        subdirectory.addFile(
            0,
            "file1",
            0,
            0,
            TorrentFilesTree.Item.WantedState.Wanted,
            TorrentFilesTree.Item.Priority.Normal
        )
        subdirectory.addFile(
            1,
            "file2",
            0,
            0,
            TorrentFilesTree.Item.WantedState.Wanted,
            TorrentFilesTree.Item.Priority.Normal
        )

        renameFileCorrectly(rootNode, subdirectory, "dir/subdir") {
            navigateDown(directory.item)
            runCurrent()
            navigateDown(subdirectory.item)
            runCurrent()
        }
    }

    private fun TestScope.renameFileCorrectly(
        rootNode: TorrentFilesTree.DirectoryNode,
        renamedNode: TorrentFilesTree.Node,
        path: String,
        navigate: TestTree.() -> Unit,
    ) {
        val mustChange = NodesThatMustChangeHelper(renamedNode)
        val mustNotChange = NodesThatMustNotChangeHelper(rootNode.getAllNodes().minus(renamedNode))

        testWithTree(rootNode) {
            navigate()

            val isInItems = currentNodePublic.children.contains(renamedNode)
            val oldItems = items.value

            renameFile(path, "foo")
            runCurrent()

            mustChange.assertThatItemsChanged { assertEquals("foo", it.name) }
            mustNotChange.assertThatItemsAreNotChanged()

            if (isInItems) {
                assertNotEquals(oldItems, items.value)
                assertTrue(items.value!!.contains(renamedNode.item))
            } else {
                assertSame(oldItems, items.value)
            }
        }
    }

    @Test
    fun `Rename file when path is incorrect 1`() = runTest {
        renameFileWhenPathIsIncorrect("foo/bar")
    }

    @Test
    fun `Rename file when path is incorrect 2`() = runTest {
        renameFileWhenPathIsIncorrect("dir/subdir/foo")
    }

    @Test
    fun `Rename file when path is incorrect 3`() = runTest {
        renameFileWhenPathIsIncorrect("dir/subdir/file1/foo")
    }

    @Test
    fun `Rename file when path is incorrect 4`() = runTest {
        renameFileWhenPathIsIncorrect("")
    }

    private fun TestScope.renameFileWhenPathIsIncorrect(path: String) {
        val rootNode = TorrentFilesTree.DirectoryNode.createRootNode()

        val directory = rootNode.addDirectory("dir")
        val subdirectory = directory.addDirectory("subdir")
        subdirectory.addFile(
            0,
            "file1",
            0,
            0,
            TorrentFilesTree.Item.WantedState.Wanted,
            TorrentFilesTree.Item.Priority.Normal
        )
        subdirectory.addFile(
            1,
            "file2",
            0,
            0,
            TorrentFilesTree.Item.WantedState.Wanted,
            TorrentFilesTree.Item.Priority.Normal
        )

        val mustNotChange = NodesThatMustNotChangeHelper(rootNode.getAllNodes())

        testWithTree(rootNode) {
            val oldItems = items.value

            renameFile(path, "foo")
            runCurrent()

            mustNotChange.assertThatItemsAreNotChanged()
            assertSame(oldItems, items.value)
        }
    }

    @Test
    fun `Get name path for top-level file`() = runTest {
        val rootNode = TorrentFilesTree.DirectoryNode.createRootNode()
        val file = rootNode.addFile(
            1,
            "foo",
            0,
            0,
            TorrentFilesTree.Item.WantedState.Wanted,
            TorrentFilesTree.Item.Priority.Normal
        )
        testWithTree(rootNode) {
            assertEquals(file.item.name, getItemNamePath(file.item))
        }
    }

    @Test
    fun `Get name path for top-level directory`() = runTest {
        val rootNode = TorrentFilesTree.DirectoryNode.createRootNode()
        val directory = rootNode.addDirectory("foo")
        directory.addFile(
            1,
            "foo",
            0,
            0,
            TorrentFilesTree.Item.WantedState.Wanted,
            TorrentFilesTree.Item.Priority.Normal
        )
        testWithTree(rootNode) {
            assertEquals(directory.item.name, getItemNamePath(directory.item))
        }
    }

    @Test
    fun `Get name path for file`() = runTest {
        val rootNode = TorrentFilesTree.DirectoryNode.createRootNode()
        val directory = rootNode.addDirectory("foo")
        val file = directory.addFile(
            1,
            "foo",
            0,
            0,
            TorrentFilesTree.Item.WantedState.Wanted,
            TorrentFilesTree.Item.Priority.Normal
        )
        testWithTree(rootNode) {
            assertEquals("foo/foo", getItemNamePath(file.item))
        }
    }

    @Test
    fun `Get name path for directory`() = runTest {
        val rootNode = TorrentFilesTree.DirectoryNode.createRootNode()
        val directory = rootNode.addDirectory("foo")
        val subdirectory = directory.addDirectory("foo")
        subdirectory.addFile(
            1,
            "foo",
            0,
            0,
            TorrentFilesTree.Item.WantedState.Wanted,
            TorrentFilesTree.Item.Priority.Normal
        )
        testWithTree(rootNode) {
            assertEquals("foo/foo", getItemNamePath(subdirectory.item))
        }
    }

    private inline fun TestScope.testWithTree(
        rootNode: TorrentFilesTree.DirectoryNode,
        block: TestTree.() -> Unit,
    ) {
        rootNode.children.forEach { (it as? TorrentFilesTree.DirectoryNode)?.initiallyCalculateFromChildrenRecursively() }
        TestTree(this).apply {
            init(rootNode, SavedStateHandle())
            runCurrent()
            block()
            destroy()
        }
    }

    private inline fun CoroutineScope.testWithUnitializedTree(block: TestTree.() -> Unit) {
        TestTree(this).apply {
            block()
            destroy()
        }
    }

    private inner class TestTree(parentScope: CoroutineScope) :
        TorrentFilesTree(parentScope, dispatcher, dispatchers) {
        val currentNodePublic: DirectoryNode
            get() = currentNode

        suspend fun updateItemsWithSortingPublic() = updateItemsWithSorting()

        var onSetFilesWantedCallback: (IntArray, Boolean) -> Unit = { _, _ -> }
        override fun onSetFilesWanted(ids: IntArray, wanted: Boolean) {
            println("onSetFilesWanted calle with: ids = ${ids.contentToString()}, wanted = $wanted")
            onSetFilesWantedCallback(ids, wanted)
        }

        var onSetFilesPriorityCallback: (IntArray, Item.Priority) -> Unit = { _, _ -> }
        override fun onSetFilesPriority(ids: IntArray, priority: Item.Priority) {
            println("onSetFilesPriority calle with: ids = ${ids.contentToString()}, priority = $priority")
            onSetFilesPriorityCallback(ids, priority)
        }
    }
}
