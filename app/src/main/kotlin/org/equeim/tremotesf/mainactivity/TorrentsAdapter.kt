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

import android.content.Intent

import android.graphics.drawable.Drawable

import android.os.Build
import android.os.Bundle
import android.support.v4.app.DialogFragment

import android.text.InputType

import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup

import android.widget.CheckBox
import android.widget.ProgressBar
import android.widget.TextView

import android.support.v7.app.AlertDialog
import android.support.v7.app.AppCompatActivity
import android.support.v7.view.ActionMode
import android.support.v7.widget.RecyclerView

import androidx.core.os.bundleOf

import com.amjjd.alphanum.AlphanumericComparator

import org.equeim.tremotesf.R
import org.equeim.tremotesf.Rpc
import org.equeim.tremotesf.Selector
import org.equeim.tremotesf.Settings
import org.equeim.tremotesf.Torrent
import org.equeim.tremotesf.torrentpropertiesactivity.TorrentPropertiesActivity
import org.equeim.tremotesf.utils.Utils
import org.equeim.tremotesf.utils.createTextFieldDialog

import kotlinx.android.synthetic.main.main_activity.torrents_view


private const val INSTANCE_STATE = "org.equeim.tremotesf.mainactivity.TorrentsAdapter"
private const val SORT_MODE = "sortMode"
private const val SORT_ORDER = "sortOrder"
private const val STATUS_FILTER_MODE = "statusFilterMode"
private const val TRACKER_FILTER = "trackerFilter"

class TorrentsAdapter(private val activity: MainActivity) : RecyclerView.Adapter<TorrentsAdapter.TorrentsViewHolder>() {
    companion object {
        fun statusFilterAcceptsTorrent(torrent: Torrent, filterMode: StatusFilterMode): Boolean {
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

    private val torrents = Rpc.torrents
    private var filteredTorrents = listOf<Torrent>()
    private val displayedTorrents = mutableListOf<Torrent>()

    val selector = Selector(activity,
                            ActionModeCallback(activity),
                            this,
                            displayedTorrents,
                            Torrent::id,
                            R.plurals.torrents_selected)

    private val filterPredicate = { torrent: Torrent ->
        statusFilterAcceptsTorrent(torrent, statusFilterMode) &&
        (trackerFilter.isEmpty() || (torrent.trackers.find { it.site == trackerFilter } != null)) &&
        torrent.name.contains(filterString, true)
    }

    private val comparator = object : Comparator<Torrent> {
        private val nameComparator = AlphanumericComparator(Collator.getInstance())

        override fun compare(o1: Torrent, o2: Torrent): Int {
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

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TorrentsViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.torrent_list_item,
                                                               parent,
                                                               false)
        Utils.setProgressBarAccentColor(view.findViewById(R.id.progress_bar) as ProgressBar)

        return TorrentsViewHolder(selector, activity, view)
    }

    override fun onBindViewHolder(holder: TorrentsViewHolder, position: Int) {
        val torrent = displayedTorrents[position]

        holder.item = torrent

        holder.nameTextView.text = torrent.name
        holder.statusIconDrawable.level = when (torrent.status) {
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
        }

        holder.sizeTextView.text = if (torrent.percentDone == 1.0) {
            activity.getString(R.string.uploaded_string,
                               Utils.formatByteSize(activity, torrent.sizeWhenDone),
                               Utils.formatByteSize(activity, torrent.totalUploaded))
        } else {
            activity.getString(R.string.completed_string,
                               Utils.formatByteSize(activity, torrent.completedSize),
                               Utils.formatByteSize(activity, torrent.sizeWhenDone),
                               DecimalFormat("0.#").format(torrent.percentDone * 100))
        }
        holder.etaTextView.text = Utils.formatDuration(activity, torrent.eta)

        holder.progressBar.progress = (torrent.percentDone * 100).toInt()
        holder.downloadSpeedTextView.text = activity.getString(R.string.download_speed_string,
                                                               Utils.formatByteSpeed(activity,
                                                                                     torrent.downloadSpeed))
        holder.uploadSpeedTextView.text = activity.getString(R.string.upload_speed_string,
                                                             Utils.formatByteSpeed(activity,
                                                                                   torrent.uploadSpeed))

        holder.statusTextView.text = torrent.statusString

        holder.updateSelectedBackground()
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
            if (torrent.changed) {
                notifyItemChanged(i)
            }
        }
        selector.actionMode?.invalidate()
    }

    fun saveInstanceState(outState: Bundle) {
        outState.putBundle(INSTANCE_STATE, bundleOf(SORT_MODE to sortMode.ordinal,
                                                    SORT_ORDER to sortOrder.ordinal,
                                                    STATUS_FILTER_MODE to statusFilterMode.ordinal,
                                                    TRACKER_FILTER to trackerFilter))
    }

    fun restoreInstanceState(savedInstanceState: Bundle) {
        val state = savedInstanceState.getBundle(INSTANCE_STATE)
        sortMode = SortMode.values()[state.getInt(SORT_MODE)]
        sortOrder = SortOrder.values()[state.getInt(SORT_ORDER)]
        statusFilterMode = StatusFilterMode.values()[state.getInt(STATUS_FILTER_MODE)]
        trackerFilter = state.getString(TRACKER_FILTER)
    }

    class TorrentsViewHolder(selector: Selector<Torrent, Int>,
                             private val activity: MainActivity,
                             itemView: View) : Selector.ViewHolder<Torrent>(selector,
                                                                            itemView) {
        override lateinit var item: Torrent

        val nameTextView = itemView.findViewById(R.id.name_text_view) as TextView
        val statusIconDrawable: Drawable = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR1) {
            nameTextView.compoundDrawables.first()
        } else {
            nameTextView.compoundDrawablesRelative.first()
        }

        val sizeTextView = itemView.findViewById(R.id.size_text_view) as TextView
        val etaTextView = itemView.findViewById(R.id.eta_text_view) as TextView
        val progressBar = itemView.findViewById(R.id.progress_bar) as ProgressBar
        val downloadSpeedTextView = itemView.findViewById(R.id.download_speed_text_view) as TextView
        val uploadSpeedTextView = itemView.findViewById(R.id.upload_speed_text_view) as TextView
        val statusTextView = itemView.findViewById(R.id.status_text_view) as TextView

        override fun onClick(view: View) {
            if (selector.actionMode == null) {
                val intent = Intent(activity, TorrentPropertiesActivity::class.java)
                intent.putExtra(TorrentPropertiesActivity.HASH, item.hashString)
                intent.putExtra(TorrentPropertiesActivity.NAME, item.name)
                activity.startActivity(intent)
            } else {
                super.onClick(view)
            }
        }
    }

