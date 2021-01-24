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

package org.equeim.tremotesf.ui.torrentpropertiesfragment

import android.app.Dialog
import android.os.Bundle
import android.text.InputType
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.TextView

import androidx.appcompat.view.ActionMode
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter

import com.google.android.material.dialog.MaterialAlertDialogBuilder

import org.equeim.libtremotesf.Tracker
import org.equeim.tremotesf.R
import org.equeim.tremotesf.databinding.AddTrackersDialogBinding
import org.equeim.tremotesf.databinding.TrackerListItemBinding
import org.equeim.tremotesf.data.rpc.Torrent
import org.equeim.tremotesf.ui.NavigationDialogFragment
import org.equeim.tremotesf.ui.SelectionTracker
import org.equeim.tremotesf.ui.utils.AlphanumericComparator
import org.equeim.tremotesf.ui.utils.createTextFieldDialog

import java.util.Comparator


data class TrackersAdapterItem(val id: Int,
                               var announce: String,
                               var status: Int,
                               var errorMessage: String,
                               var peers: Int,
                               private var nextUpdateTime: Long) {

    var nextUpdateEta = 0L

    constructor(rpcTracker: Tracker) : this(rpcTracker.id(),
                                            rpcTracker.announce(),
                                            rpcTracker.status(),
                                            rpcTracker.errorMessage(),
                                            rpcTracker.peers(),
                                            rpcTracker.nextUpdateTime())

    init {
        updateNextUpdateEta()
    }

    fun updatedFrom(rpcTracker: Tracker): TrackersAdapterItem {
        return copy(announce = rpcTracker.announce(),
                    status = rpcTracker.status(),
                    errorMessage = rpcTracker.errorMessage(),
                    peers = rpcTracker.peers(),
                    nextUpdateTime = rpcTracker.nextUpdateTime())
    }

    fun updateNextUpdateEta() {
        nextUpdateEta = if (nextUpdateTime > 0) {
            val eta = nextUpdateTime - System.currentTimeMillis() / 1000
            if (eta < 0) -1 else eta
        } else {
            -1
        }
    }
}

