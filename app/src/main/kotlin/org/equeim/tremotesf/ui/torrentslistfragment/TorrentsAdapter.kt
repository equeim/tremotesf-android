// SPDX-FileCopyrightText: 2017-2025 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package org.equeim.tremotesf.ui.torrentslistfragment

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
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import org.equeim.tremotesf.R
import org.equeim.tremotesf.databinding.TorrentListItemBinding
import org.equeim.tremotesf.databinding.TorrentListItemCompactBinding
import org.equeim.tremotesf.rpc.GlobalRpcClient
import org.equeim.tremotesf.rpc.requests.Torrent
import org.equeim.tremotesf.rpc.requests.TorrentStatus
import org.equeim.tremotesf.rpc.requests.reannounceTorrents
import org.equeim.tremotesf.rpc.toNativeSeparators
import org.equeim.tremotesf.ui.SelectionTracker
import org.equeim.tremotesf.ui.utils.AsyncLoadingListAdapter
import org.equeim.tremotesf.ui.utils.DecimalFormats
import org.equeim.tremotesf.ui.utils.FormatUtils
import org.equeim.tremotesf.ui.utils.Utils
import org.equeim.tremotesf.ui.utils.bindingAdapterPositionOrNull
import org.equeim.tremotesf.ui.utils.fuzzyEquals
import org.equeim.tremotesf.ui.utils.submitListAwait
import java.lang.ref.WeakReference


