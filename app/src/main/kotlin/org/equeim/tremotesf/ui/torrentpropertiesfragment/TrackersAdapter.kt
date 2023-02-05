// SPDX-FileCopyrightText: 2017-2022 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package org.equeim.tremotesf.ui.torrentpropertiesfragment

import android.app.Dialog
import android.os.Bundle
import android.text.InputType
import android.text.format.DateUtils
import android.view.*
import android.widget.TextView
import androidx.appcompat.view.ActionMode
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.whenStarted
import androidx.recyclerview.widget.DiffUtil
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.*
import org.equeim.libtremotesf.Tracker
import org.equeim.tremotesf.R
import org.equeim.tremotesf.common.AlphanumericComparator
import org.equeim.tremotesf.databinding.AddTrackersDialogBinding
import org.equeim.tremotesf.databinding.TrackerListItemBinding
import org.equeim.tremotesf.torrentfile.rpc.Torrent
import org.equeim.tremotesf.ui.NavigationDialogFragment
import org.equeim.tremotesf.ui.SelectionTracker
import org.equeim.tremotesf.ui.navigate
import org.equeim.tremotesf.ui.utils.AsyncLoadingListAdapter
import org.equeim.tremotesf.ui.utils.bindingAdapterPositionOrNull
import org.equeim.tremotesf.ui.utils.createTextFieldDialog
import org.equeim.tremotesf.ui.utils.submitListAwait
import org.threeten.bp.Duration
import org.threeten.bp.Instant
import kotlin.time.Duration.Companion.seconds


data class TrackersAdapterItem(
    val id: Int,
    val announce: String,
    val status: Tracker.Status,
    val errorMessage: String,
    val peers: Int,
    val nextUpdateTime: Instant?,
    val nextUpdateEta: Duration? = nextUpdateTime?.let { Duration.between(Instant.now(), nextUpdateTime) }
) {
    constructor(rpcTracker: Tracker) : this(
        rpcTracker.id(),
        rpcTracker.announce(),
        rpcTracker.status(),
        rpcTracker.errorMessage(),
        rpcTracker.peers(),
        rpcTracker.nextUpdateTime()
    )

    fun withUpdatedNextUpdateEta(): TrackersAdapterItem {
        return if (nextUpdateTime == null) {
            this
        } else {
            copy(nextUpdateEta = nextUpdateTime.let { Duration.between(Instant.now(), nextUpdateTime) })
        }
    }
}

