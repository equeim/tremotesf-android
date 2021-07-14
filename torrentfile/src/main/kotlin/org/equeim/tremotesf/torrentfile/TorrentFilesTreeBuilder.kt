package org.equeim.tremotesf.torrentfile

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
    val files: List<TorrentFilesTree.FileNode>
)

fun buildTorrentFilesTree(block: TorrentFilesTreeBuilderScope.() -> Unit): TorrentFilesTreeBuildResult {
    val rootNode = TorrentFilesTree.DirectoryNode.createRootNode()
    val files = mutableListOf<TorrentFilesTree.FileNode>()

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
                    if (partIndex == 0 && currentNode.children.isNotEmpty()) {
                        throw IllegalArgumentException("There can be only one top-level node in a tree")
                    }
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
                    val childNode = currentNode.getChildByItemNameOrNull(part)
                    currentNode = when (childNode) {
                        null -> {
                            if (partIndex == 0 && currentNode.children.isNotEmpty()) {
                                throw IllegalArgumentException("There can be only one top-level node in a tree")
                            }
                            currentNode.addDirectory(part)
                        }
                        !is TorrentFilesTree.DirectoryNode -> {
                            throw IllegalArgumentException("Node that is expected to be directory was already added as file")
                        }
                        else -> childNode
                    }
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
