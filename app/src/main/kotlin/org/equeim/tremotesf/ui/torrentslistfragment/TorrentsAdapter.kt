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
import android.graphics.drawable.Drawable
import android.os.Build
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.view.ActionMode
import androidx.recyclerview.widget.DiffUtil
import org.equeim.libtremotesf.TorrentData
import org.equeim.tremotesf.R
import org.equeim.tremotesf.data.rpc.Rpc
import org.equeim.tremotesf.data.rpc.Torrent
import org.equeim.tremotesf.databinding.TorrentListItemBinding
import org.equeim.tremotesf.databinding.TorrentListItemCompactBinding
import org.equeim.tremotesf.ui.SelectionTracker
import org.equeim.tremotesf.ui.Settings
import org.equeim.tremotesf.ui.utils.DecimalFormats
import org.equeim.tremotesf.ui.utils.StateRestoringListAdapter
import org.equeim.tremotesf.ui.utils.Utils


class TorrentsAdapter(private val fragment: TorrentsListFragment) :
    StateRestoringListAdapter<Torrent, TorrentsAdapter.BaseTorrentsViewHolder>(Callback()) {
    private val selectionTracker = SelectionTracker.createForIntKeys(
        this,
        true,
        fragment,
        ::ActionModeCallback,
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
        return Rpc.isConnected.value
    }

    override fun onStateRestored() {
        selectionTracker.restoreInstanceState()
    }

    inner class TorrentsViewHolder(
        multilineName: Boolean,
        private val binding: TorrentListItemBinding
    ) : BaseTorrentsViewHolder(multilineName, binding.root) {
        init {
            Utils.setProgressBarColor(binding.progressBar)
        }

        override fun update() {
            super.update()

            with(binding) {
                sizeTextView.text = if (torrent.isFinished) {
                    context.getString(
                        R.string.uploaded_string,
                        Utils.formatByteSize(context, torrent.sizeWhenDone),
                        Utils.formatByteSize(context, torrent.totalUploaded)
                    )
                } else {
                    context.getString(
                        R.string.completed_string,
                        Utils.formatByteSize(context, torrent.completedSize),
                        Utils.formatByteSize(context, torrent.sizeWhenDone),
                        DecimalFormats.generic.format(torrent.percentDone * 100)
                    )
                }
                etaTextView.text = Utils.formatDuration(context, torrent.eta)

                progressBar.progress = (torrent.percentDone * 100).toInt()
                downloadSpeedTextView.text = context.getString(
                    R.string.download_speed_string,
                    Utils.formatByteSpeed(
                        context,
                        torrent.downloadSpeed
                    )
                )
                uploadSpeedTextView.text = context.getString(
                    R.string.upload_speed_string,
                    Utils.formatByteSpeed(
                        context,
                        torrent.uploadSpeed
                    )
                )

                statusTextView.text = torrent.statusString
            }
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
                    Utils.formatByteSpeed(
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
                    Utils.formatByteSpeed(
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
        private val statusIconDrawable: Drawable =
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR1) {
                nameTextView.compoundDrawables.first()
            } else {
                nameTextView.compoundDrawablesRelative.first()
            }
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
            statusIconDrawable.level = when (torrent.status) {
                TorrentData.Status.Paused -> 0
                TorrentData.Status.Downloading,
                TorrentData.Status.StalledDownloading,
                TorrentData.Status.QueuedForDownloading -> 1
                TorrentData.Status.Seeding,
                TorrentData.Status.StalledSeeding,
                TorrentData.Status.QueuedForSeeding -> 2
                TorrentData.Status.Checking,
                TorrentData.Status.QueuedForChecking -> 3
                TorrentData.Status.Errored -> 4
                else -> 0
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

    private inner class ActionModeCallback(selectionTracker: SelectionTracker<Int>) :
        SelectionTracker.ActionModeCallback<Int>(selectionTracker) {

        override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
            mode.menuInflater.inflate(R.menu.torrents_context_menu, menu)
            return true
        }

        override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
            super.onPrepareActionMode(mode, menu)

            if (selectionTracker.selectedCount == 1) {
                val startEnabled = when (getFirstSelectedTorrent().status) {
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

            val getTorrentIds = {
                selectionTracker.selectedKeys.toIntArray()
            }

            when (item.itemId) {
                R.id.start -> Rpc.nativeInstance.startTorrents(getTorrentIds())
                R.id.pause -> Rpc.nativeInstance.pauseTorrents(getTorrentIds())
                R.id.check -> Rpc.nativeInstance.checkTorrents(getTorrentIds())
                R.id.start_now -> Rpc.nativeInstance.startTorrentsNow(getTorrentIds())
                R.id.reannounce -> Rpc.nativeInstance.reannounceTorrents(getTorrentIds())
                R.id.set_location -> {
                    fragment.navigate(
                        TorrentsListFragmentDirections.toTorrentSetLocationDialog(
                            getTorrentIds(),
                            getFirstSelectedTorrent().downloadDirectory
                        )
                    )
                }
                R.id.rename -> {
                    val torrent = getFirstSelectedTorrent()
                    fragment.navigate(
                        TorrentsListFragmentDirections.toTorrentFileRenameDialog(
                            torrent.name,
                            torrent.name,
                            torrent.id
                        )
                    )
                }
                R.id.remove -> fragment.navigate(
                    TorrentsListFragmentDirections.toRemoveTorrentDialog(
                        getTorrentIds()
                    )
                )
                R.id.share -> {
                    val magnetLinks =
                        currentList.slice(selectionTracker.getSelectedPositionsUnsorted().sorted())
                            .map { it.data.magnetLink }
                    Utils.shareTorrents(magnetLinks, fragment.requireContext())
                }
                else -> return false
            }

            return true
        }

        private fun getFirstSelectedTorrent(): Torrent {
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
