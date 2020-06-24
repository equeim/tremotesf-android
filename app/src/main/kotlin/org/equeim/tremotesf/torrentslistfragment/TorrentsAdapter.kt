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

package org.equeim.tremotesf.torrentslistfragment

import java.util.Comparator

import android.app.Dialog
import android.content.Context
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

import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.view.ActionMode
import androidx.core.os.bundleOf
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.findNavController
import androidx.recyclerview.widget.RecyclerView

import com.google.android.material.dialog.MaterialAlertDialogBuilder

import org.equeim.libtremotesf.TorrentData
import org.equeim.tremotesf.AddTorrentDirectoriesAdapter
import org.equeim.tremotesf.IntSelector
import org.equeim.tremotesf.NavigationFragment
import org.equeim.tremotesf.NavigationDialogFragment
import org.equeim.tremotesf.R
import org.equeim.tremotesf.Rpc
import org.equeim.tremotesf.Selector
import org.equeim.tremotesf.Settings
import org.equeim.tremotesf.Torrent
import org.equeim.tremotesf.TorrentFileRenameDialogFragment
import org.equeim.tremotesf.databinding.RemoveTorrentsDialogBinding
import org.equeim.tremotesf.databinding.SetLocationDialogBinding
import org.equeim.tremotesf.databinding.TorrentListItemBinding
import org.equeim.tremotesf.databinding.TorrentListItemCompactBinding
import org.equeim.tremotesf.torrentpropertiesfragment.TorrentPropertiesFragment
import org.equeim.tremotesf.utils.AlphanumericComparator
import org.equeim.tremotesf.utils.DecimalFormats
import org.equeim.tremotesf.utils.Utils
import org.equeim.tremotesf.utils.createTextFieldDialog
import org.equeim.tremotesf.utils.safeNavigate


private const val INSTANCE_STATE = "org.equeim.tremotesf.mainactivity.TorrentsAdapter"
private const val SORT_MODE = "sortMode"
private const val SORT_ORDER = "sortOrder"
private const val STATUS_FILTER_MODE = "statusFilterMode"
private const val TRACKER_FILTER = "trackerFilter"
private const val DIRECTORY_FILTER = "directoryFilter"