class TorrentsAdapter(
    private val fragment: TorrentsListFragment,
    model: TorrentsListFragmentViewModel,
    private val compactView: Boolean,
    private val multilineName: Boolean,
) :
    AsyncLoadingListAdapter<Torrent, TorrentsAdapter.BaseTorrentsViewHolder>(Callback()) {
    private val selectionTracker = SelectionTracker.createForIntKeys(
        this,
        true,
        fragment,
        { ActionModeCallback(this, it, model) },
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

    private val _currentListChanged =
        MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val currentListChanged: Flow<Unit> by ::_currentListChanged

    override fun onCurrentListChanged(
        previousList: MutableList<Torrent>,
        currentList: MutableList<Torrent>,
    ) {
        _currentListChanged.tryEmit(Unit)
    }

    inner class TorrentsViewHolder(
        multilineName: Boolean,
        private val binding: TorrentListItemBinding,
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
                                FormatUtils.formatFileSize(context, torrent.sizeWhenDone),
                                FormatUtils.formatFileSize(context, torrent.totalUploaded)
                            )
                    }
                } else {
                    if (oldTorrent?.isFinished != torrent.isFinished ||
                        oldTorrent.completedSize != torrent.completedSize ||
                        oldTorrent.sizeWhenDone != torrent.sizeWhenDone
                    ) {
                        sizeTextView.text = context.getString(
                            R.string.completed_string,
                            FormatUtils.formatFileSize(context, torrent.completedSize),
                            FormatUtils.formatFileSize(context, torrent.sizeWhenDone),
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
                        FormatUtils.formatTransferRate(
                            context,
                            torrent.downloadSpeed
                        )
                    )
                }

                if (oldTorrent?.uploadSpeed != torrent.uploadSpeed) {
                    uploadSpeedTextView.text = context.getString(
                        R.string.upload_speed_string,
                        FormatUtils.formatTransferRate(
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

                if (torrent.labels.isNotEmpty()) {
                    labelsTextView.visibility = View.VISIBLE
                    labelsTextView.text = torrent.labels.joinToString(", ")
                } else {
                    labelsTextView.visibility = View.GONE
                }
            }
        }

        override fun updateSelectionState(isSelected: Boolean) {
            binding.root.isChecked = isSelected
        }
    }

    inner class TorrentsViewHolderCompact(
        multilineName: Boolean,
        private val binding: TorrentListItemCompactBinding,
    ) : BaseTorrentsViewHolder(multilineName, binding.root) {
        override fun update() {
            val oldTorrent = torrent
            super.update()
            val torrent = this.torrent ?: return

            val speedLabelsWereEmpty = downloadSpeedTextView.text.isEmpty() && uploadSpeedTextView.text.isEmpty()

            if (oldTorrent?.downloadSpeed != torrent.downloadSpeed) {
                downloadSpeedTextView.text = if (torrent.downloadSpeed.bytesPerSecond == 0L) {
                    ""
                } else {
                    context.getString(
                        R.string.download_speed_string,
                        FormatUtils.formatTransferRate(
                            context,
                            torrent.downloadSpeed
                        )
                    )
                }
            }

            if (oldTorrent?.uploadSpeed != torrent.uploadSpeed) {
                uploadSpeedTextView.text = if (torrent.uploadSpeed.bytesPerSecond == 0L) {
                    ""
                } else {
                    context.getString(
                        R.string.upload_speed_string,
                        FormatUtils.formatTransferRate(
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
                    if (torrent.downloadSpeed.bytesPerSecond != 0L || torrent.uploadSpeed.bytesPerSecond != 0L) R.string.progress_string_with_dot else R.string.progress_string,
                    DecimalFormats.generic.format(torrent.percentDone * 100)
                )
            }
        }
    }

    open inner class BaseTorrentsViewHolder(
        multilineName: Boolean,
        itemView: View,
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
            val resId = if (torrent.error != null) {
                R.drawable.ic_error_24dp
            } else {
                when (torrent.status) {
                    TorrentStatus.Paused -> R.drawable.ic_pause_24dp
                    TorrentStatus.Downloading,
                    TorrentStatus.QueuedForDownloading,
                    -> R.drawable.ic_arrow_downward_24dp

                    TorrentStatus.Seeding,
                    TorrentStatus.QueuedForSeeding,
                    -> R.drawable.ic_arrow_upward_24dp

                    TorrentStatus.Checking,
                    TorrentStatus.QueuedForChecking,
                    -> R.drawable.ic_refresh_24dp
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
        selectionTracker: SelectionTracker<Int>,
        private val model: TorrentsListFragmentViewModel,
    ) :
        SelectionTracker.ActionModeCallback<Int>(selectionTracker) {

        private val adapter = WeakReference(adapter)

        override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
            mode.menuInflater.inflate(R.menu.torrents_context_menu, menu)
            return true
        }

        override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
            if (selectionTracker?.selectedCount == 1) {
                val startEnabled = adapter.get()?.getFirstSelectedTorrent()?.status == TorrentStatus.Paused
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
                R.id.start -> model.startTorrents(selectionTracker.selectedKeys.toList(), now = false)
                R.id.pause -> model.pauseTorrents(selectionTracker.selectedKeys.toList())
                R.id.check -> model.checkTorrents(selectionTracker.selectedKeys.toList())
                R.id.start_now -> model.startTorrents(selectionTracker.selectedKeys.toList(), now = true)
                R.id.reannounce -> GlobalRpcClient.performBackgroundRpcRequest(R.string.torrents_reannounce_error) {
                    reannounceTorrents(
                        selectionTracker.selectedKeys.toList()
                    )
                }

                R.id.set_location -> {
                    adapter.getFirstSelectedTorrent()?.let { firstTorrent ->
                        activity.navigate(
                            TorrentsListFragmentDirections.toTorrentSetLocationDialog(
                                selectionTracker.mapSelectedPositionsToArray { adapter.getItem(it).hashString },
                                firstTorrent.downloadDirectory.toNativeSeparators()
                            )
                        )
                    }
                }

                R.id.rename -> {
                    adapter.getFirstSelectedTorrent()?.let { torrent ->
                        activity.navigate(
                            TorrentsListFragmentDirections.toTorrentFileRenameDialog(
                                torrent.name,
                                torrent.name,
                                torrent.hashString
                            )
                        )
                    }
                }

                R.id.remove -> activity.navigate(
                    TorrentsListFragmentDirections.toRemoveTorrentDialog(
                        selectionTracker.mapSelectedPositionsToArray { adapter.getItem(it).hashString }
                    )
                )

                R.id.share -> {
                    val magnetLinks =
                        adapter.currentList
                            .slice(selectionTracker.getSelectedPositionsUnsorted().sorted().asIterable())
                            .map { it.magnetLink }
                    Utils.shareTorrents(magnetLinks, activity)
                }

                else -> return false
            }

            return true
        }

        private fun TorrentsAdapter.getFirstSelectedTorrent(): Torrent? =
            selectionTracker.getFirstSelectedPosition()?.let(::getItem)
    }

    private class Callback : DiffUtil.ItemCallback<Torrent>() {
        override fun areItemsTheSame(oldItem: Torrent, newItem: Torrent): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Torrent, newItem: Torrent): Boolean {
            return oldItem == newItem
        }
    }
}

private fun Torrent.getStatusString(context: Context): CharSequence {
    return when (status) {
        TorrentStatus.Paused -> if (error != null) {
            context.getString(R.string.torrent_paused_with_error, errorString)
        } else {
            context.getText(R.string.torrent_paused)
        }

        TorrentStatus.Downloading -> if (isDownloadingStalled) {
            if (error != null) {
                context.getString(R.string.torrent_downloading_stalled_with_error, errorString)
            } else {
                context.getText(R.string.torrent_downloading_stalled)
            }
        } else {
            val peers = this.peersSendingToUsCount + this.webSeedersSendingToUsCount
            if (error != null) {
                context.resources.getQuantityString(
                    R.plurals.torrent_downloading_with_error,
                    peers,
                    peers,
                    errorString
                )
            } else {
                context.resources.getQuantityString(
                    R.plurals.torrent_downloading,
                    peers,
                    peers
                )
            }
        }

        TorrentStatus.Seeding -> if (isSeedingStalled) {
            if (error != null) {
                context.getString(R.string.torrent_seeding_stalled_with_error, errorString)
            } else {
                context.getText(R.string.torrent_seeding_stalled)
            }
        } else {
            if (error != null) {
                context.resources.getQuantityString(
                    R.plurals.torrent_seeding_with_error,
                    peersGettingFromUsCount,
                    peersGettingFromUsCount,
                    errorString
                )
            } else {
                context.resources.getQuantityString(
                    R.plurals.torrent_seeding,
                    peersGettingFromUsCount,
                    peersGettingFromUsCount
                )
            }
        }

        TorrentStatus.QueuedForDownloading,
        TorrentStatus.QueuedForSeeding,
        -> if (error != null) {
            context.getString(R.string.torrent_queued_with_error, errorString)
        } else {
            context.getText(R.string.torrent_queued)
        }

        TorrentStatus.Checking -> if (error != null) {
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

        TorrentStatus.QueuedForChecking -> if (error != null) {
            context.getString(R.string.torrent_queued_for_checking_with_error, errorString)
        } else {
            context.getText(R.string.torrent_queued_for_checking)
        }
    }
}
