// SPDX-FileCopyrightText: 2017-2024 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package org.equeim.tremotesf.ui.torrentpropertiesfragment

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import org.equeim.tremotesf.R
import org.equeim.tremotesf.databinding.TorrentFileListItemBinding
import org.equeim.tremotesf.rpc.requests.FileSize
import org.equeim.tremotesf.ui.BaseTorrentFilesAdapter
import org.equeim.tremotesf.ui.SelectionTracker
import org.equeim.tremotesf.ui.navigate
import org.equeim.tremotesf.ui.utils.DecimalFormats
import org.equeim.tremotesf.ui.utils.FormatUtils
import org.equeim.tremotesf.ui.utils.bindingAdapterPositionOrNull


class TorrentFilesAdapter(
    private val model: TorrentFilesFragmentViewModel,
    private val fragment: TorrentFilesFragment,
) : BaseTorrentFilesAdapter(model.filesTree, fragment) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        if (viewType == TYPE_ITEM) {
            val binding = TorrentFileListItemBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
            return ItemHolder(this, selectionTracker, binding)
        }
        return super.onCreateViewHolder(parent, viewType)
    }

    override fun navigateToRenameDialog(path: String, name: String) {
        fragment.navigate(
            TorrentPropertiesFragmentDirections
                .toTorrentFileRenameDialog(path, name, model.torrentHashString)
        )
    }

    private class ItemHolder(
        private val adapter: TorrentFilesAdapter,
        selectionTracker: SelectionTracker<Int>,
        val binding: TorrentFileListItemBinding,
    ) : BaseItemHolder(adapter, selectionTracker, binding.root) {
        override fun update() {
            super.update()
            val item = bindingAdapterPositionOrNull?.let(adapter::getItem) ?: return
            with(binding) {
                progressBar.progress = (item.progress * 100).toInt()
                val context = progressBar.context
                progressTextView.text = context.getString(
                    R.string.completed_string,
                    FormatUtils.formatFileSize(
                        context,
                        FileSize.fromBytes(item.completedSize)
                    ),
                    FormatUtils.formatFileSize(
                        context,
                        FileSize.fromBytes(item.size)
                    ),
                    DecimalFormats.generic.format(item.progress * 100)
                )
            }
        }
    }
}
