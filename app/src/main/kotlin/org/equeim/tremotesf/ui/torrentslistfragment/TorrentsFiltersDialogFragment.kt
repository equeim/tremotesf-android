// SPDX-FileCopyrightText: 2017-2025 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package org.equeim.tremotesf.ui.torrentslistfragment

import android.os.Bundle
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.lifecycle.viewModelScope
import androidx.navigation.navGraphViewModels
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.equeim.tremotesf.R
import org.equeim.tremotesf.databinding.TorrentsFiltersDialogFragmentBinding
import org.equeim.tremotesf.rpc.RpcRequestState
import org.equeim.tremotesf.ui.NavigationBottomSheetDialogFragment
import org.equeim.tremotesf.ui.utils.ArrayDropdownAdapter
import org.equeim.tremotesf.ui.utils.launchAndCollectWhenStarted
import org.equeim.tremotesf.ui.utils.viewLifecycleObject

private const val RESET_BUTTON_HIDE_DELAY_MS = 100L

class TorrentsFiltersDialogFragment : NavigationBottomSheetDialogFragment(R.layout.torrents_filters_dialog_fragment) {
    private val model by navGraphViewModels<TorrentsListFragmentViewModel>(R.id.torrents_list_fragment)
    private val binding by viewLifecycleObject(TorrentsFiltersDialogFragmentBinding::bind)

    override fun onViewStateRestored(savedInstanceState: Bundle?) {
        super.onViewStateRestored(savedInstanceState)
        with(binding) {
            sortView.apply {
                setAdapter(ArrayDropdownAdapter(resources.getStringArray(R.array.sort_spinner_items)))
                setOnItemClickListener { _, _, position, _ ->
                    model.setSortMode(TorrentsListFragmentViewModel.SortMode.entries[position])
                }
            }

            sortViewLayout.setStartIconOnClickListener {
                with(model) {
                    viewModelScope.launch {
                        setSortOrder(sortOrder.first().inverted())
                    }
                }
            }

            val statusFilterViewAdapter = StatusFilterViewAdapter(requireContext(), statusView)
            statusView.apply {
                setAdapter(statusFilterViewAdapter)
                setOnItemClickListener { _, _, position, _ ->
                    model.setStatusFilterMode(
                        TorrentsListFragmentViewModel.StatusFilterMode.entries[position]
                    )
                }
            }

            val labelsViewAdapter = LabelsViewAdapter(requireContext(), labelsView)
            labelsView.apply {
                setAdapter(labelsViewAdapter)
                setOnItemClickListener { _, _, position, _ ->
                    model.setLabelFilter(labelsViewAdapter.getLabel(position).orEmpty())
                }
            }

            val trackersViewAdapter = TrackersViewAdapter(requireContext(), trackersView)
            trackersView.apply {
                setAdapter(trackersViewAdapter)
                setOnItemClickListener { _, _, position, _ ->
                    model.setTrackerFilter(trackersViewAdapter.getTrackerFilter(position).orEmpty())
                }
            }

            val directoriesViewAdapter = DirectoriesViewAdapter(requireContext(), directoriesView)
            directoriesView.apply {
                setAdapter(directoriesViewAdapter)
                setOnItemClickListener { _, _, position, _ ->
                    model.setDirectoryFilter(directoriesViewAdapter.getDirectoryPath(position).orEmpty())
                }
            }

            combine(model.sortOrder, model.sortMode, ::Pair)
                .launchAndCollectWhenStarted(viewLifecycleOwner) { (sortOrder, sortMode) ->
                    updateSortView(sortOrder, sortMode)
                }

            val allTorrents = model.allTorrents.map { (it as? RpcRequestState.Loaded)?.response.orEmpty() }

            combine(allTorrents, model.statusFilterMode, ::Pair)
                .launchAndCollectWhenStarted(viewLifecycleOwner) { (torrents, statusFilterMode) ->
                    statusFilterViewAdapter.update(torrents, statusFilterMode)
                }

            combine(allTorrents, model.labelFilter, ::Pair)
                .launchAndCollectWhenStarted(viewLifecycleOwner) { (torrents, labelFilter) ->
                    if (labelFilter == null) {
                        labelsViewLayout.isVisible = false
                    } else {
                        labelsViewLayout.isVisible = true
                        labelsViewAdapter.update(torrents, labelFilter)
                    }
                }

            combine(allTorrents, model.trackerFilter, ::Pair)
                .launchAndCollectWhenStarted(viewLifecycleOwner) { (torrents, trackerFilter) ->
                    trackersViewAdapter.update(torrents, trackerFilter)
                }

            combine(allTorrents, model.directoryFilter, ::Pair)
                .launchAndCollectWhenStarted(viewLifecycleOwner) { (torrents, directoryFilter) ->
                    directoriesViewAdapter.update(torrents, directoryFilter)
                }

            resetButton.setOnClickListener { model.resetSortAndFilters() }
            resetButton.isInvisible = true
            model.sortOrFiltersEnabled.launchAndCollectWhenStarted(viewLifecycleOwner) {
                if (it) {
                    resetButton.isInvisible = false
                } else {
                    if (resetButton.isVisible) {
                        delay(RESET_BUTTON_HIDE_DELAY_MS)
                    }
                    resetButton.isInvisible = true
                }
            }
        }

        model.torrentsListState.filter { it !is RpcRequestState.Loaded }
            .launchAndCollectWhenStarted(viewLifecycleOwner) {
                navController.popBackStack()
            }
    }

    private fun updateSortView(
        sortOrder: TorrentsListFragmentViewModel.SortOrder,
        sortMode: TorrentsListFragmentViewModel.SortMode,
    ) {
        with(binding) {
            val resId = if (sortOrder == TorrentsListFragmentViewModel.SortOrder.Descending) {
                R.drawable.sort_descending
            } else {
                R.drawable.sort_ascending
            }
            sortViewLayout.setStartIconDrawable(resId)

            sortView.apply {
                setText(adapter.getItem(sortMode.ordinal) as String)
            }
        }
    }
}
