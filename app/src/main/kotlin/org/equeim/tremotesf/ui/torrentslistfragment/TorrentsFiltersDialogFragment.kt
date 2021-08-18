package org.equeim.tremotesf.ui.torrentslistfragment

import android.os.Bundle
import android.view.View
import androidx.navigation.navGraphViewModels
import kotlinx.coroutines.flow.filter
import org.equeim.tremotesf.R
import org.equeim.tremotesf.databinding.TorrentsFiltersDialogFragmentBinding
import org.equeim.tremotesf.rpc.GlobalRpc
import org.equeim.tremotesf.ui.NavigationBottomSheetDialogFragment
import org.equeim.tremotesf.ui.Settings
import org.equeim.tremotesf.ui.utils.ArrayDropdownAdapter
import org.equeim.tremotesf.ui.utils.collectWhenStarted
import org.equeim.tremotesf.ui.utils.viewBinding

class TorrentsFiltersDialogFragment : NavigationBottomSheetDialogFragment(R.layout.torrents_filters_dialog_fragment) {
    private val model by navGraphViewModels<TorrentsListFragmentViewModel>(R.id.torrents_list_fragment)
    private val binding by viewBinding(TorrentsFiltersDialogFragmentBinding::bind)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        with (binding) {
            sortView.apply {
                setAdapter(ArrayDropdownAdapter(resources.getStringArray(R.array.sort_spinner_items)))
                setText(adapter.getItem(model.sortMode.value.ordinal) as String)
                setOnItemClickListener { _, _, position, _ ->
                    model.apply {
                        sortMode.value = TorrentsListFragmentViewModel.SortMode.values()[position]
                        if (GlobalRpc.isConnected.value) {
                            Settings.torrentsSortMode = sortMode.value
                        }
                    }
                }
            }

            updateSortViewLayoutIcon()
            sortViewLayout.setStartIconOnClickListener {
                with(model) {
                    sortOrder.value = sortOrder.value.inverted()
                    Settings.torrentsSortOrder = sortOrder.value
                    updateSortViewLayoutIcon()
                }
            }

            val statusFilterViewAdapter = StatusFilterViewAdapter(requireContext(), model, statusView)
            statusView.apply {
                setAdapter(statusFilterViewAdapter)
                setOnItemClickListener { _, _, position, _ ->
                    model.apply {
                        statusFilterMode.value =
                            TorrentsListFragmentViewModel.StatusFilterMode.values()[position]
                        if (GlobalRpc.isConnected.value) {
                            Settings.torrentsStatusFilter = statusFilterMode.value
                        }
                    }
                }
            }

            val trackersViewAdapter = TrackersViewAdapter(requireContext(), model, trackersView)
            trackersView.apply {
                setAdapter(trackersViewAdapter)
                setOnItemClickListener { _, _, position, _ ->
                    model.apply {
                        trackerFilter.value = trackersViewAdapter.getTrackerFilter(position)
                        if (GlobalRpc.isConnected.value) {
                            Settings.torrentsTrackerFilter = trackerFilter.value
                        }
                    }
                }
            }

            val directoriesViewAdapter = DirectoriesViewAdapter(requireContext(), model, directoriesView)
            directoriesView.apply {
                setAdapter(directoriesViewAdapter)
                setOnItemClickListener { _, _, position, _ ->
                    model.apply {
                        directoryFilter.value = directoriesViewAdapter.getDirectoryFilter(position)
                        if (GlobalRpc.isConnected.value) {
                            Settings.torrentsDirectoryFilter = directoryFilter.value
                        }
                    }
                }
            }

            GlobalRpc.torrents.collectWhenStarted(viewLifecycleOwner) {
                statusFilterViewAdapter.update(it)
                trackersViewAdapter.update(it)
                directoriesViewAdapter.update(it)
            }
        }

        GlobalRpc.isConnected.filter { !it }.collectWhenStarted(viewLifecycleOwner) {
            navController.popBackStack()
        }
    }

    private fun updateSortViewLayoutIcon() {
        val resId = if (model.sortOrder.value == TorrentsListFragmentViewModel.SortOrder.Descending) {
            R.drawable.sort_descending
        } else {
            R.drawable.sort_ascending
        }
        binding.sortViewLayout.setStartIconDrawable(resId)
    }
}
