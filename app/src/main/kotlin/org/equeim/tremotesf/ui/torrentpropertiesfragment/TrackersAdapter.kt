// SPDX-FileCopyrightText: 2017-2024 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package org.equeim.tremotesf.ui.torrentpropertiesfragment

import android.app.Dialog
import android.os.Bundle
import android.os.Parcelable
import android.text.InputType
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.view.ActionMode
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.setFragmentResult
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.DiffUtil
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.parcelize.Parcelize
import org.equeim.tremotesf.R
import org.equeim.tremotesf.common.AlphanumericComparator
import org.equeim.tremotesf.databinding.AddTrackersDialogBinding
import org.equeim.tremotesf.databinding.TrackerListItemBinding
import org.equeim.tremotesf.rpc.requests.torrentproperties.Tracker
import org.equeim.tremotesf.ui.NavigationDialogFragment
import org.equeim.tremotesf.ui.SelectionTracker
import org.equeim.tremotesf.ui.navigate
import org.equeim.tremotesf.ui.utils.AsyncLoadingListAdapter
import org.equeim.tremotesf.ui.utils.bindingAdapterPositionOrNull
import org.equeim.tremotesf.ui.utils.createTextFieldDialog
import org.equeim.tremotesf.ui.utils.parcelable
import org.equeim.tremotesf.ui.utils.submitListAwait
import timber.log.Timber
import java.time.Duration
import java.time.Instant
import kotlin.time.Duration.Companion.seconds


data class TrackersAdapterItem(
    val tracker: Tracker,
    val nextUpdateEta: Duration? = tracker.nextUpdateTime?.let { Duration.between(Instant.now(), it) },
) {
    fun withUpdatedNextUpdateEta(): TrackersAdapterItem = if (tracker.nextUpdateTime == null) {
        this
    } else {
        copy(nextUpdateEta = tracker.nextUpdateTime.let { Duration.between(Instant.now(), it) })
    }
}

class TrackersAdapter(
    private val fragment: TrackersFragment,
) : AsyncLoadingListAdapter<TrackersAdapterItem, TrackersAdapter.ViewHolder>(Callback()) {
    private var etaUpdateJob: Job? = null

    private val comparator = object : Comparator<TrackersAdapterItem> {
        private val stringComparator = AlphanumericComparator()
        override fun compare(o1: TrackersAdapterItem, o2: TrackersAdapterItem) =
            stringComparator.compare(
                o1.tracker.announceUrl,
                o2.tracker.announceUrl
            )
    }

    private val context = fragment.requireContext()

    private val selectionTracker = SelectionTracker.createForIntKeys(
        this,
        true,
        fragment,
        ::ActionModeCallback,
        R.plurals.trackers_selected
    ) { getItem(it).tracker.id }

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

    suspend fun update(trackers: List<Tracker>?) {
        Timber.d("update() called with: trackers = $trackers")
        etaUpdateJob?.cancel()
        etaUpdateJob = null
        val newTrackers = trackers?.map(::TrackersAdapterItem)
        submitListAwait(newTrackers?.sortedWith(comparator))
        selectionTracker.commitAdapterUpdate()
        if (newTrackers != null && newTrackers.any { it.tracker.nextUpdateTime != null }) {
            updateEtaPeriodically()
        }
    }

    private fun updateEtaPeriodically() {
        etaUpdateJob?.cancel()
        val lifecycleOwner = fragment.viewLifecycleOwner
        etaUpdateJob = lifecycleOwner.lifecycleScope.launch {
            lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
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
        val binding: TrackerListItemBinding,
    ) : SelectionTracker.ViewHolder<Int>(selectionTracker, binding.root) {
        override fun onClick(view: View) {
            val position = bindingAdapterPositionOrNull ?: return
            val tracker = getItem(position)
            fragment.navigate(
                TorrentPropertiesFragmentDirections.toEditTrackerDialog(
                    tracker.tracker.id,
                    tracker.tracker.announceUrl
                )
            )
        }

        override fun update() {
            super.update()
            val item = bindingAdapterPositionOrNull?.let(::getItem) ?: return
            with(binding) {
                nameTextView.text = item.tracker.announceUrl
                statusTextView.text = context.getText(
                    when (item.tracker.status) {
                        Tracker.Status.Inactive -> R.string.tracker_inactive
                        Tracker.Status.WaitingForUpdate -> R.string.tracker_waiting_for_update
                        Tracker.Status.QueuedForUpdate -> R.string.tracker_queued_for_update
                        Tracker.Status.Updating -> R.string.tracker_updating
                    }
                )
                nextUpdateTextView.apply {
                    if (item.nextUpdateEta != null) {
                        isVisible = true
                        text = context.getString(
                            R.string.next_update,
                            DateUtils.formatElapsedTime(item.nextUpdateEta.seconds)
                        )
                    } else {
                        isVisible = false
                        text = null
                    }
                }
                errorTextView.apply {
                    if (item.tracker.errorMessage.isNullOrEmpty()) {
                        isVisible = false
                        text = null
                    } else {
                        isVisible = true
                        text = context.getString(R.string.tracker_error, item.tracker.errorMessage)
                    }
                }
                peersTextView.text = context.resources.getQuantityString(
                    R.plurals.peers_plural,
                    item.tracker.peers,
                    item.tracker.peers
                )
                seedersTextView.text = context.resources.getQuantityString(
                    R.plurals.seeders_plural,
                    item.tracker.seeders,
                    item.tracker.seeders
                )
                leechersTextView.text = context.resources.getQuantityString(
                    R.plurals.leechers_plural,
                    item.tracker.leechers,
                    item.tracker.leechers
                )
            }
        }
    }

    private class Callback : DiffUtil.ItemCallback<TrackersAdapterItem>() {
        override fun areItemsTheSame(
            oldItem: TrackersAdapterItem,
            newItem: TrackersAdapterItem,
        ): Boolean {
            return oldItem.tracker.id == newItem.tracker.id
        }

        override fun areContentsTheSame(
            oldItem: TrackersAdapterItem,
            newItem: TrackersAdapterItem,
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
                    TorrentPropertiesFragmentDirections.toRemoveTrackersDialog(
                        selectionTracker.selectedKeys.toIntArray()
                    )
                )
                return true
            }

            return false
        }
    }
}

