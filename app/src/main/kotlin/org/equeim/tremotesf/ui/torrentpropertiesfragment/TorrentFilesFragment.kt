/*
 * Copyright (C) 2017-2022 Alexey Rochev <equeim@gmail.com>
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
