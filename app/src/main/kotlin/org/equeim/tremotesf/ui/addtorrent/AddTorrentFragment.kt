// SPDX-FileCopyrightText: 2017-2024 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package org.equeim.tremotesf.ui.addtorrent

import android.app.Dialog
import android.content.DialogInterface
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.Parcelable
import android.view.LayoutInflater
import android.widget.Toast
import androidx.annotation.CallSuper
import androidx.annotation.LayoutRes
import androidx.annotation.MenuRes
import androidx.annotation.StringRes
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.setFragmentResult
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.withResumed
import androidx.navigation.NavDirections
import androidx.navigation.fragment.navArgs
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.android.material.progressindicator.CircularProgressIndicatorSpec
import com.google.android.material.progressindicator.IndeterminateDrawable
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.parcelize.Parcelize
import org.equeim.tremotesf.R
import org.equeim.tremotesf.databinding.MergingTrackersDialogBinding
import org.equeim.tremotesf.rpc.requests.torrentproperties.TorrentLimits
import org.equeim.tremotesf.ui.NavigationActivityViewModel
import org.equeim.tremotesf.ui.NavigationDialogFragment
import org.equeim.tremotesf.ui.NavigationFragment
import org.equeim.tremotesf.ui.Settings
import org.equeim.tremotesf.ui.addtorrent.MergingTrackersDialogFragment.Result.ButtonClicked
import org.equeim.tremotesf.ui.utils.parcelable
import timber.log.Timber
import kotlin.coroutines.CoroutineContext