    private class ActionModeCallback(private val activity: AppCompatActivity) : Selector.ActionModeCallback<Torrent>() {
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
                setLocationItem!!.isEnabled = true
            } else {
                startItem!!.isEnabled = true
                pauseItem!!.isEnabled = true
                setLocationItem!!.isEnabled = false
            }

            return true
        }

        override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
            if (super.onActionItemClicked(mode, item)) {
                return true
            }

            when (item.itemId) {
                R.id.start -> Rpc.startTorrents(selector.selectedItems.map(Torrent::id))
                R.id.pause -> Rpc.pauseTorrents(selector.selectedItems.map(Torrent::id))
                R.id.check -> Rpc.checkTorrents(selector.selectedItems.map(Torrent::id))
                R.id.set_location -> SetLocationDialogFragment.create(selector.selectedItems.first())
                        .show(activity.supportFragmentManager, SetLocationDialogFragment.TAG)
                R.id.remove -> RemoveDialogFragment().show(activity.supportFragmentManager,
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
            private const val TORRENT_ID = "torrentId"
            private const val LOCATION = "location"

            fun create(torrent: Torrent): SetLocationDialogFragment {
                val fragment = SetLocationDialogFragment()
                fragment.arguments = bundleOf(TORRENT_ID to torrent.id,
                                              LOCATION to torrent.downloadDirectory)
                return fragment
            }
        }

        override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
            return createTextFieldDialog(context!!,
                                         null,
                                         R.layout.set_location_dialog,
                                         getString(R.string.location),
                                         InputType.TYPE_TEXT_VARIATION_URI,
                                         arguments!!.getString(LOCATION)) {
                val textField = dialog.findViewById(R.id.text_field) as TextView
                val moveFilesCheckBox = dialog.findViewById(R.id.move_files_check_box) as CheckBox
                Rpc.setTorrentLocation(arguments!!.getInt(TORRENT_ID),
                                       textField.text.toString(),
                                       moveFilesCheckBox.isChecked)

                (activity as? MainActivity)?.torrentsAdapter?.selector?.actionMode?.finish()
            }
        }
    }

    class RemoveDialogFragment : DialogFragment() {
        companion object {
            const val TAG = "org.equeim.tremotesf.TorrentsAdapter.RemoveDialogFragment"
        }

        override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
            val selector = (activity as MainActivity).torrentsAdapter.selector
            val selectedCount = selector.selectedCount

            val dialog = AlertDialog.Builder(context!!)
                    .setMessage(resources.getQuantityString(R.plurals.remove_torrents_message,
                                                                     selectedCount,
                                                                     selectedCount))
                    .setView(R.layout.remove_torrents_dialog)
                    .setNegativeButton(android.R.string.cancel, null)
                    .setPositiveButton(R.string.remove, { _, _ ->
                        Rpc.removeTorrents(selector.selectedItems.map(Torrent::id),
                                           (this.dialog.findViewById(R.id.delete_files_check_box) as CheckBox).isChecked)
                        selector.actionMode?.finish()
                    }).create()

            dialog.setOnShowListener {
                val deleteFilesCheckBox = dialog.findViewById<CheckBox>(R.id.delete_files_check_box)!!
                deleteFilesCheckBox.isChecked = Settings.deleteFiles
            }

            return dialog
        }
    }
}