class TorrentsAdapter(activity: AppCompatActivity, private val fragment: TorrentsListFragment) : RecyclerView.Adapter<TorrentsAdapter.BaseTorrentsViewHolder>() {
    companion object {
        fun statusFilterAcceptsTorrent(torrent: Torrent, filterMode: StatusFilterMode): Boolean {
            return when (filterMode) {
                StatusFilterMode.Active -> (torrent.status == TorrentData.Status.Downloading) ||
                                           (torrent.status == TorrentData.Status.Seeding)
                StatusFilterMode.Downloading -> when (torrent.status) {
                    TorrentData.Status.Downloading,
                    TorrentData.Status.StalledDownloading,
                    TorrentData.Status.QueuedForDownloading -> true
                    else -> false
                }
                StatusFilterMode.Seeding -> when (torrent.status) {
                    TorrentData.Status.Seeding,
                    TorrentData.Status.StalledSeeding,
                    TorrentData.Status.QueuedForSeeding -> true
                    else -> false
                }
                StatusFilterMode.Paused -> (torrent.status == TorrentData.Status.Paused)
                StatusFilterMode.Checking -> (torrent.status == TorrentData.Status.Checking) ||
                                             (torrent.status == TorrentData.Status.Checking)
                StatusFilterMode.Errored -> (torrent.status == TorrentData.Status.Errored)
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

    private var filteredTorrents = listOf<Torrent>()
    private val displayedTorrents = mutableListOf<Torrent>()

    val selector = IntSelector(activity,
                               ActionModeCallback(activity),
                               this,
                               displayedTorrents,
                               Torrent::id,
                               R.plurals.torrents_selected)

    private val filterPredicate = { torrent: Torrent ->
        statusFilterAcceptsTorrent(torrent, statusFilterMode) &&
        (trackerFilter.isEmpty() || (torrent.trackerSites.find { it == trackerFilter } != null)) &&
        (directoryFilter.isEmpty() || torrent.downloadDirectory == directoryFilter) &&
        torrent.name.contains(filterString, true)
    }

    private val comparator = object : Comparator<Torrent> {
        private val nameComparator = AlphanumericComparator()

        override fun compare(o1: Torrent, o2: Torrent): Int {
            var compared = when (sortMode) {
                SortMode.Name -> nameComparator.compare(o1.name, o2.name)
                SortMode.Status -> o1.status.compareTo(o2.status)
                SortMode.Progress -> o1.percentDone.compareTo(o2.percentDone)
                SortMode.Eta -> o1.eta.compareTo(o2.eta)
                SortMode.Ratio -> o1.ratio.compareTo(o2.ratio)
                SortMode.Size -> o1.totalSize.compareTo(o2.totalSize)
                SortMode.AddedDate -> o1.addedDateTime.compareTo(o2.addedDateTime)
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

    private val compactView = Settings.torrentCompactView
    private val multilineName = Settings.torrentNameMultiline

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
                fragment.binding.torrentsView.scrollToPosition(0)
            }
        }

    var trackerFilter = ""
        set(value) {
            if (value != field) {
                field = value
                updateListContent()
                fragment.binding.torrentsView.scrollToPosition(0)
            }
        }

    var directoryFilter = ""
        set(value) {
            if (value != field) {
                field = value
                updateListContent()
                fragment.binding.torrentsView.scrollToPosition(0)
            }
        }

    var filterString = ""
        set(value) {
            if (value != field) {
                field = value
                updateListContent()
                fragment.binding.torrentsView.scrollToPosition(0)
            }
        }

    override fun getItemCount(): Int {
        return displayedTorrents.size
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BaseTorrentsViewHolder {
        if (compactView) {
            return TorrentsViewHolderCompact(selector,
                                             multilineName,
                                             TorrentListItemCompactBinding.inflate(LayoutInflater.from(parent.context),
                                                                                   parent,
                                                                                   false))
        }
        return TorrentsViewHolder(selector,
                                  multilineName,
                                  TorrentListItemBinding.inflate(LayoutInflater.from(parent.context),
                                                                 parent,
                                                                 false))
    }

    override fun onBindViewHolder(holder: BaseTorrentsViewHolder, position: Int) {
        holder.update(displayedTorrents[position])
    }

    private fun updateListContent() {
        filteredTorrents = Rpc.torrents.value.filter(filterPredicate)

        if (displayedTorrents.isEmpty()) {
            displayedTorrents.addAll(filteredTorrents.sortedWith(comparator))
            notifyItemRangeInserted(0, displayedTorrents.size)
            return
        }

        if (filteredTorrents.isEmpty()) {
            if (displayedTorrents.isNotEmpty()) {
                val size = displayedTorrents.size
                displayedTorrents.clear()
                notifyItemRangeRemoved(0, size)
                selector.clearRemovedItems()
            }
            return
        }

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
        val wasEmpty = displayedTorrents.isEmpty()
        updateListContent()
        if (!wasEmpty) {
            for ((i, torrent) in displayedTorrents.withIndex()) {
                if (torrent.changed) {
                    notifyItemChanged(i)
                }
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

    fun restoreInstanceState(savedInstanceState: Bundle?) {
        savedInstanceState?.getBundle(INSTANCE_STATE)?.let { state ->
            sortMode = SortMode.values()[state.getInt(SORT_MODE)]
            sortOrder = SortOrder.values()[state.getInt(SORT_ORDER)]
            statusFilterMode = StatusFilterMode.values()[state.getInt(STATUS_FILTER_MODE)]
            trackerFilter = state.getString(TRACKER_FILTER, "")
            directoryFilter = state.getString(DIRECTORY_FILTER, "")
        }
    }

    class TorrentsViewHolder(selector: Selector<Torrent, Int>,
                             multilineName: Boolean,
                             private val binding: TorrentListItemBinding) : BaseTorrentsViewHolder(selector, multilineName, binding.root) {
        init {
            Utils.setProgressBarColor(binding.progressBar)
        }

        override fun update(torrent: Torrent) {
            super.update(torrent)

            with(binding) {
                sizeTextView.text = if (torrent.isFinished) {
                    context.getString(R.string.uploaded_string,
                                      Utils.formatByteSize(context, torrent.sizeWhenDone),
                                      Utils.formatByteSize(context, torrent.totalUploaded))
                } else {
                    context.getString(R.string.completed_string,
                                      Utils.formatByteSize(context, torrent.completedSize),
                                      Utils.formatByteSize(context, torrent.sizeWhenDone),
                                      DecimalFormats.generic.format(torrent.percentDone * 100))
                }
                etaTextView.text = Utils.formatDuration(context, torrent.eta)

                progressBar.progress = (torrent.percentDone * 100).toInt()
                downloadSpeedTextView.text = context.getString(R.string.download_speed_string,
                                                               Utils.formatByteSpeed(context,
                                                                                     torrent.downloadSpeed))
                uploadSpeedTextView.text = context.getString(R.string.upload_speed_string,
                                                             Utils.formatByteSpeed(context,
                                                                                   torrent.uploadSpeed))

                statusTextView.text = torrent.statusString
            }
        }
    }

    class TorrentsViewHolderCompact(selector: Selector<Torrent, Int>,
                                    multilineName: Boolean,
                                    private val binding: TorrentListItemCompactBinding) : BaseTorrentsViewHolder(selector, multilineName, binding.root) {
        override fun update(torrent: Torrent) {
            super.update(torrent)

            downloadSpeedTextView.text = if (torrent.downloadSpeed == 0L) {
                ""
            } else {
                context.getString(R.string.download_speed_string,
                                   Utils.formatByteSpeed(context,
                                                         torrent.downloadSpeed))
            }

            uploadSpeedTextView.text = if (torrent.uploadSpeed == 0L) {
                ""
            } else {
                context.getString(R.string.upload_speed_string,
                                   Utils.formatByteSpeed(context,
                                                         torrent.uploadSpeed))
            }

            binding.progressTextView.text = context.getString(if (torrent.downloadSpeed != 0L || torrent.uploadSpeed != 0L) R.string.progress_string_with_dot else R.string.progress_string,
                                                              DecimalFormats.generic.format(torrent.percentDone * 100))
        }
    }

    open class BaseTorrentsViewHolder(selector: Selector<Torrent, Int>,
                                      multilineName: Boolean,
                                      itemView: View) : Selector.ViewHolder<Torrent>(selector, itemView) {
        override lateinit var item: Torrent

        protected val context: Context = itemView.context

        private val nameTextView = itemView.findViewById<TextView>(R.id.name_text_view)!!
        private val statusIconDrawable: Drawable = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR1) {
            nameTextView.compoundDrawables.first()
        } else {
            nameTextView.compoundDrawablesRelative.first()
        }
        protected val downloadSpeedTextView = itemView.findViewById<TextView>(R.id.download_speed_text_view)!!
        protected val uploadSpeedTextView = itemView.findViewById<TextView>(R.id.upload_speed_text_view)!!

        init {
            if (!multilineName) {
                nameTextView.ellipsize = TextUtils.TruncateAt.END
                nameTextView.maxLines = 1
                nameTextView.isSingleLine = true
            }
        }

        open fun update(torrent: Torrent) {
            item = torrent

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

            updateSelectedBackground()
        }

        override fun onClick(view: View) {
            if (selector.actionMode == null) {
                view.findNavController().safeNavigate(R.id.action_torrentsListFragment_to_torrentPropertiesFragment,
                                                      bundleOf(TorrentPropertiesFragment.HASH to item.hashString,
                                                               TorrentPropertiesFragment.NAME to item.name))
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
                val startEnabled = when (selector.selectedItems.first().status) {
                    TorrentData.Status.Paused,
                    TorrentData.Status.Errored -> true
                    else -> false
                }
                startItem!!.isEnabled = startEnabled
                pauseItem!!.isEnabled = !startEnabled
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
                R.id.start -> Rpc.nativeInstance.startTorrents(selector.selectedItems.map(Torrent::id).toIntArray())
                R.id.pause -> Rpc.nativeInstance.pauseTorrents(selector.selectedItems.map(Torrent::id).toIntArray())
                R.id.check -> Rpc.nativeInstance.checkTorrents(selector.selectedItems.map(Torrent::id).toIntArray())
                R.id.reannounce -> Rpc.nativeInstance.reannounceTorrents(selector.selectedItems.map(Torrent::id).toIntArray())
                R.id.set_location -> activity.findNavController(R.id.nav_host)
                        .safeNavigate(R.id.action_torrentsListFragment_to_setLocationDialogFragment,
                                      bundleOf(SetLocationDialogFragment.TORRENT_IDS to selector.selectedItems.map(Torrent::id).toIntArray(),
                                               SetLocationDialogFragment.LOCATION to selector.selectedItems.first().downloadDirectory))
                R.id.rename -> {
                    val torrent = selector.selectedItems.first()
                    activity.findNavController(R.id.nav_host).safeNavigate(R.id.action_torrentsListFragment_to_torrentRenameDialogFragment,
                                                                           bundleOf(TorrentFileRenameDialogFragment.TORRENT_ID to torrent.id,
                                                                                    TorrentFileRenameDialogFragment.FILE_PATH to torrent.name,
                                                                                    TorrentFileRenameDialogFragment.FILE_NAME to torrent.name))
                }
                R.id.remove -> activity.findNavController(R.id.nav_host)
                        .safeNavigate(R.id.action_torrentsListFragment_to_removeTorrentDialogFragment,
                                      bundleOf(RemoveTorrentDialogFragment.TORRENT_IDS to selector.selectedItems.map(Torrent::id).toIntArray()))
                else -> return false
            }

            return true
        }

        override fun onDestroyActionMode(mode: ActionMode) {
            startItem = null
            pauseItem = null
            setLocationItem = null
            super.onDestroyActionMode(mode)
        }
    }

    class SetLocationDialogFragment : NavigationDialogFragment() {
        companion object {
            const val TORRENT_IDS = "torrentIds"
            const val LOCATION = "location"
        }

        private var directoriesAdapter: AddTorrentDirectoriesAdapter? = null

        override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
            return createTextFieldDialog(requireContext(),
                                         null,
                                         SetLocationDialogBinding::inflate,
                                         R.id.download_directory_edit,
                                         R.id.download_directory_layout,
                                         getString(R.string.location),
                                         InputType.TYPE_TEXT_VARIATION_URI,
                                         requireArguments().getString(LOCATION),
                                         {
                                             it.downloadDirectoryLayout.downloadDirectoryEdit.let { edit ->
                                                 directoriesAdapter = AddTorrentDirectoriesAdapter(edit, savedInstanceState)
                                                 edit.setAdapter(directoriesAdapter)
                                             }
                                         },
                                         {
                                             Rpc.nativeInstance.setTorrentsLocation(requireArguments().getIntArray(TORRENT_IDS),
                                                                                    it.downloadDirectoryLayout.downloadDirectoryEdit.text.toString(),
                                                                                    it.moveFilesCheckBox.isChecked)
                                             directoriesAdapter?.save()
                                         })
        }

        override fun onSaveInstanceState(outState: Bundle) {
            directoriesAdapter?.saveInstanceState(outState)
        }
    }

    class RemoveTorrentDialogFragment : NavigationDialogFragment() {
        companion object {
            const val TORRENT_IDS = "torrentIds"
            const val POP_BACK_STACK = "popBackStack"
        }

        override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
            val ids = requireArguments().getIntArray(TORRENT_IDS)!!

            val builder = MaterialAlertDialogBuilder(requireContext());
            val binding = RemoveTorrentsDialogBinding.inflate(LayoutInflater.from(builder.context))

            binding.deleteFilesCheckBox.isChecked = Settings.deleteFiles

            return builder
                    .setMessage(if (ids.size == 1) getString(R.string.remove_torrent_message)
                                else resources.getQuantityString(R.plurals.remove_torrents_message,
                                                                 ids.size,
                                                                 ids.size))
                    .setView(binding.root)
                    .setNegativeButton(android.R.string.cancel, null)
                    .setPositiveButton(R.string.remove) { _, _ ->
                        Rpc.nativeInstance.removeTorrents(ids, binding.deleteFilesCheckBox.isChecked)
                        activity?.actionMode?.finish()
                        if (requireArguments().getBoolean(POP_BACK_STACK)) {
                            val id = (parentFragmentManager.primaryNavigationFragment as NavigationFragment).destinationId
                            val listener = object : NavController.OnDestinationChangedListener {
                                override fun onDestinationChanged(controller: NavController, destination: NavDestination, arguments: Bundle?) {
                                    if (destination.id == id) {
                                        navController.popBackStack()
                                        controller.removeOnDestinationChangedListener(this)
                                    }
                                }
                            }
                            navController.addOnDestinationChangedListener(listener)
                        }
                    }
                    .create()
        }
    }
}