class TrackersAdapter(private val torrentPropertiesFragment: TorrentPropertiesFragment,
                      trackersFragment: TrackersFragment) : ListAdapter<TrackersAdapterItem, TrackersAdapter.ViewHolder>(Callback()) {
    private var torrent: Torrent? = null

    private val comparator = object : Comparator<TrackersAdapterItem> {
        private val stringComparator = AlphanumericComparator()
        override fun compare(o1: TrackersAdapterItem, o2: TrackersAdapterItem) = stringComparator.compare(o1.announce,
                                                                                                          o2.announce)
    }

    private val context = trackersFragment.requireContext()

    private val selectionTracker = SelectionTracker.createForIntKeys(this,
                                                                     true,
                                                                     trackersFragment,
                                                                     ::ActionModeCallback,
                                                                     R.plurals.trackers_selected) { getItem(it).id }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(selectionTracker,
                          TrackerListItemBinding.inflate(LayoutInflater.from(parent.context),
                                                         parent,
                                                         false))
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.update()

    fun update(torrent: Torrent?) {
        if (torrent == null) {
            if (this.torrent == null) {
                return
            }
            this.torrent = null
            submitList(null)
            return
        }

        this.torrent = torrent

        val trackers = currentList

        if (!torrent.isChanged && !(trackers.isEmpty() && torrent.trackerSites.isNotEmpty())) {
            trackers.forEach(TrackersAdapterItem::updateNextUpdateEta)
            notifyItemRangeChanged(0, trackers.size)
            return
        }

        val newTrackers = mutableListOf<TrackersAdapterItem>()
        val rpcTrackers = torrent.trackers
        for (rpcTracker: Tracker in rpcTrackers) {
            val id = rpcTracker.id()
            var tracker = trackers.find { it.id == id }
            tracker = tracker?.updatedFrom(rpcTracker) ?: TrackersAdapterItem(rpcTracker)
            newTrackers.add(tracker)
        }

        submitList(if (newTrackers.isEmpty()) null else newTrackers.sortedWith(comparator)) {
            selectionTracker.commitAdapterUpdate()
            selectionTracker.restoreInstanceState()
        }
    }

    inner class ViewHolder(selectionTracker: SelectionTracker<Int>,
                           val binding: TrackerListItemBinding) : SelectionTracker.ViewHolder<Int>(selectionTracker, binding.root) {
        override fun onClick(view: View) {
            val tracker = getItem(adapterPosition)
            torrentPropertiesFragment.navigate(TorrentPropertiesFragmentDirections.editTrackerDialogFragment(tracker.id, tracker.announce))
        }

        override fun update() {
            super.update()
            val tracker = getItem(adapterPosition)
            with(binding) {
                nameTextView.text = tracker.announce
                statusTextView.text = when (tracker.status) {
                    Tracker.Status.Inactive -> context.getString(R.string.tracker_inactive)
                    Tracker.Status.Active -> context.getString(R.string.tracker_active)
                    Tracker.Status.Queued -> context.getString(R.string.tracker_queued)
                    Tracker.Status.Updating -> context.getString(R.string.tracker_updating)
                    else -> {
                        if (tracker.errorMessage.isEmpty()) {
                            context.getString(R.string.error)
                        } else {
                            context.getString(R.string.tracker_error, tracker.errorMessage)
                        }
                    }
                }

                if (tracker.status == Tracker.Status.Error) {
                    peersTextView.visibility = View.GONE
                } else {
                    peersTextView.text = context.resources.getQuantityString(R.plurals.peers_plural,
                                                                             tracker.peers,
                                                                             tracker.peers)
                    peersTextView.visibility = View.VISIBLE
                }

                if (tracker.nextUpdateEta < 0) {
                    nextUpdateTextView.visibility = View.GONE
                } else {
                    nextUpdateTextView.text = context.getString(R.string.next_update,
                                                                DateUtils.formatElapsedTime(tracker.nextUpdateEta))
                    nextUpdateTextView.visibility = View.VISIBLE
                }
            }
        }
    }

    private class Callback : DiffUtil.ItemCallback<TrackersAdapterItem>() {
        override fun areItemsTheSame(oldItem: TrackersAdapterItem, newItem: TrackersAdapterItem): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: TrackersAdapterItem, newItem: TrackersAdapterItem): Boolean {
            return oldItem == newItem
        }
    }

    private inner class ActionModeCallback(selectionTracker: SelectionTracker<Int>) : SelectionTracker.ActionModeCallback<Int>(selectionTracker) {
        override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
            mode.menuInflater.inflate(R.menu.trackers_context_menu, menu)
            return true
        }

        override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
            if (super.onActionItemClicked(mode, item)) {
                return true
            }

            if (item.itemId == R.id.remove) {
                torrentPropertiesFragment.navigate(TorrentPropertiesFragmentDirections.removeTrackerDialogFragment(selectionTracker.selectedKeys.toIntArray()))
                return true
            }

            return false
        }
    }
}

class EditTrackerDialogFragment : NavigationDialogFragment() {
    private val args: EditTrackerDialogFragmentArgs by navArgs()

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val addingTrackers = (args.trackerId == -1)

        val onAccepted = { textField: TextView ->
            val torrent = (parentFragmentManager.primaryNavigationFragment as? TorrentPropertiesFragment)?.torrent
            if (torrent != null) {
                textField.text?.let { text ->
                    if (addingTrackers) {
                        torrent.addTrackers(text.lines().filter(String::isNotEmpty))
                    } else {
                        torrent.setTracker(args.trackerId, text.toString())
                    }
                }
            }
        }

        if (addingTrackers) {
            return createTextFieldDialog(requireContext(),
                                         R.string.add_trackers,
                                         AddTrackersDialogBinding::inflate,
                                         R.id.text_field,
                                         R.id.text_field_layout,
                                         getString(R.string.trackers_announce_urls),
                                         InputType.TYPE_TEXT_VARIATION_URI,
                                         args.announce,
                                         null) {
                onAccepted(it.textField)
            }
        }
        return createTextFieldDialog(requireContext(),
                                     R.string.edit_tracker,
                                     getString(R.string.tracker_announce_url),
                                     InputType.TYPE_TEXT_VARIATION_URI,
                                     args.announce,
                                     null) {
            onAccepted(it.textField)
        }
    }
}

class RemoveTrackerDialogFragment : NavigationDialogFragment() {
    private val args: RemoveTrackerDialogFragmentArgs by navArgs()

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return MaterialAlertDialogBuilder(requireContext())
                .setMessage(resources.getQuantityString(R.plurals.remove_trackers_message,
                                                        args.ids.size,
                                                        args.ids.size))
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.remove) { _, _ ->
                    (parentFragmentManager.primaryNavigationFragment as? TorrentPropertiesFragment)?.torrent?.removeTrackers(args.ids)
                    requiredActivity.actionMode?.finish()
                }
                .create()
    }
}
