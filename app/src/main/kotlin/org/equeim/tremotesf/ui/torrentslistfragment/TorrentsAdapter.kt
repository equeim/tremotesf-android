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

package org.equeim.tremotesf.ui.torrentslistfragment

import android.annotation.SuppressLint
import android.content.Context
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.appcompat.view.ActionMode
import androidx.recyclerview.widget.DiffUtil
import org.equeim.libtremotesf.TorrentData
import org.equeim.tremotesf.R
import org.equeim.tremotesf.torrentfile.rpc.Torrent
import org.equeim.tremotesf.databinding.TorrentListItemBinding
import org.equeim.tremotesf.databinding.TorrentListItemCompactBinding
import org.equeim.tremotesf.rpc.GlobalRpc
import org.equeim.tremotesf.rpc.statusString
import org.equeim.tremotesf.ui.SelectionTracker
import org.equeim.tremotesf.ui.Settings
import org.equeim.tremotesf.ui.utils.DecimalFormats
import org.equeim.tremotesf.ui.utils.FormatUtils
import org.equeim.tremotesf.ui.utils.StateRestoringListAdapter
import org.equeim.tremotesf.ui.utils.Utils
import java.lang.ref.WeakReference


class TorrentsAdapter(private val fragment: TorrentsListFragment) :
    StateRestoringListAdapter<Torrent, TorrentsAdapter.BaseTorrentsViewHolder>(Callback()) {
    private val selectionTracker = SelectionTracker.createForIntKeys(
        this,
        true,
        fragment,
        { ActionModeCallback(this, it) },
        R.plurals.torrents_selected
    ) {
        getItem(it).id
    }

    private val compactView = Settings.torrentCompactView
    private val multilineName = Settings.torrentNameMultiline

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BaseTorrentsViewHolder {
        if (compactView) {
            return TorrentsViewHolderCompact(
                multilineName,
                TorrentListItemCompactBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
            )
        }
        return TorrentsViewHolder(
            multilineName,
            TorrentListItemBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
        )
    }

    override fun onBindViewHolder(holder: BaseTorrentsViewHolder, position: Int) {
        holder.update()
    }

    fun update(torrents: List<Torrent>) {
        submitList(torrents) {
            selectionTracker.commitAdapterUpdate()
        }
    }

    override fun allowStateRestoring(): Boolean {
        return GlobalRpc.isConnected.value
    }

    override fun onStateRestored() {
        selectionTracker.restoreInstanceState()
    }

    inner class TorrentsViewHolder(
        multilineName: Boolean,
        private val binding: TorrentListItemBinding
    ) : BaseTorrentsViewHolder(multilineName, binding.root) {
        override fun update() {
            super.update()

            with(binding) {
                sizeTextView.text = if (torrent.isFinished) {
                    context.getString(
                        R.string.uploaded_string,
                        FormatUtils.formatByteSize(context, torrent.sizeWhenDone),
                        FormatUtils.formatByteSize(context, torrent.totalUploaded)
                    )
                } else {
                    context.getString(
                        R.string.completed_string,
                        FormatUtils.formatByteSize(context, torrent.completedSize),
                        FormatUtils.formatByteSize(context, torrent.sizeWhenDone),
                        DecimalFormats.generic.format(torrent.percentDone * 100)
                    )
                }
                etaTextView.text = FormatUtils.formatDuration(context, torrent.eta)

                progressBar.progress = (torrent.percentDone * 100).toInt()
                downloadSpeedTextView.text = context.getString(
                    R.string.download_speed_string,
                    FormatUtils.formatByteSpeed(
                        context,
                        torrent.downloadSpeed
                    )
                )
                uploadSpeedTextView.text = context.getString(
                    R.string.upload_speed_string,
                    FormatUtils.formatByteSpeed(
                        context,
                        torrent.uploadSpeed
                    )
                )

                statusTextView.text = torrent.statusString
            }
        }

        override fun updateSelectionState(isSelected: Boolean) {
            binding.root.isChecked = isSelected
        }
    }

    inner class TorrentsViewHolderCompact(
        multilineName: Boolean,
        private val binding: TorrentListItemCompactBinding
    ) : BaseTorrentsViewHolder(multilineName, binding.root) {
        override fun update() {
            super.update()

            downloadSpeedTextView.text = if (torrent.downloadSpeed == 0L) {
                ""
            } else {
                context.getString(
                    R.string.download_speed_string,
                    FormatUtils.formatByteSpeed(
                        context,
                        torrent.downloadSpeed
                    )
                )
            }

            uploadSpeedTextView.text = if (torrent.uploadSpeed == 0L) {
                ""
            } else {
                context.getString(
                    R.string.upload_speed_string,
                    FormatUtils.formatByteSpeed(
                        context,
                        torrent.uploadSpeed
                    )
                )
            }

            binding.progressTextView.text = context.getString(
                if (torrent.downloadSpeed != 0L || torrent.uploadSpeed != 0L) R.string.progress_string_with_dot else R.string.progress_string,
                DecimalFormats.generic.format(torrent.percentDone * 100)
            )
        }
    }

    open inner class BaseTorrentsViewHolder(
        multilineName: Boolean,
        itemView: View
    ) : SelectionTracker.ViewHolder<Int>(selectionTracker, itemView) {
        protected lateinit var torrent: Torrent
            private set

        protected val context: Context = itemView.context

        private val nameTextView = itemView.findViewById<TextView>(R.id.name_text_view)!!
        @DrawableRes private var iconResId = 0
        protected val downloadSpeedTextView =
            itemView.findViewById<TextView>(R.id.download_speed_text_view)!!
        protected val uploadSpeedTextView =
            itemView.findViewById<TextView>(R.id.upload_speed_text_view)!!

        init {
            if (!multilineName) {
                nameTextView.ellipsize = TextUtils.TruncateAt.END
                nameTextView.maxLines = 1
                nameTextView.isSingleLine = true
            }
        }

        override fun update() {
            super.update()

            torrent = getItem(bindingAdapterPosition)

            nameTextView.text = torrent.name
            val resId = when (torrent.status) {
                TorrentData.Status.Paused -> R.drawable.ic_pause_24dp
                TorrentData.Status.Downloading,
                TorrentData.Status.StalledDownloading,
                TorrentData.Status.QueuedForDownloading -> R.drawable.ic_arrow_downward_24dp
                TorrentData.Status.Seeding,
                TorrentData.Status.StalledSeeding,
                TorrentData.Status.QueuedForSeeding -> R.drawable.ic_arrow_upward_24dp
                TorrentData.Status.Checking,
                TorrentData.Status.QueuedForChecking -> R.drawable.ic_refresh_24dp
                TorrentData.Status.Errored -> R.drawable.ic_error_24dp
                else -> 0
            }
            if (resId != iconResId) {
                nameTextView.setCompoundDrawablesRelativeWithIntrinsicBounds(resId, 0, 0, 0)
                iconResId = resId
            }
        }

        override fun onClick(view: View) {
            fragment.navigate(
                TorrentsListFragmentDirections.toTorrentPropertiesFragment(
                    torrent.hashString,
                    torrent.name
                )
            )
        }
    }

    private class ActionModeCallback(adapter: TorrentsAdapter, selectionTracker: SelectionTracker<Int>) :
        SelectionTracker.ActionModeCallback<Int>(selectionTracker) {

        private val adapter = WeakReference(adapter)

        override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
            mode.menuInflater.inflate(R.menu.torrents_context_menu, menu)
            return true
        }

        override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
            super.onPrepareActionMode(mode, menu)

            if (selectionTracker?.selectedCount == 1) {
                val startEnabled = when (adapter.get()?.getFirstSelectedTorrent()?.status) {
                    TorrentData.Status.Paused,
                    TorrentData.Status.Errored -> true
                    else -> false
                }
                for (id in intArrayOf(R.id.start, R.id.start_now)) {
                    menu.findItem(id).isEnabled = startEnabled
                }
                menu.findItem(R.id.pause).isEnabled = !startEnabled
            } else {
                menu.setGroupEnabled(0, true)
            }

            return true
        }

        override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
            if (super.onActionItemClicked(mode, item)) {
                return true
            }

            val selectionTracker = this.selectionTracker ?: return false
            val adapter = this.adapter.get() ?: return false

            val getTorrentIds = {
                selectionTracker.selectedKeys.toIntArray()
            }

            when (item.itemId) {
                R.id.start -> GlobalRpc.nativeInstance.startTorrents(getTorrentIds())
                R.id.pause -> GlobalRpc.nativeInstance.pauseTorrents(getTorrentIds())
                R.id.check -> GlobalRpc.nativeInstance.checkTorrents(getTorrentIds())
                R.id.start_now -> GlobalRpc.nativeInstance.startTorrentsNow(getTorrentIds())
                R.id.reannounce -> GlobalRpc.nativeInstance.reannounceTorrents(getTorrentIds())
                R.id.set_location -> {
                    activity.navigate(
                        TorrentsListFragmentDirections.toTorrentSetLocationDialog(
                            getTorrentIds(),
                            adapter.getFirstSelectedTorrent().downloadDirectory
                        )
                    )
                }
                R.id.rename -> {
                    val torrent = adapter.getFirstSelectedTorrent()
                    activity.navigate(
                        TorrentsListFragmentDirections.toTorrentFileRenameDialog(
                            torrent.name,
                            torrent.name,
                            torrent.id
                        )
                    )
                }
                R.id.remove -> activity.navigate(
                    TorrentsListFragmentDirections.toRemoveTorrentDialog(
                        getTorrentIds()
                    )
                )
                R.id.share -> {
                    val magnetLinks =
                        adapter.currentList.slice(selectionTracker.getSelectedPositionsUnsorted().sorted())
                            .map { it.data.magnetLink }
                    Utils.shareTorrents(magnetLinks, activity)
                }
                else -> return false
            }

            return true
        }

        private fun TorrentsAdapter.getFirstSelectedTorrent(): Torrent {
            return getItem(selectionTracker.getFirstSelectedPosition())
        }
    }

    private class Callback : DiffUtil.ItemCallback<Torrent>() {
        override fun areItemsTheSame(oldItem: Torrent, newItem: Torrent): Boolean {
            return oldItem.id == newItem.id
        }

        @SuppressLint("DiffUtilEquals")
        override fun areContentsTheSame(oldItem: Torrent, newItem: Torrent): Boolean {
            return oldItem === newItem
        }
    }
}
