// SPDX-FileCopyrightText: 2017-2024 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package org.equeim.tremotesf.ui.addtorrent

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.equeim.tremotesf.R
import org.equeim.tremotesf.common.AlphanumericComparator
import org.equeim.tremotesf.rpc.GlobalRpcClient
import org.equeim.tremotesf.rpc.GlobalServers
import org.equeim.tremotesf.rpc.RpcRequestError
import org.equeim.tremotesf.rpc.normalizePath
import org.equeim.tremotesf.rpc.requests.getDownloadDirectory
import org.equeim.tremotesf.rpc.requests.getTorrentsDownloadDirectories
import org.equeim.tremotesf.rpc.toNativeSeparators
import org.equeim.tremotesf.ui.utils.BaseDropdownAdapter
import timber.log.Timber


class AddTorrentDirectoriesAdapter(
    coroutineScope: CoroutineScope,
    savedInstanceState: Bundle?,
) : BaseDropdownAdapter(
    R.layout.download_directory_dropdown_item,
    android.R.id.text1
) {
    companion object {
        private const val STATE_KEY =
            "org.equeim.tremotesf.ui.addtorrent.AddTorrentDirectoriesAdapter.items"
    }

    private var items = ArrayList<String>()

    init {
        val saved = savedInstanceState?.getStringArrayList(STATE_KEY)
        if (saved != null) {
            items = saved
        } else {
            coroutineScope.launch {
                val directories = sortedSetOf(AlphanumericComparator())
                val torrentsDirectories = try {
                    GlobalRpcClient.getTorrentsDownloadDirectories()
                } catch (e: RpcRequestError) {
                    Timber.e(e, "Failed to get torrents download directories")
                    emptyList()
                }
                val serverCapabilities = GlobalRpcClient.serverCapabilities
                torrentsDirectories.mapTo(directories) { it.toNativeSeparators() }
                GlobalServers.serversState.value.currentServer?.lastDownloadDirectories
                    ?.mapTo(directories) { it.normalizePath(serverCapabilities).toNativeSeparators() }
                try {
                    directories.add(GlobalRpcClient.getDownloadDirectory().toNativeSeparators())
                } catch (e: RpcRequestError) {
                    Timber.e(e, "Failed to get default download directory")
                }
                items = ArrayList(directories)
                notifyDataSetChanged()
            }
        }
    }

    override fun getCount() = items.size
    override fun getItem(position: Int) = items[position]

    override fun createViewHolder(view: View): BaseViewHolder = ViewHolder(view)

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = super.getView(position, convertView, parent)
        (view.tag as ViewHolder).position = position
        return view
    }

    fun remove(position: Int) {
        items.removeAt(position)
        notifyDataSetChanged()
    }

    fun save(textEdit: EditText) {
        val serverCapabilities = GlobalRpcClient.serverCapabilities
        val directories = items.mapTo(ArrayList(items.size + 1)) { it.normalizePath(serverCapabilities).value }
        val editPath = textEdit.text.toString().normalizePath(serverCapabilities).value
        if (!directories.contains(editPath)) {
            directories.add(editPath)
        }
        GlobalServers.serversState.value.currentServer?.let { current ->
            if (current.lastDownloadDirectories != directories || current.lastDownloadDirectory != editPath) {
                GlobalServers.addOrReplaceServer(
                    current.copy(
                        lastDownloadDirectories = directories,
                        lastDownloadDirectory = editPath
                    )
                )
            }
        }
    }

    fun saveInstanceState(outState: Bundle) {
        outState.putStringArrayList(STATE_KEY, items)
    }

    private inner class ViewHolder(view: View) : BaseViewHolder(view) {
        var position = -1

        init {
            view.findViewById<View>(R.id.remove_button).setOnClickListener {
                if (count > 1) {
                    remove(position)
                }
            }
        }
    }
}