class AddTrackersDialogFragment : NavigationDialogFragment() {
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return createTextFieldDialog(
            context = requireContext(),
            title = R.string.add_trackers,
            viewBindingFactory = AddTrackersDialogBinding::inflate,
            textFieldId = R.id.text_field,
            textFieldLayoutId = R.id.text_field_layout,
            hint = getString(R.string.trackers_announce_urls),
            inputType = InputType.TYPE_TEXT_VARIATION_URI,
            defaultText = null,
            onInflatedView = null
        ) { binding ->
            val announceUrls = binding.textField.text?.lineSequence()?.filter(String::isNotEmpty)?.toList().orEmpty()
            setFragmentResult(
                AddTrackersDialogFragment::class.qualifiedName!!,
                Result(announceUrls).toBundle()
            )
        }
    }

    @Parcelize
    data class Result(
        val announceUrls: List<String>,
    ) : Parcelable {
        fun toBundle(): Bundle = bundleOf("" to this)

        companion object {
            fun fromBundle(bundle: Bundle): Result = requireNotNull(bundle.parcelable(""))
        }
    }
}

class EditTrackerDialogFragment : NavigationDialogFragment() {
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val args = EditTrackerDialogFragmentArgs.fromBundle(requireArguments())
        return createTextFieldDialog(
            requireContext(),
            R.string.edit_tracker,
            getString(R.string.tracker_announce_url),
            InputType.TYPE_TEXT_VARIATION_URI,
            args.announceUrl,
            null
        ) {
            val announceUrl = it.textField.text?.toString()?.trim().orEmpty()
            setFragmentResult(
                EditTrackerDialogFragment::class.qualifiedName!!,
                Result(args.trackerId, announceUrl).toBundle()
            )
        }
    }

    @Parcelize
    data class Result(
        val trackerId: Int,
        val newAnnounceUrl: String,
    ) : Parcelable {
        fun toBundle(): Bundle = bundleOf("" to this)

        companion object {
            fun fromBundle(bundle: Bundle): Result = requireNotNull(bundle.parcelable(""))
        }
    }
}

class RemoveTrackersDialogFragment : NavigationDialogFragment() {
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val args = RemoveTrackersDialogFragmentArgs.fromBundle(requireArguments())
        return MaterialAlertDialogBuilder(requireContext())
            .setMessage(
                resources.getQuantityString(
                    R.plurals.remove_trackers_message,
                    args.trackerIds.size,
                    args.trackerIds.size
                )
            )
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.remove) { _, _ ->
                setFragmentResult(
                    RemoveTrackersDialogFragment::class.qualifiedName!!,
                    Result(args.trackerIds).toBundle()
                )
            }
            .create()
    }

    @Parcelize
    data class Result(
        val trackerIds: IntArray,
    ) : Parcelable {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as Result
            if (!trackerIds.contentEquals(other.trackerIds)) return false
            return true
        }

        override fun hashCode(): Int {
            return trackerIds.contentHashCode()
        }

        fun toBundle(): Bundle = bundleOf("" to this)

        companion object {
            fun fromBundle(bundle: Bundle): Result = requireNotNull(bundle.parcelable(""))
        }
    }
}