class TrackersAdapter(
    private val fragment: TrackersFragment
) : AsyncLoadingListAdapter<TrackersAdapterItem, TrackersAdapter.ViewHolder>(Callback()) {
    private var torrent: Torrent? = null
    private var etaUpdateJob: Job? = null

    private val comparator = object : Comparator<TrackersAdapterItem> {
        private val stringComparator = AlphanumericComparator()
        override fun compare(o1: TrackersAdapterItem, o2: TrackersAdapterItem) =
            stringComparator.compare(
                o1.announce,
                o2.announce
            )
    }

    private val context = fragment.requireContext()

    private val selectionTracker = SelectionTracker.createForIntKeys(
        this,
        true,
        fragment,
        ::ActionModeCallback,
        R.plurals.trackers_selected
    ) { getItem(it).id }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(
            selectionTracker,
            TrackerListItemBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
        )
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.update()

    suspend fun update(torrent: Torrent?) {
        etaUpdateJob?.cancel()
        etaUpdateJob = null
        if (torrent == null) {
            if (this.torrent == null) {
                return
            }
            this.torrent = null
            submitList(null)
            return
        }
        this.torrent = torrent
        val newTrackers = torrent.trackers.map(::TrackersAdapterItem)
        submitListAwait(newTrackers.sortedWith(comparator))
        selectionTracker.commitAdapterUpdate()
        if (newTrackers.any { it.nextUpdateTime != null }) {
            updateEtaPeriodically()
        }
    }

    private fun updateEtaPeriodically() {
        etaUpdateJob?.cancel()
        val lifecycleOwner = fragment.viewLifecycleOwner
        etaUpdateJob = lifecycleOwner.lifecycleScope.launch {
            lifecycleOwner.whenStarted {
                while (currentCoroutineContext().isActive) {
                    delay(1.seconds)
                    val newTrackers = currentList.map(TrackersAdapterItem::withUpdatedNextUpdateEta)
                    submitList(newTrackers.sortedWith(comparator))
                }
            }
        }
    }

    override fun onStateRestored() {
        selectionTracker.restoreInstanceState()
    }

    inner class ViewHolder(
        selectionTracker: SelectionTracker<Int>,
        val binding: TrackerListItemBinding
    ) : SelectionTracker.ViewHolder<Int>(selectionTracker, binding.root) {
        override fun onClick(view: View) {
            val position = bindingAdapterPositionOrNull ?: return
            val tracker = getItem(position)
            fragment.navigate(
                TorrentPropertiesFragmentDirections.toEditTrackerDialog(
                    tracker.id,
                    tracker.announce
                )
            )
        }

        override fun update() {
            super.update()
            val tracker = bindingAdapterPositionOrNull?.let(::getItem) ?: return
            with(binding) {
                nameTextView.text = tracker.announce
                statusTextView.text = when (tracker.status) {
                    Tracker.Status.Inactive -> if (tracker.errorMessage.isNotEmpty()) {
                        context.getString(R.string.tracker_error, tracker.errorMessage)
                    } else {
                        context.getText(R.string.tracker_inactive)
                    }
                    Tracker.Status.WaitingForUpdate -> context.getText(R.string.tracker_waiting_for_update)
                    Tracker.Status.QueuedForUpdate -> context.getText(R.string.tracker_queued_for_update)
                    Tracker.Status.Updating -> context.getText(R.string.tracker_updating)
                }

                if (tracker.errorMessage.isNotEmpty()) {
                    peersTextView.visibility = View.GONE
                } else {
                    peersTextView.text = context.resources.getQuantityString(
                        R.plurals.peers_plural,
                        tracker.peers,
                        tracker.peers
                    )
                    peersTextView.visibility = View.VISIBLE
                }

                if (tracker.nextUpdateEta == null) {
                    nextUpdateTextView.visibility = View.GONE
                } else {
                    nextUpdateTextView.text = context.getString(
                        R.string.next_update,
                        DateUtils.formatElapsedTime(tracker.nextUpdateEta.seconds)
                    )
                    nextUpdateTextView.visibility = View.VISIBLE
                }
            }
        }
    }

    private class Callback : DiffUtil.ItemCallback<TrackersAdapterItem>() {
        override fun areItemsTheSame(
            oldItem: TrackersAdapterItem,
            newItem: TrackersAdapterItem
        ): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(
            oldItem: TrackersAdapterItem,
            newItem: TrackersAdapterItem
        ): Boolean {
            return oldItem == newItem
        }
    }

    private class ActionModeCallback(selectionTracker: SelectionTracker<Int>) :
        SelectionTracker.ActionModeCallback<Int>(selectionTracker) {

        override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
            mode.menuInflater.inflate(R.menu.trackers_context_menu, menu)
            return true
        }

        override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
            if (super.onActionItemClicked(mode, item)) {
                return true
            }

            val selectionTracker = this.selectionTracker ?: return false

            if (item.itemId == R.id.remove) {
                activity.navigate(
                    TorrentPropertiesFragmentDirections.toRemoveTrackerDialog(
                        selectionTracker.selectedKeys.toIntArray()
                    )
                )
                return true
            }

            return false
        }
    }
}

class EditTrackerDialogFragment : NavigationDialogFragment() {
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val args = EditTrackerDialogFragmentArgs.fromBundle(requireArguments())

        val addingTrackers = (args.trackerId == -1)

        val onAccepted = { textField: TextView ->
            val propertiesFragmentModel = TorrentPropertiesFragmentViewModel.get(navController)
            val torrent = propertiesFragmentModel.torrent.value
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
            return createTextFieldDialog(
                requireContext(),
                R.string.add_trackers,
                AddTrackersDialogBinding::inflate,
                R.id.text_field,
                R.id.text_field_layout,
                getString(R.string.trackers_announce_urls),
                InputType.TYPE_TEXT_VARIATION_URI,
                args.announce,
                null
            ) {
                onAccepted(it.textField)
            }
        }
        return createTextFieldDialog(
            requireContext(),
            R.string.edit_tracker,
            getString(R.string.tracker_announce_url),
            InputType.TYPE_TEXT_VARIATION_URI,
            args.announce,
            null
        ) {
            onAccepted(it.textField)
        }
    }
}

class RemoveTrackerDialogFragment : NavigationDialogFragment() {
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val args = RemoveTrackerDialogFragmentArgs.fromBundle(requireArguments())
        return MaterialAlertDialogBuilder(requireContext())
            .setMessage(
                resources.getQuantityString(
                    R.plurals.remove_trackers_message,
                    args.ids.size,
                    args.ids.size
                )
            )
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.remove) { _, _ ->
                val propertiesFragmentModel = TorrentPropertiesFragmentViewModel.get(navController)
                val torrent = propertiesFragmentModel.torrent.value
                torrent?.removeTrackers(args.ids)
                requiredActivity.actionMode?.finish()
            }
            .create()
    }
}
