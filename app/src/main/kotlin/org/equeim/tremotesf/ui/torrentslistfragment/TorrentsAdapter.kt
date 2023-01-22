/*
 * Copyright (C) 2017-2022 Alexey Rochev <equeim@gmail.com>
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
import android.view.*
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.appcompat.view.ActionMode
import androidx.recyclerview.widget.DiffUtil
import org.equeim.libtremotesf.IntVector
import org.equeim.libtremotesf.TorrentData
import org.equeim.tremotesf.R
import org.equeim.tremotesf.databinding.TorrentListItemBinding
import org.equeim.tremotesf.databinding.TorrentListItemCompactBinding
import org.equeim.tremotesf.rpc.GlobalRpc
import org.equeim.tremotesf.torrentfile.rpc.Torrent
import org.equeim.tremotesf.ui.SelectionTracker
import org.equeim.tremotesf.ui.utils.*
import java.lang.ref.WeakReference


class TorrentsAdapter(
    private val fragment: TorrentsListFragment,
    private val compactView: Boolean,
    private val multilineName: Boolean
) :
    AsyncLoadingListAdapter<Torrent, TorrentsAdapter.BaseTorrentsViewHolder>(Callback()) {
    private val selectionTracker = SelectionTracker.createForIntKeys(
        this,
        true,
        fragment,
        { ActionModeCallback(this, it) },
        R.plurals.torrents_selected
    ) {
        getItem(it).id
    }

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

    suspend fun update(torrents: List<Torrent>?) {
        submitListAwait(torrents)
        selectionTracker.commitAdapterUpdate()
    }

    override fun onStateRestored() {
        selectionTracker.restoreInstanceState()
    }

    inner class TorrentsViewHolder(
        multilineName: Boolean,
        private val binding: TorrentListItemBinding
    ) : BaseTorrentsViewHolder(multilineName, binding.root) {
        override fun update() {
            val oldTorrent = torrent
            super.update()
            val torrent = this.torrent ?: return

            with(binding) {
                if (torrent.isFinished) {
                    if (oldTorrent?.isFinished != torrent.isFinished ||
                        oldTorrent.sizeWhenDone != torrent.sizeWhenDone ||
                        oldTorrent.totalUploaded != torrent.totalUploaded
                    ) {
                        sizeTextView.text =
                            context.getString(
                                R.string.uploaded_string,
                                FormatUtils.formatByteSize(context, torrent.sizeWhenDone),
                                FormatUtils.formatByteSize(context, torrent.totalUploaded)
                            )
                    }
                } else {
                    if (oldTorrent?.isFinished != torrent.isFinished ||
                        oldTorrent.completedSize != torrent.completedSize ||
                        oldTorrent.sizeWhenDone != torrent.sizeWhenDone
                    ) {
                        sizeTextView.text = context.getString(
                            R.string.completed_string,
                            FormatUtils.formatByteSize(context, torrent.completedSize),
                            FormatUtils.formatByteSize(context, torrent.sizeWhenDone),
                            DecimalFormats.generic.format(torrent.percentDone * 100)
                        )
                    }
                }

                if (oldTorrent?.eta != torrent.eta) {
                    etaTextView.text = FormatUtils.formatDuration(context, torrent.eta)
                }

                progressBar.progress = (torrent.percentDone * 100).toInt()

                if (oldTorrent?.downloadSpeed != torrent.downloadSpeed) {
                    downloadSpeedTextView.text = context.getString(
                        R.string.download_speed_string,
                        FormatUtils.formatByteSpeed(
                            context,
                            torrent.downloadSpeed
                        )
                    )
                }

                if (oldTorrent?.uploadSpeed != torrent.uploadSpeed) {
                    uploadSpeedTextView.text = context.getString(
                        R.string.upload_speed_string,
                        FormatUtils.formatByteSpeed(
                            context,
                            torrent.uploadSpeed
                        )
                    )
                }

                torrent.getStatusString(context).let {
                    if (!statusTextView.text.contentEquals(it)) {
                        statusTextView.text = it
                    }
                }
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
            val oldTorrent = torrent
            super.update()
            val torrent = this.torrent ?: return

            val speedLabelsWereEmpty = downloadSpeedTextView.text.isEmpty() && uploadSpeedTextView.text.isEmpty()

            if (oldTorrent?.downloadSpeed != torrent.downloadSpeed) {
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
            }

            if (oldTorrent?.uploadSpeed != torrent.uploadSpeed) {
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
            }

            val speedLabelsAreEmpty = downloadSpeedTextView.text.isEmpty() && uploadSpeedTextView.text.isEmpty()

            if (speedLabelsWereEmpty != speedLabelsAreEmpty ||
                !(oldTorrent?.percentDone fuzzyEquals torrent.percentDone)
            ) {
                binding.progressTextView.text = context.getString(
                    if (torrent.downloadSpeed != 0L || torrent.uploadSpeed != 0L) R.string.progress_string_with_dot else R.string.progress_string,
                    DecimalFormats.generic.format(torrent.percentDone * 100)
                )
            }
        }
    }

    open inner class BaseTorrentsViewHolder(
        multilineName: Boolean,
        itemView: View
    ) : SelectionTracker.ViewHolder<Int>(selectionTracker, itemView) {
        protected var torrent: Torrent? = null
            private set

        protected val context: Context = itemView.context

        private val nameTextView = itemView.findViewById<TextView>(R.id.name_text_view)!!
        @DrawableRes
        private var iconResId = 0
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

            val torrent = bindingAdapterPositionOrNull?.let(::getItem) ?: return
            val oldTorrent = this.torrent
            this.torrent = torrent

            if (oldTorrent?.name != torrent.name) {
                nameTextView.text = torrent.name
            }
            val resId = if (torrent.hasError) {
                R.drawable.ic_error_24dp
            } else {
                when (torrent.status) {
                    TorrentData.Status.Paused -> R.drawable.ic_pause_24dp
                    TorrentData.Status.Downloading,
                    TorrentData.Status.QueuedForDownloading -> R.drawable.ic_arrow_downward_24dp
                    TorrentData.Status.Seeding,
                    TorrentData.Status.QueuedForSeeding -> R.drawable.ic_arrow_upward_24dp
                    TorrentData.Status.Checking,
                    TorrentData.Status.QueuedForChecking -> R.drawable.ic_refresh_24dp
                }
            }
            if (resId != iconResId) {
                nameTextView.setCompoundDrawablesRelativeWithIntrinsicBounds(resId, 0, 0, 0)
                iconResId = resId
            }
        }

        override fun onClick(view: View) {
            val torrent = this.torrent ?: return
            fragment.navigate(
                TorrentsListFragmentDirections.toTorrentPropertiesFragment(
                    torrent.hashString,
                    torrent.name
                )
            )
        }
    }

    private class ActionModeCallback(
        adapter: TorrentsAdapter,
        selectionTracker: SelectionTracker<Int>
    ) :
        SelectionTracker.ActionModeCallback<Int>(selectionTracker) {

        private val adapter = WeakReference(adapter)

        override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
            mode.menuInflater.inflate(R.menu.torrents_context_menu, menu)
            return true
        }

        override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
            super.onPrepareActionMode(mode, menu)

            if (selectionTracker?.selectedCount == 1) {
                val startEnabled = adapter.get()?.getFirstSelectedTorrent()?.status == TorrentData.Status.Paused
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

            when (item.itemId) {
                R.id.start -> GlobalRpc.nativeInstance.startTorrents(IntVector(selectionTracker.selectedKeys))
                R.id.pause -> GlobalRpc.nativeInstance.pauseTorrents(IntVector(selectionTracker.selectedKeys))
                R.id.check -> GlobalRpc.nativeInstance.checkTorrents(IntVector(selectionTracker.selectedKeys))
                R.id.start_now -> GlobalRpc.nativeInstance.startTorrentsNow(IntVector(selectionTracker.selectedKeys))
                R.id.reannounce -> GlobalRpc.nativeInstance.reannounceTorrents(IntVector(selectionTracker.selectedKeys))
                R.id.set_location -> {
                    activity.navigate(
                        TorrentsListFragmentDirections.toTorrentSetLocationDialog(
                            selectionTracker.selectedKeys.toIntArray(),
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
                        selectionTracker.selectedKeys.toIntArray()
                    )
                )
                R.id.share -> {
                    val magnetLinks =
                        adapter.currentList.slice(
                            selectionTracker.getSelectedPositionsUnsorted().sorted()
                        )
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

private fun Torrent.getStatusString(context: Context): CharSequence {
    return when (status) {
        TorrentData.Status.Paused -> if (hasError) {
            context.getString(R.string.torrent_paused_with_error, errorString)
        } else {
            context.getText(R.string.torrent_paused)
        }
        TorrentData.Status.Downloading -> if (isDownloadingStalled) {
            if (hasError) {
                context.getString(R.string.torrent_downloading_stalled_with_error, errorString)
            } else {
                context.getText(R.string.torrent_downloading_stalled)
            }
        } else {
            val seeders = this.seeders + this.activeWebSeeders
            if (hasError) {
                context.resources.getQuantityString(
                    R.plurals.torrent_downloading_with_error,
                    seeders,
                    seeders,
                    errorString
                )
            } else {
                context.resources.getQuantityString(
                    R.plurals.torrent_downloading,
                    seeders,
                    seeders
                )
            }
        }
        TorrentData.Status.Seeding -> if (isSeedingStalled) {
            if (hasError) {
                context.getString(R.string.torrent_seeding_stalled_with_error, errorString)
            } else {
                context.getText(R.string.torrent_seeding_stalled)
            }
        } else {
            if (hasError) {
                context.resources.getQuantityString(
                    R.plurals.torrent_seeding_with_error,
                    leechers,
                    leechers,
                    errorString
                )
            } else {
                context.resources.getQuantityString(
                    R.plurals.torrent_seeding,
                    leechers,
                    leechers
                )
            }
        }
        TorrentData.Status.QueuedForDownloading,
        TorrentData.Status.QueuedForSeeding -> if (hasError) {
            context.getString(R.string.torrent_queued_with_error, errorString)
        } else {
            context.getText(R.string.torrent_queued)
        }
        TorrentData.Status.Checking -> if (hasError) {
            context.getString(
                R.string.torrent_checking_with_error,
                DecimalFormats.generic.format(recheckProgress * 100),
                errorString
            )
        } else {
            context.getString(
                R.string.torrent_checking,
                DecimalFormats.generic.format(recheckProgress * 100)
            )
        }
        TorrentData.Status.QueuedForChecking -> if (hasError) {
            context.getString(R.string.torrent_queued_for_checking_with_error, errorString)
        } else {
            context.getText(R.string.torrent_queued_for_checking)
        }
    }
}
