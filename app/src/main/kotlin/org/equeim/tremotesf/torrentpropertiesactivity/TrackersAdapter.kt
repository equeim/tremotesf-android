/*
 * Copyright (C) 2017 Alexey Rochev <equeim@gmail.com>
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

package org.equeim.tremotesf.torrentpropertiesactivity

import java.text.Collator
import java.util.Comparator

import android.app.Dialog
import android.app.DialogFragment
import android.content.Context
import android.content.DialogInterface

import android.os.Bundle

import android.text.Editable
import android.text.TextWatcher
import android.text.format.DateUtils

import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.ViewGroup
import android.view.View
import android.view.inputmethod.InputMethodManager

import android.widget.TextView

import android.support.v7.app.AlertDialog
import android.support.v7.view.ActionMode
import android.support.v7.widget.RecyclerView
import android.text.InputType

import com.amjjd.alphanum.AlphanumericComparator

import org.equeim.tremotesf.R
import org.equeim.tremotesf.Selector
import org.equeim.tremotesf.Torrent
import org.equeim.tremotesf.Tracker
import org.equeim.tremotesf.utils.createTextFieldDialog


class TrackersAdapter(private val activity: TorrentPropertiesActivity) : RecyclerView.Adapter<TrackersAdapter.ViewHolder>() {
    private var torrent: Torrent? = null
    private val trackers = mutableListOf<Tracker>()
    private val comparator = object : Comparator<Tracker> {
        private val stringComparator = AlphanumericComparator(Collator.getInstance())
        override fun compare(o1: Tracker, o2: Tracker) = stringComparator.compare(o1.announce,
                                                                                  o2.announce)
    }

    val selector = Selector(activity,
                            ActionModeCallback(),
                            this,
                            trackers,
                            Tracker::id,
                            R.plurals.trackers_selected)

    override fun getItemCount(): Int {
        return trackers.size
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(selector,
                          LayoutInflater.from(parent.context).inflate(R.layout.tracker_list_item,
                                                                      parent,
                                                                      false))
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val tracker = trackers[position]

        holder.item = tracker
        holder.nameTextView.text = tracker.announce
        holder.statusTextView.text = tracker.statusString

        if (tracker.status == Tracker.Status.Error) {
            holder.peersTextView.visibility = View.GONE
        } else {
            holder.peersTextView.text = activity.resources.getQuantityString(R.plurals.peers_plural,
                                                                             tracker.peers,
                                                                             tracker.peers)
            holder.peersTextView.visibility = View.VISIBLE
        }

        if (tracker.nextUpdate < 0) {
            holder.nextUpdateTextView.visibility = View.GONE
        } else {
            holder.nextUpdateTextView.text = activity.getString(R.string.next_update,
                                                                DateUtils.formatElapsedTime(tracker.nextUpdate))
            holder.nextUpdateTextView.visibility = View.VISIBLE
        }

        holder.updateSelectedBackground()
    }

    fun update() {
        val torrent = activity.torrent

        if (torrent == null) {
            if (this.torrent == null) {
                return
            }
            this.torrent = null
            val count = itemCount
            trackers.clear()
            notifyItemRangeRemoved(0, count)
            selector.actionMode?.finish()
            return
        }

        this.torrent = torrent

        run {
            var i = 0
            while (i < trackers.size) {
                if (torrent.trackers.contains(trackers[i])) {
                    i++
                } else {
                    trackers.removeAt(i)
                    notifyItemRemoved(i)
                }
            }
        }

        selector.clearRemovedItems()

        for ((i, tracker) in torrent.trackers.sortedWith(comparator).withIndex()) {
            if (trackers.getOrNull(i) === tracker) {
                if (tracker.changed) {
                    notifyItemChanged(i)
                }
            } else {
                val index = trackers.indexOf(tracker)
                if (index == -1) {
                    trackers.add(i, tracker)
                    notifyItemInserted(i)
                } else {
                    trackers.removeAt(index)
                    trackers.add(i, tracker)
                    notifyItemMoved(index, i)
                    if (tracker.changed) {
                        notifyItemChanged(i)
                    }
                }
            }
        }
    }

    inner class ViewHolder(selector: Selector<Tracker, Int>,
                           itemView: View) : Selector.ViewHolder<Tracker>(selector, itemView) {
        override lateinit var item: Tracker
        val nameTextView = itemView.findViewById(R.id.name_text_view) as TextView
        val statusTextView = itemView.findViewById(R.id.status_text_view) as TextView
        val peersTextView = itemView.findViewById(R.id.peers_text_view) as TextView
        val nextUpdateTextView = itemView.findViewById(R.id.next_update_text_view) as TextView

        override fun onClick(view: View) {
            if (selector.actionMode == null) {
                if (activity.fragmentManager.findFragmentByTag(EditTrackerDialogFragment.TAG) == null) {
                    val fragment = EditTrackerDialogFragment()
                    val args = Bundle()
                    args.putInt(EditTrackerDialogFragment.TRACKER_ID, item.id)
                    args.putString(EditTrackerDialogFragment.ANNOUNCE, item.announce)
                    fragment.arguments = args
                    fragment.show(activity.fragmentManager, EditTrackerDialogFragment.TAG)
                }
            } else {
                super.onClick(view)
            }
        }
    }

    class EditTrackerDialogFragment : DialogFragment() {
        companion object {
            const val TAG = "org.equeim.tremotesf.TrackerAdapter.EditTrackerDialogFragment"
            const val TRACKER_ID = "trackerId"
            const val ANNOUNCE = "announce"
        }

        override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
            val trackerId = arguments?.getInt(TRACKER_ID) ?: -1

            return createTextFieldDialog(activity,
                                         if (trackerId == -1) R.string.add_tracker else R.string.edit_tracker,
                                         null,
                                         activity.getString(R.string.tracker_announce_url),
                                         InputType.TYPE_TEXT_VARIATION_URI,
                                         arguments?.getString(ANNOUNCE)) {
                val torrent = (activity as TorrentPropertiesActivity).torrent
                val textField = dialog.findViewById(R.id.text_field) as TextView
                if (trackerId == -1) {
                    torrent?.addTracker(textField.text.toString())
                } else {
                    torrent?.setTracker(trackerId, textField.text.toString())
                }
            }
        }
    }

    private inner class ActionModeCallback() : Selector.ActionModeCallback<Tracker>() {
        override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
            mode.menuInflater.inflate(R.menu.trackers_context_menu, menu)
            return true
        }

        override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
            if (super.onActionItemClicked(mode, item)) {
                return true
            }

            if (item.itemId == R.id.remove) {
                RemoveDialogFragment.create(selector.selectedItems.map(Tracker::id).toIntArray())
                        .show(activity.fragmentManager, RemoveDialogFragment.TAG)
                return true
            }

            return false
        }
    }

    class RemoveDialogFragment : DialogFragment() {
        companion object {
            const val TAG = "org.equeim.tremotesf.TorrentsAdapter.RemoveDialogFragment"

            fun create(ids: IntArray): RemoveDialogFragment {
                val fragment = RemoveDialogFragment()
                val arguments = Bundle()
                arguments.putIntArray("ids", ids)
                fragment.arguments = arguments
                return fragment
            }
        }

        override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
            val ids = arguments.getIntArray("ids")

            return AlertDialog.Builder(activity)
                    .setMessage(activity.resources.getQuantityString(R.plurals.remove_trackers_message,
                                                                     ids.size,
                                                                     ids.size))
                    .setNegativeButton(android.R.string.cancel, null)
                    .setPositiveButton(R.string.remove, { _, _ ->
                        val activity = this.activity as TorrentPropertiesActivity
                        activity.torrent?.removeTrackers(ids)
                        activity.actionMode?.finish()
                    }).create()
        }
    }
}