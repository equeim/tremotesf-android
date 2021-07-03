/*
 * Copyright (C) 2017-2020 Alexey Rochev <equeim@gmail.com>
 *
 * This file is part of Tremotesf.
 *
 * Tremotesf is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Tremotesf is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.equeim.tremotesf.torrentfile

import android.os.Bundle
import androidx.annotation.CallSuper
import androidx.annotation.MainThread
import androidx.annotation.WorkerThread
import androidx.collection.SimpleArrayMap
import androidx.core.os.bundleOf
import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.equeim.tremotesf.common.AlphanumericComparator
import java.util.Comparator
import java.util.concurrent.Executors
import kotlin.coroutines.coroutineContext

open class TorrentFilesTree(parentScope: CoroutineScope) {
    val dispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    protected val scope = CoroutineScope(dispatcher + Job(parentScope.coroutineContext[Job]))

    sealed class Node(@Volatile var item: Item, val path: IntArray) {
        @CallSuper
        override fun equals(other: Any?): Boolean {
            return other is Node &&
                    other.item == item &&
                    other.path.contentEquals(path)
        }

        @CallSuper
        override fun hashCode(): Int {
            var result = item.hashCode()
            result = 31 * result + path.contentHashCode()
            return result
        }

        override fun toString() = "Node(item=$item)"

        @CallSuper
        open fun setItemWantedRecursively(wanted: Boolean, ids: MutableList<Int>) {
            item = item.copy(wantedState = Item.WantedState.fromBoolean(wanted))
        }

        @CallSuper
        open fun setItemPriorityRecursively(priority: Item.Priority, ids: MutableList<Int>) {
            item = item.copy(priority = priority)
        }
    }

    class FileNode(item: Item, path: IntArray) : Node(item, path) {
        override fun setItemWantedRecursively(wanted: Boolean, ids: MutableList<Int>) {
            super.setItemWantedRecursively(wanted, ids)
            ids.add(item.fileId)
        }

        override fun setItemPriorityRecursively(priority: Item.Priority, ids: MutableList<Int>) {
            super.setItemPriorityRecursively(priority, ids)
            ids.add(item.fileId)
        }
    }

    class DirectoryNode private constructor(item: Item, path: IntArray) : Node(item, path) {
        private val _children = mutableListOf<Node>()
        val children: List<Node>
            get() = _children
        private val childrenMap = SimpleArrayMap<String, Node>()

        override fun equals(other: Any?): Boolean {
            return super.equals(other) &&
                    other is DirectoryNode &&
                    other.children == children
        }

        override fun hashCode(): Int {
            var result = super.hashCode()
            result = 31 * result + children.hashCode()
            return result
        }

        fun getChildByItemNameOrNull(name: String) = childrenMap[name]

        fun recalculateFromChildren() {
            item = item.recalculatedFromChildren(children)
        }

        internal fun initiallyCalculateFromChildrenRecursively() {
            children.forEach { (it as? DirectoryNode)?.initiallyCalculateFromChildrenRecursively() }
            item.calculateFromChildren(children)
        }

        override fun setItemWantedRecursively(wanted: Boolean, ids: MutableList<Int>) {
            super.setItemWantedRecursively(wanted, ids)
            children.forEach { it.setItemWantedRecursively(wanted, ids) }
        }

        override fun setItemPriorityRecursively(priority: Item.Priority, ids: MutableList<Int>) {
            super.setItemPriorityRecursively(priority, ids)
            children.forEach { it.setItemPriorityRecursively(priority, ids) }
        }

        internal fun addFile(
            id: Int,
            name: String,
            size: Long,
            completedSize: Long,
            wantedState: Item.WantedState,
            priority: Item.Priority
        ): FileNode {
            if (id < 0) throw IllegalArgumentException("fileId can't be less than zero")
            val path = this.path + children.size
            val node = FileNode(
                Item(id, name, size, completedSize, wantedState, priority, path), path
            )
            addChild(name, node)
            return node
        }

        internal fun addDirectory(name: String): DirectoryNode {
            val path = this.path + children.size
            val node = DirectoryNode(
                Item(
                    -1,
                    name,
                    0,
                    0,
                    Item.WantedState.Wanted,
                    Item.Priority.Normal,
                    path
                ), path
            )
            addChild(name, node)
            return node
        }

        private fun addChild(name: String, node: Node) {
            _children.add(node)
            childrenMap.put(name, node)
        }

        companion object {
            fun createRootNode() = DirectoryNode(Item(), intArrayOf())
        }
    }

    data class Item(
        val fileId: Int = -1,
        val name: String = "",
        var size: Long = 0,
        var completedSize: Long = 0,
        var wantedState: WantedState = WantedState.Wanted,
        var priority: Priority = Priority.Normal,
        val nodePath: IntArray = intArrayOf()
    ) {
        val isDirectory: Boolean
            get() = (fileId == -1)

        val progress: Float
            get() {
                val bytes = size
                if (bytes == 0L) {
                    return 0.0f
                }
                return (completedSize.toFloat() / bytes.toFloat())
            }

        enum class WantedState {
            Wanted,
            Unwanted,
            Mixed;

            companion object {
                fun fromBoolean(wanted: Boolean): WantedState {
                    return if (wanted) Wanted
                    else Unwanted
                }
            }
        }

        enum class Priority {
            Low,
            Normal,
            High,
            Mixed
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as Item

            if (fileId != other.fileId) return false
            if (name != other.name) return false
            if (size != other.size) return false
            if (completedSize != other.completedSize) return false
            if (wantedState != other.wantedState) return false
            if (priority != other.priority) return false
            if (!nodePath.contentEquals(other.nodePath)) return false

            return true
        }

        override fun hashCode(): Int {
            var result = fileId
            result = 31 * result + name.hashCode()
            result = 31 * result + size.hashCode()
            result = 31 * result + completedSize.hashCode()
            result = 31 * result + wantedState.hashCode()
            result = 31 * result + priority.hashCode()
            result = 31 * result + nodePath.contentHashCode()
            return result
        }

        internal fun recalculatedFromChildren(children: List<Node>): Item =
            copy().apply { calculateFromChildren(children) }

        internal fun calculateFromChildren(children: List<Node>) {
            size = 0L
            completedSize = 0L
            children.first().item.let {
                wantedState = it.wantedState
                priority = it.priority
            }

            for (child in children) {
                val childItem = child.item
                size += childItem.size
                completedSize += childItem.completedSize
                if (wantedState != WantedState.Mixed && childItem.wantedState != wantedState) {
                    wantedState = WantedState.Mixed
                }
                if (priority != Priority.Mixed && childItem.priority != priority) {
                    priority = Priority.Mixed
                }
            }
        }
    }

    protected val comparator = object : Comparator<Item?> {
        private val nameComparator = AlphanumericComparator()

        override fun compare(item1: Item?, item2: Item?): Int {
            if (item1 == null) {
                if (item2 == null) {
                    return 0
                }
                return -1
            }
            if (item2 == null) {
                return 1
            }
            if (item1.isDirectory == item2.isDirectory) {
                return nameComparator.compare(item1.name, item2.name)
            }
            if (item1.isDirectory) {
                return -1
            }
            return 1
        }
    }

    private val savedStateKey = this::class.qualifiedName ?: TorrentFilesTree::class.qualifiedName!!

    @Volatile
    var rootNode = DirectoryNode.createRootNode()
        private set

    @Volatile
    private var inited = false

    @Volatile
    protected var currentNode: DirectoryNode = rootNode
        private set

    private val _items = MutableStateFlow(emptyList<Item?>())
    val items: StateFlow<List<Item?>> by ::_items

    fun navigateUp(): Boolean {
        if (!inited) return false
        if (currentNode == rootNode) {
            return false
        }
        val parent = findNodeByPath(currentNode.path.dropLast(1).asSequence()) ?: return false
        if (parent !is DirectoryNode) return false
        navigateTo(parent)
        return true
    }

    fun navigateDown(item: Item) {
        if (!inited) return
        if (!item.isDirectory) return
        val node = findNodeByPath(item.nodePath.asSequence()) ?: return
        if (node !is DirectoryNode) return
        navigateTo(node)
    }

    private fun navigateTo(node: DirectoryNode) {
        scope.launch {
            updateItemsWithSorting(node)
            currentNode = node
        }
    }

    @WorkerThread
    protected suspend fun updateItemsWithSorting(parentNode: DirectoryNode = currentNode) {
        val items = if (parentNode == rootNode) {
            ArrayList(parentNode.children.size)
        } else {
            ArrayList<Item?>(parentNode.children.size + 1).apply {
                add(null)
            }
        }
        items.addAll(parentNode.children.asSequence().map { it.item })
        items.sortWith(comparator)
        coroutineContext.ensureActive()
        _items.value = items
    }

    @WorkerThread
    private suspend fun updateItemsWithoutSorting() {
        val children = currentNode.children
        val items = items.value.map { if (it == null) it else children[it.nodePath.last()].item }
        coroutineContext.ensureActive()
        _items.value = items
    }

    private inline fun setItemsWantedOrPriority(
        nodeIndexes: List<Int>,
        crossinline nodeAction: Node.(MutableList<Int>) -> Unit,
        crossinline fileIdsAction: (IntArray) -> Unit
    ) {
        if (!inited) return
        scope.launch {
            val ids = mutableListOf<Int>()
            val currentNode = currentNode
            val children = currentNode.children
            for (index in nodeIndexes) {
                if (!isActive) return@launch
                children[index].nodeAction(ids)
            }

            val nodes = ArrayList<DirectoryNode>(currentNode.path.size)
            var node: DirectoryNode = rootNode
            for (index in currentNode.path) {
                node = (node.children.getOrNull(index) as? DirectoryNode) ?: break
                nodes.add(node)
            }
            nodes.reverse()
            for (n in nodes) {
                n.recalculateFromChildren()
            }

            updateItemsWithoutSorting()
            withContext(Dispatchers.Main) {
                fileIdsAction(ids.toIntArray())
            }
        }
    }

    fun setItemsWanted(nodeIndexes: List<Int>, wanted: Boolean) {
        setItemsWantedOrPriority(
            nodeIndexes,
            { setItemWantedRecursively(wanted, it) },
            { onSetFilesWanted(it, wanted) })
    }

    fun setItemsPriority(nodeIndexes: List<Int>, priority: Item.Priority) = scope.launch {
        setItemsWantedOrPriority(
            nodeIndexes,
            { setItemPriorityRecursively(priority, it) },
            { onSetFilesPriority(it, priority) })
    }

    fun getItemPath(item: Item): String? {
        val pathParts = mutableListOf<String>()
        var node: Node = rootNode
        for (index in item.nodePath) {
            node = (node as? DirectoryNode)?.children?.get(index) ?: break
            pathParts.add(node.item.name)
        }
        return if (pathParts.isEmpty()) null
        else pathParts.joinToString("/")
    }

    fun renameFile(path: String, newName: String) = scope.launch {
        val pathParts = path.split('/').filter(String::isNotEmpty)
        var node: Node? = rootNode
        var updateItems = false
        for (part in pathParts) {
            if (node == currentNode) {
                updateItems = true
            }
            node = (node as? DirectoryNode)?.getChildByItemNameOrNull(part)
            if (node == null) {
                break
            }
        }
        if (node != null && node != rootNode) {
            ensureActive()

            node.item = node.item.copy(name = newName)

            if (updateItems) {
                updateItemsWithSorting(currentNode)
            }
        }
    }

    protected open fun onSetFilesWanted(ids: IntArray, wanted: Boolean) = Unit
    protected open fun onSetFilesPriority(ids: IntArray, priority: Item.Priority) = Unit

    @MainThread
    fun init(
        rootNode: DirectoryNode,
        savedStateHandle: SavedStateHandle
    ) {
        this.rootNode = rootNode
        currentNode = rootNode
        inited = true
        if (!restoreInstanceState(savedStateHandle)) {
            navigateTo(rootNode)
        }
        savedStateHandle.setSavedStateProvider(savedStateKey, ::saveInstanceState)
    }

    @MainThread
    private fun restoreInstanceState(savedStateHandle: SavedStateHandle): Boolean {
        val path = savedStateHandle.get<Bundle>(savedStateKey)?.getIntArray("") ?: return false
        val node = findNodeByPath(path.asSequence()) as? DirectoryNode
        navigateTo(node ?: rootNode)
        return true
    }

    @MainThread
    private fun saveInstanceState(): Bundle {
        return bundleOf("" to currentNode.path)
    }

    @MainThread
    open fun reset() {
        scope.coroutineContext.cancelChildren()
        rootNode = DirectoryNode.createRootNode()
        currentNode = rootNode
        _items.value = emptyList()
        inited = false
    }

    private fun findNodeByPath(path: Sequence<Int>): Node? {
        var node: Node = rootNode
        for (index in path) {
            node = (node as? DirectoryNode)?.children?.getOrNull(index) ?: return null
        }
        return node
    }
}

interface TorrentFilesTreeBuilderScope {
    fun addFile(
        fileId: Int,
        path: List<String>,
        size: Long,
        completedSize: Long,
        wantedState: TorrentFilesTree.Item.WantedState,
        priority: TorrentFilesTree.Item.Priority
    )
}

data class TorrentFilesTreeBuildResult(
    val rootNode: TorrentFilesTree.DirectoryNode,
    val files: List<TorrentFilesTree.Node>
)

fun buildTorrentFilesTree(block: TorrentFilesTreeBuilderScope.() -> Unit): TorrentFilesTreeBuildResult {
    val rootNode = TorrentFilesTree.DirectoryNode.createRootNode()
    val files = mutableListOf<TorrentFilesTree.Node>()

    val scope = object : TorrentFilesTreeBuilderScope {
        override fun addFile(
            fileId: Int,
            path: List<String>,
            size: Long,
            completedSize: Long,
            wantedState: TorrentFilesTree.Item.WantedState,
            priority: TorrentFilesTree.Item.Priority
        ) {
            var currentNode = rootNode

            val lastPartIndex = (path.size - 1)

            for ((partIndex, part: String) in path.withIndex()) {
                if (partIndex == lastPartIndex) {
                    val node = currentNode.addFile(
                        fileId,
                        part,
                        size,
                        completedSize,
                        wantedState,
                        priority
                    )
                    files.add(node)
                } else {
                    var childDirectoryNode = currentNode.getChildByItemNameOrNull(part) as TorrentFilesTree.DirectoryNode?
                    if (childDirectoryNode == null) {
                        childDirectoryNode = currentNode.addDirectory(part)
                    }
                    currentNode = childDirectoryNode
                }
            }
        }
    }
    scope.block()
    rootNode.children.forEach {
        (it as? TorrentFilesTree.DirectoryNode)?.initiallyCalculateFromChildrenRecursively()
    }
    return TorrentFilesTreeBuildResult(rootNode, files)
}
