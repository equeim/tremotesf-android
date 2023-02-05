// SPDX-FileCopyrightText: 2017-2022 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package org.equeim.tremotesf.ui.torrentpropertiesfragment

import android.os.Bundle
import android.view.View
import androidx.fragment.app.viewModels
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import org.equeim.tremotesf.R
import org.equeim.tremotesf.databinding.TorrentFilesFragmentBinding
import org.equeim.tremotesf.rpc.GlobalRpc
import org.equeim.tremotesf.ui.navController
import org.equeim.tremotesf.ui.utils.launchAndCollectWhenStarted
import org.equeim.tremotesf.ui.utils.viewLifecycleObject


class TorrentFilesFragment :
    TorrentPropertiesFragment.PagerFragment(R.layout.torrent_files_fragment, TorrentPropertiesFragment.PagerAdapter.Tab.Files) {

    private val model by viewModels<TorrentFilesFragmentViewModel>(::requireParentFragment) {
        viewModelFactory {
            initializer {
                TorrentFilesFragmentViewModel(
                    TorrentPropertiesFragmentViewModel.get(navController).torrent,
                    createSavedStateHandle()
                )
            }
        }
    }

    private val binding by viewLifecycleObject(TorrentFilesFragmentBinding::bind)

    override fun onViewStateRestored(savedInstanceState: Bundle?) {
        super.onViewStateRestored(savedInstanceState)

        val filesAdapter = TorrentFilesAdapter(model, this)
        binding.filesView.apply {
            adapter = filesAdapter
            layoutManager = LinearLayoutManager(requireContext())
            addItemDecoration(DividerItemDecoration(requireContext(), DividerItemDecoration.VERTICAL))
            (itemAnimator as DefaultItemAnimator).supportsChangeAnimations = false
        }

        model.state.launchAndCollectWhenStarted(viewLifecycleOwner) { state ->
            updatePlaceholder(state)
            updateProgressBar(state)
        }

        model.filesTree.items.launchAndCollectWhenStarted(viewLifecycleOwner, filesAdapter::update)

        GlobalRpc.torrentFileRenamedEvents.launchAndCollectWhenStarted(viewLifecycleOwner) { (torrentId, filePath, newName) ->
            if (torrentId == model.torrent.value?.id) {
                model.filesTree.renameFile(filePath, newName)
            }
        }
    }

    override fun onToolbarClicked() {
        binding.filesView.scrollToPosition(0)
    }

    override fun onNavigatedFromParent() {
        model.destroy()
    }

    private fun updatePlaceholder(modelState: TorrentFilesFragmentViewModel.State) {
        binding.placeholder.visibility =
            if (modelState == TorrentFilesFragmentViewModel.State.TreeCreated && model.filesTree.isEmpty) {
                View.VISIBLE
            } else {
                View.GONE
            }
    }

    private fun updateProgressBar(modelState: TorrentFilesFragmentViewModel.State) {
        binding.progressBar.visibility = when (modelState) {
            TorrentFilesFragmentViewModel.State.Loading,
            TorrentFilesFragmentViewModel.State.CreatingTree -> View.VISIBLE
            else -> View.GONE
        }
    }
}