abstract class AddTorrentFragment(
    @LayoutRes contentLayoutId: Int,
    @StringRes titleRes: Int,
    @MenuRes toolbarMenuRes: Int,
) : NavigationFragment(contentLayoutId, titleRes, toolbarMenuRes) {
    protected lateinit var priorityItems: Array<String>
    protected val priorityItemEnums = arrayOf(
        TorrentLimits.BandwidthPriority.High,
        TorrentLimits.BandwidthPriority.Normal,
        TorrentLimits.BandwidthPriority.Low
    )

    private val activityModel: NavigationActivityViewModel by activityViewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        priorityItems = resources.getStringArray(R.array.priority_items)
    }

    override fun onViewStateRestored(savedInstanceState: Bundle?) {
        super.onViewStateRestored(savedInstanceState)
        toolbar.setNavigationOnClickListener {
            if (!requireActivity().isTaskRoot) {
                // FIXME: https://issuetracker.google.com/issues/145231159
                // For some reason it is needed to finish activity before navigateUp(),
                // otherwise we won't switch to our task in some cases
                requireActivity().finish()
            }
            navController.navigateUp()
        }
        MergingTrackersDialogFragment.setFragmentResultListener(this, ::onMergeTrackersDialogResult)
    }

    @CallSuper
    protected open fun navigateBack() {
        // This can be called very shortly after opening add torrent screen,
        // so be careful to avoid crashes
        lifecycleScope.launch {
            requiredActivity.withResumed {
                parentFragmentManager.executePendingTransactions()
                requiredActivity.onBackPressedDispatcher.onBackPressed()
            }
        }
    }

    @CallSuper
    protected open fun onMergeTrackersDialogResult(result: MergingTrackersDialogFragment.Result) {
        Timber.d("onMergeTrackersDialogResult() called with: result = $result")
        if ((result as? ButtonClicked)?.doNotAskAgain == true) {
            @OptIn(DelicateCoroutinesApi::class)
            GlobalScope.launch {
                Settings.askForMergingTrackersWhenAddingExistingTorrent.set(false)
                Settings.mergeTrackersWhenAddingExistingTorrent.set(result.merge)
            }
        }
    }

    private fun showMessageWhenMergingTrackers(torrentName: String) {
        val inOtherAppsTask = !requiredActivity.isTaskRoot
        if (!inOtherAppsTask) {
            activityModel.showSnackbarMessage(
                R.string.torrent_duplicate_merging_trackers,
                torrentName
            )
        } else {
            Toast.makeText(
                requireContext(),
                getString(
                    R.string.torrent_duplicate_merging_trackers,
                    torrentName
                ),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun showMessageWhenTorrentAlreadyExists(torrentName: String) {
        val inOtherAppsTask = !requiredActivity.isTaskRoot
        if (!inOtherAppsTask) {
            activityModel.showSnackbarMessage(R.string.torrent_duplicate_not_merging_trackers, torrentName)
        } else {
            Toast.makeText(requireContext(), getString(R.string.torrent_duplicate_not_merging_trackers, torrentName), Toast.LENGTH_SHORT).show()
        }
    }

    protected fun updateAddTorrentLinkState(
        state: AddTorrentState?,
        coroutineContext: CoroutineContext,
        addButton: ExtendedFloatingActionButton,
        mergingTrackersDialogDirectionsProvider: (String) -> NavDirections,
    ) {
        val shouldBeAskingForMergingTrackers = state is AddTorrentState.AskingForMergingTrackers
        val showingMergeTrackersDialog = navController.currentDestination?.id == R.id.merging_trackers_dialog_fragment
        if (showingMergeTrackersDialog != shouldBeAskingForMergingTrackers) {
            if (shouldBeAskingForMergingTrackers) {
                navigate(mergingTrackersDialogDirectionsProvider((state as AddTorrentState.AskingForMergingTrackers).torrentName))
            } else {
                navController.popBackStack()
            }
        }

        val checkingIfTorrentExists = state is AddTorrentState.CheckingIfTorrentExists
        addButton.apply {
            if (isClickable != !checkingIfTorrentExists) {
                isClickable = !checkingIfTorrentExists
                if (checkingIfTorrentExists) {
                    icon = createProgressDrawableForFAB()
                } else {
                    setIconResource(R.drawable.ic_done_24dp)
                }
            }
        }

        when (state) {
            is AddTorrentState.AddedTorrent, is AddTorrentState.MergedTrackers, is AddTorrentState.DidNotMergeTrackers -> {
                when (state) {
                    is AddTorrentState.MergedTrackers -> if (!state.afterAsking) {
                        showMessageWhenMergingTrackers(state.torrentName)
                    }
                    is AddTorrentState.DidNotMergeTrackers -> if (!state.afterAsking) {
                        showMessageWhenTorrentAlreadyExists(state.torrentName)
                    }
                    else -> Unit
                }
                navigateBack()
                coroutineContext.cancel()
            }

            else -> Unit
        }
    }

    private fun createProgressDrawableForFAB(): Drawable =
        IndeterminateDrawable.createCircularDrawable(
            requireContext(),
            CircularProgressIndicatorSpec(
                requireContext(),
                null,
                0,
                R.style.Widget_Tremotesf_FABCircularProgressIndicator
            )
        )

    sealed interface AddTorrentState {
        data object CheckingIfTorrentExists : AddTorrentState
        data object AddedTorrent : AddTorrentState
        data class AskingForMergingTrackers(val torrentName: String) : AddTorrentState
        data class MergedTrackers(val torrentName: String, val afterAsking: Boolean) : AddTorrentState
        data class DidNotMergeTrackers(val torrentName: String, val afterAsking: Boolean) : AddTorrentState
    }
}

class MergingTrackersDialogFragment : NavigationDialogFragment() {
    private val args: MergingTrackersDialogFragmentArgs by navArgs()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        isCancelable = args.cancelable
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val builder = MaterialAlertDialogBuilder(requireContext())
        val binding = MergingTrackersDialogBinding.inflate(LayoutInflater.from(builder.context))
        return builder
            .setMessage(
                if (args.torrentName != null) getString(R.string.torrent_duplicate_merging_trackers_question, args.torrentName)
                else getText(R.string.torrent_duplicate_merging_trackers_question_without_name)
            )
            .setView(binding.root)
            .setNegativeButton(R.string.merge_no) { _, _ ->
                setFragmentResult(
                    REQUEST_KEY,
                    bundleOf(RESULT_KEY to ButtonClicked(merge = false, doNotAskAgain = binding.dontAskCheckBox.isChecked))
                )
            }
            .setPositiveButton(R.string.merge_yes) { _, _ ->
                setFragmentResult(
                    REQUEST_KEY,
                    bundleOf(RESULT_KEY to ButtonClicked(merge = true, doNotAskAgain = binding.dontAskCheckBox.isChecked))
                )
            }
            .create()
    }

    override fun onCancel(dialog: DialogInterface) {
        setFragmentResult(
            REQUEST_KEY,
            bundleOf(RESULT_KEY to Result.Cancelled)
        )
    }

    sealed interface Result : Parcelable {
        @Parcelize
        data class ButtonClicked(
            val merge: Boolean,
            val doNotAskAgain: Boolean,
        ) : Result

        @Parcelize
        data object Cancelled : Result
    }

    companion object {
        private val REQUEST_KEY = MergingTrackersDialogFragment::class.qualifiedName!!
        private val RESULT_KEY = Result::class.qualifiedName!!

        fun setFragmentResultListener(fragment: Fragment, listener: (Result) -> Unit) {
            fragment.parentFragmentManager.setFragmentResultListener(
                REQUEST_KEY,
                fragment.viewLifecycleOwner
            ) { _, bundle ->
                bundle.parcelable<Result>(RESULT_KEY)?.let(listener)
            }
        }
    }
}
