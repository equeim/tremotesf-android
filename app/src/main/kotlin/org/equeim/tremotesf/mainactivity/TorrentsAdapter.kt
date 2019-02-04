/*
 * Copyright (C) 2017-2018 Alexey Rochev <equeim@gmail.com>
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

package org.equeim.tremotesf.mainactivity

import java.text.Collator
import java.text.DecimalFormat
import java.util.Comparator

import android.app.Dialog
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.TextView

import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.view.ActionMode
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.RecyclerView

import com.amjjd.alphanum.AlphanumericComparator
import org.jetbrains.anko.intentFor

import org.equeim.libtremotesf.Torrent
import org.equeim.tremotesf.AddTorrentDirectoriesAdapter
import org.equeim.tremotesf.R
import org.equeim.tremotesf.Rpc
import org.equeim.tremotesf.Selector
import org.equeim.tremotesf.Settings
import org.equeim.tremotesf.TorrentData
import org.equeim.tremotesf.torrentpropertiesactivity.TorrentFilesAdapter
import org.equeim.tremotesf.torrentpropertiesactivity.TorrentPropertiesActivity
import org.equeim.tremotesf.utils.Utils
import org.equeim.tremotesf.utils.createTextFieldDialog

import kotlinx.android.synthetic.main.download_directory_edit.*
import kotlinx.android.synthetic.main.main_activity.*
import kotlinx.android.synthetic.main.remove_torrents_dialog.*
import kotlinx.android.synthetic.main.set_location_dialog.*
import kotlinx.android.synthetic.main.torrent_list_item.view.*
import kotlinx.android.synthetic.main.torrent_list_item_compact.view.*


private const val INSTANCE_STATE = "org.equeim.tremotesf.mainactivity.TorrentsAdapter"
private const val SORT_MODE = "sortMode"
private const val SORT_ORDER = "sortOrder"
private const val STATUS_FILTER_MODE = "statusFilterMode"
private const val TRACKER_FILTER = "trackerFilter"
private const val DIRECTORY_FILTER = "directoryFilter"

class TorrentsAdapter(private val activity: MainActivity) : RecyclerView.Adapter<TorrentsAdapter.BaseTorrentsViewHolder>() {
    companion object {
        fun statusFilterAcceptsTorrent(torrent: TorrentData, filterMode: StatusFilterMode): Boolean {
            return when (filterMode) {
                StatusFilterMode.Active -> (torrent.status == Torrent.Status.Downloading) ||
                                           (torrent.status == Torrent.Status.Seeding)
                StatusFilterMode.Downloading -> when (torrent.status) {
                    Torrent.Status.Downloading,
                    Torrent.Status.StalledDownloading,
                    Torrent.Status.QueuedForDownloading -> true
                    else -> false
                }
                StatusFilterMode.Seeding -> when (torrent.status) {
                    Torrent.Status.Seeding,
                    Torrent.Status.StalledSeeding,
                    Torrent.Status.QueuedForSeeding -> true
                    else -> false
                }
                StatusFilterMode.Paused -> (torrent.status == Torrent.Status.Paused)
                StatusFilterMode.Checking -> (torrent.status == Torrent.Status.Checking) ||
                                             (torrent.status == Torrent.Status.Checking)
                StatusFilterMode.Errored -> (torrent.status == Torrent.Status.Errored)
                StatusFilterMode.All -> true
            }
        }
    }

    enum class SortMode {
        Name,
        Status,
        Progress,
        Eta,
        Ratio,
        Size,
        AddedDate
    }

    enum class SortOrder {
        Ascending,
        Descending
    }

    enum class StatusFilterMode {
        All,
        Active,
        Downloading,
        Seeding,
        Paused,
        Checking,
        Errored
    }

    private val torrents = Rpc.instance.torrents

    private var filteredTorrents = listOf<TorrentData>()
    private val displayedTorrents = mutableListOf<TorrentData>()

    val selector = Selector(activity,
                            ActionModeCallback(activity),
                            this,
                            displayedTorrents,
                            TorrentData::id,
                            R.plurals.torrents_selected)

    private val filterPredicate = { torrent: TorrentData ->
        statusFilterAcceptsTorrent(torrent, statusFilterMode) &&
        (trackerFilter.isEmpty() || (torrent.trackers.find { it == trackerFilter } != null)) &&
        (directoryFilter.isEmpty() || torrent.downloadDirectory == directoryFilter) &&
        torrent.name.contains(filterString, true)
    }

    private val comparator = object : Comparator<TorrentData> {
        private val nameComparator = AlphanumericComparator(Collator.getInstance())

        override fun compare(o1: TorrentData, o2: TorrentData): Int {
            var compared = when (sortMode) {
                SortMode.Name -> nameComparator.compare(o1.name, o2.name)
                SortMode.Status -> o1.status.compareTo(o2.status)
                SortMode.Progress -> o1.percentDone.compareTo(o2.percentDone)
                SortMode.Eta -> o1.eta.compareTo(o2.eta)
                SortMode.Ratio -> o1.ratio.compareTo(o2.ratio)
                SortMode.Size -> o1.totalSize.compareTo(o2.totalSize)
                SortMode.AddedDate -> o1.addedDate.compareTo(o2.addedDate)
            }
            if (sortMode != SortMode.Name && compared == 0) {
                compared = nameComparator.compare(o1.name, o2.name)
            }
            if (sortOrder == SortOrder.Descending) {
                compared = -compared
            }
            return compared
        }
    }

    var sortMode = Settings.torrentsSortMode
        set(value) {
            if (value != field) {
                field = value
                displayedTorrents.clear()
                displayedTorrents.addAll(filteredTorrents.sortedWith(comparator))
                notifyItemRangeChanged(0, itemCount)
            }
        }

    var sortOrder = Settings.torrentsSortOrder
        set(value) {
            if (value != field) {
                field = value
                displayedTorrents.clear()
                displayedTorrents.addAll(filteredTorrents.sortedWith(comparator))
                notifyItemRangeChanged(0, itemCount)
            }
        }

    var statusFilterMode = Settings.torrentsStatusFilter
        set(value) {
            if (value != field) {
                field = value
                updateListContent()
                activity.torrents_view.scrollToPosition(0)
            }
        }

    var trackerFilter = ""
        set(value) {
            if (value != field) {
                field = value
                updateListContent()
                activity.torrents_view.scrollToPosition(0)
            }
        }

    var directoryFilter = ""
        set(value) {
            if (value != field) {
                field = value
                updateListContent()
                activity.torrents_view.scrollToPosition(0)
            }
        }

    var filterString = ""
        set(value) {
            if (value != field) {
                field = value
                updateListContent()
                activity.torrents_view.scrollToPosition(0)
            }
        }

    override fun getItemCount(): Int {
        return displayedTorrents.size
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BaseTorrentsViewHolder {
        if (Settings.torrentCompactView) {
            return TorrentsViewHolderCompact(selector,
                                             activity,
                                             LayoutInflater.from(parent.context).inflate(R.layout.torrent_list_item_compact,
                                                                                         parent,
                                                                                         false))
        }
        return TorrentsViewHolder(selector,
                                  activity,
                                  LayoutInflater.from(parent.context).inflate(R.layout.torrent_list_item,
                                                                              parent,
                                                                             false))
    }

    override fun onBindViewHolder(holder: BaseTorrentsViewHolder, position: Int) {
        holder.update(displayedTorrents[position])
    }

    private fun updateListContent() {
        filteredTorrents = torrents.filter(filterPredicate)

        run {
            var i = 0
            while (i < displayedTorrents.size) {
                if (filteredTorrents.contains(displayedTorrents[i])) {
                    i++
                } else {
                    displayedTorrents.removeAt(i)
                    notifyItemRemoved(i)
                }
            }
        }

        for ((i, torrent) in filteredTorrents.sortedWith(comparator).withIndex()) {
            if (displayedTorrents.getOrNull(i) !== torrent) {
                val index = displayedTorrents.indexOf(torrent)
                if (index == -1) {
                    displayedTorrents.add(i, torrent)
                    notifyItemInserted(i)
                } else {
                    displayedTorrents.removeAt(index)
                    displayedTorrents.add(i, torrent)
                    notifyItemMoved(index, i)
                }
            }
        }

        selector.clearRemovedItems()
    }

    fun update() {
        updateListContent()
        for ((i, torrent) in displayedTorrents.withIndex()) {
            if (torrent.torrent.isChanged) {
                notifyItemChanged(i)
            }
        }
        selector.actionMode?.invalidate()
    }

    fun saveInstanceState(outState: Bundle) {
        outState.putBundle(INSTANCE_STATE, bundleOf(SORT_MODE to sortMode.ordinal,
                                                    SORT_ORDER to sortOrder.ordinal,
                                                    STATUS_FILTER_MODE to statusFilterMode.ordinal,
                                                    TRACKER_FILTER to trackerFilter,
                                                    DIRECTORY_FILTER to directoryFilter))
    }

    fun restoreInstanceState(savedInstanceState: Bundle) {
        savedInstanceState.getBundle(INSTANCE_STATE)?.let { state ->
            sortMode = SortMode.values()[state.getInt(SORT_MODE)]
            sortOrder = SortOrder.values()[state.getInt(SORT_ORDER)]
            statusFilterMode = StatusFilterMode.values()[state.getInt(STATUS_FILTER_MODE)]
            trackerFilter = state.getString(TRACKER_FILTER, "")
            directoryFilter = state.getString(DIRECTORY_FILTER, "")
        }
    }

    class TorrentsViewHolder(selector: Selector<TorrentData, Int>,
                             activity: MainActivity,
                             itemView: View) : BaseTorrentsViewHolder(selector, activity, itemView) {
        private val sizeTextView = itemView.size_text_view!!
        private val etaTextView = itemView.eta_text_view!!
        private val progressBar = itemView.progress_bar!!
        private val statusTextView = itemView.status_text_view!!

        init {
            Utils.setProgressBarColor(progressBar)
        }

        override fun update(torrent: TorrentData) {
            super.update(torrent)

            sizeTextView.text = if (torrent.isFinished) {
                activity.getString(R.string.uploaded_string,
                                   Utils.formatByteSize(activity, torrent.sizeWhenDone),
                                   Utils.formatByteSize(activity, torrent.totalUploaded))
            } else {
                activity.getString(R.string.completed_string,
                                   Utils.formatByteSize(activity, torrent.completedSize),
                                   Utils.formatByteSize(activity, torrent.sizeWhenDone),
                                   DecimalFormat("0.#").format(torrent.percentDone * 100))
            }
            etaTextView.text = Utils.formatDuration(activity, torrent.eta)

            progressBar.progress = (torrent.percentDone * 100).toInt()
            downloadSpeedTextView.text = activity.getString(R.string.download_speed_string,
                                                            Utils.formatByteSpeed(activity,
                                                                                  torrent.downloadSpeed))
            uploadSpeedTextView.text = activity.getString(R.string.upload_speed_string,
                                                          Utils.formatByteSpeed(activity,
                                                                                torrent.uploadSpeed))

            statusTextView.text = torrent.statusString
        }
    }

    class TorrentsViewHolderCompact(selector: Selector<TorrentData, Int>,
                                    activity: MainActivity,
                                    itemView: View) : BaseTorrentsViewHolder(selector, activity, itemView) {
        private val progressTextView = itemView.progress_text_view!!

        override fun update(torrent: TorrentData) {
            super.update(torrent)

            downloadSpeedTextView.text = if (torrent.downloadSpeed == 0L) {
                ""
            } else {
                activity.getString(R.string.download_speed_string,
                                   Utils.formatByteSpeed(activity,
                                                         torrent.downloadSpeed))
            }

            uploadSpeedTextView.text = if (torrent.uploadSpeed == 0L) {
                ""
            } else {
                activity.getString(R.string.upload_speed_string,
                                   Utils.formatByteSpeed(activity,
                                                         torrent.uploadSpeed))
            }

            progressTextView.text = activity.getString(if (torrent.downloadSpeed != 0L || torrent.uploadSpeed != 0L) R.string.progress_string_with_dot else R.string.progress_string,
                                                       DecimalFormat("0.#").format(torrent.percentDone * 100))
        }
    }

    open class BaseTorrentsViewHolder(selector: Selector<TorrentData, Int>,
                             protected val activity: MainActivity,
                             itemView: View) : Selector.ViewHolder<TorrentData>(selector,
                                                                            itemView) {
        override lateinit var item: TorrentData

        private val nameTextView = itemView.findViewById<TextView>(R.id.name_text_view)!!
        private val statusIconDrawable: Drawable = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR1) {
            nameTextView.compoundDrawables.first()
        } else {
            nameTextView.compoundDrawablesRelative.first()
        }
        protected val downloadSpeedTextView = itemView.findViewById<TextView>(R.id.download_speed_text_view)!!
        protected val uploadSpeedTextView = itemView.findViewById<TextView>(R.id.upload_speed_text_view)!!

        init {
            if (!Settings.torrentNameMultiline) {
                nameTextView.ellipsize = TextUtils.TruncateAt.END
                nameTextView.maxLines = 1
                nameTextView.setSingleLine(true)
            }
        }

        open fun update(torrent: TorrentData) {
            item = torrent

            nameTextView.text = torrent.name
            statusIconDrawable.level = when (torrent.status) {
                Torrent.Status.Paused -> 0
                Torrent.Status.Downloading,
                Torrent.Status.StalledDownloading,
                Torrent.Status.QueuedForDownloading -> 1
                Torrent.Status.Seeding,
                Torrent.Status.StalledSeeding,
                Torrent.Status.QueuedForSeeding -> 2
                Torrent.Status.Checking,
                Torrent.Status.QueuedForChecking -> 3
                Torrent.Status.Errored -> 4
                else -> 0
            }

            updateSelectedBackground()
        }

        override fun onClick(view: View) {
            if (selector.actionMode == null) {
                activity.startActivity(activity.intentFor<TorrentPropertiesActivity>(TorrentPropertiesActivity.HASH to item.hashString,
                                                                                     TorrentPropertiesActivity.NAME to item.name))
            } else {
                super.onClick(view)
            }
        }
    }

    private class ActionModeCallback(private val activity: AppCompatActivity) : Selector.ActionModeCallback<TorrentData>() {
        private var startItem: MenuItem? = null
        private var pauseItem: MenuItem? = null
        private var setLocationItem: MenuItem? = null

        override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
            mode.menuInflater.inflate(R.menu.torrents_context_menu, menu)
            startItem = menu.findItem(R.id.start)
            pauseItem = menu.findItem(R.id.pause)
            setLocationItem = menu.findItem(R.id.set_location)
            return true
        }

        override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
            super.onPrepareActionMode(mode, menu)

            if (selector.selectedCount == 1) {
                val status = selector.selectedItems.first().status
                startItem!!.isEnabled = when (status) {
                    Torrent.Status.Paused,
                    Torrent.Status.Errored -> true
                    else -> false
                }
                pauseItem!!.isEnabled = !startItem!!.isEnabled
            } else {
                startItem!!.isEnabled = true
                pauseItem!!.isEnabled = true
            }

            return true
        }

        override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
            if (super.onActionItemClicked(mode, item)) {
                return true
            }

            when (item.itemId) {
                R.id.start -> Rpc.instance.startTorrents(selector.selectedItems.map(TorrentData::id).toIntArray())
                R.id.pause -> Rpc.instance.pauseTorrents(selector.selectedItems.map(TorrentData::id).toIntArray())
                R.id.check -> Rpc.instance.checkTorrents(selector.selectedItems.map(TorrentData::id).toIntArray())
                R.id.reannounce -> Rpc.instance.reannounceTorrents(selector.selectedItems.map(TorrentData::id).toIntArray())
                R.id.set_location -> SetLocationDialogFragment.create(selector.selectedItems.map(TorrentData::id).toIntArray(),
                                                                      selector.selectedItems.first().downloadDirectory)
                        .show(activity.supportFragmentManager, SetLocationDialogFragment.TAG)
                R.id.rename -> {
                    val torrent = selector.selectedItems.first()
                    TorrentFilesAdapter.RenameDialogFragment.create(torrent.id,
                                                                    torrent.name,
                                                                    torrent.name)
                            .show(activity.supportFragmentManager, TorrentFilesAdapter.RenameDialogFragment.TAG)
                }
                R.id.remove -> RemoveDialogFragment.create(selector.selectedItems.map(TorrentData::id).toIntArray())
                        .show(activity.supportFragmentManager,
                              RemoveDialogFragment.TAG)
                else -> return false
            }

            return true
        }

        override fun onDestroyActionMode(mode: ActionMode) {
            super.onDestroyActionMode(mode)
            startItem = null
            pauseItem = null
            setLocationItem = null
        }
    }

    class SetLocationDialogFragment : DialogFragment() {
        companion object {
            const val TAG = "org.equeim.tremotesf.TorrentsAdapter.SetLocationDialogFragment"
            private const val TORRENT_IDS = "torrentIds"
            private const val LOCATION = "location"

            fun create(ids: IntArray, location: String): SetLocationDialogFragment {
                val fragment = SetLocationDialogFragment()
                fragment.arguments = bundleOf(TORRENT_IDS to ids,
                                              LOCATION to location)
                return fragment
            }
        }

        private var directoriesAdapter: AddTorrentDirectoriesAdapter? = null

        override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
            return createTextFieldDialog(requireContext(),
                                         null,
                                         R.layout.set_location_dialog,
                                         R.id.download_directory_edit,
                                         getString(R.string.location),
                                         InputType.TYPE_TEXT_VARIATION_URI,
                                         arguments!!.getString(LOCATION),
                                         {
                                             directoriesAdapter = AddTorrentDirectoriesAdapter.setupPopup(dialog.download_directory_dropdown, dialog.download_directory_edit)
                                         },
                                         {
                                             Rpc.instance.setTorrentsLocation(arguments!!.getIntArray(TORRENT_IDS),
                                                                              dialog.download_directory_edit.text.toString(),
                                                                              dialog.move_files_check_box.isChecked)
                                             directoriesAdapter?.save()
                                         })
        }
    }

    class RemoveDialogFragment : DialogFragment() {
        companion object {
            const val TAG = "org.equeim.tremotesf.TorrentsAdapter.RemoveDialogFragment"
            private const val TORRENT_IDS = "torrentIds"

            fun create(ids: IntArray): RemoveDialogFragment {
                val fragment = RemoveDialogFragment()
                fragment.arguments = bundleOf(TORRENT_IDS to ids)
                return fragment
            }
        }

        override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
            val ids = arguments!!.getIntArray(TORRENT_IDS)!!

            val dialog = AlertDialog.Builder(requireContext())
                    .setMessage(if (ids.size == 1) getString(R.string.remove_torrent_message)
                                else resources.getQuantityString(R.plurals.remove_torrents_message,
                                                                 ids.size,
                                                                 ids.size))
                    .setView(R.layout.remove_torrents_dialog)
                    .setNegativeButton(android.R.string.cancel, null)
                    .setPositiveButton(R.string.remove) { _, _ ->
                        Rpc.instance.removeTorrents(ids,
                                                    dialog.delete_files_check_box.isChecked)
                        (activity as? Selector.ActionModeActivity)?.actionMode?.finish()
                    }
                    .create()

            dialog.setOnShowListener {
                dialog.delete_files_check_box.isChecked = Settings.deleteFiles
            }

            return dialog
        }
    }
}