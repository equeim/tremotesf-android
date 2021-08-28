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

import android.os.Bundle
import android.view.View
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import org.equeim.tremotesf.R
import org.equeim.tremotesf.databinding.TorrentFilesFragmentBinding
import org.equeim.tremotesf.rpc.GlobalRpc
import org.equeim.tremotesf.ui.utils.collectWhenStarted
import org.equeim.tremotesf.ui.utils.savedStateViewModelFactory
import org.equeim.tremotesf.ui.utils.viewBinding


class TorrentFilesFragment :
    TorrentPropertiesFragment.PagerFragment(R.layout.torrent_files_fragment) {

    private val model by viewModels<TorrentFilesFragmentViewModel>(::requireParentFragment) {
        savedStateViewModelFactory { _, handle ->
            TorrentFilesFragmentViewModel(
                TorrentPropertiesFragmentViewModel.get(this).torrent,
                handle
            )
        }
    }

    private val binding by viewBinding(TorrentFilesFragmentBinding::bind)
    private var adapter: TorrentFilesAdapter? = null

    override fun onViewStateRestored(savedInstanceState: Bundle?) {
        super.onViewStateRestored(savedInstanceState)

        val adapter = TorrentFilesAdapter(model, this)
        this.adapter = adapter

        binding.filesView.apply {
            this.adapter = adapter
            layoutManager = LinearLayoutManager(requireContext())
            addItemDecoration(DividerItemDecoration(requireContext(), DividerItemDecoration.VERTICAL))
            (itemAnimator as DefaultItemAnimator).supportsChangeAnimations = false
        }

        model.state.collectWhenStarted(viewLifecycleOwner) { state ->
            updatePlaceholder(state)
            updateProgressBar(state)
        }

        model.filesTree.items.collectWhenStarted(viewLifecycleOwner, adapter::update)

        GlobalRpc.torrentFileRenamedEvents.collectWhenStarted(viewLifecycleOwner) { (torrentId, filePath, newName) ->
            if (torrentId == model.torrent.value?.id) {
                model.filesTree.renameFile(filePath, newName)
            }
        }
    }

    override fun onDestroyView() {
        adapter = null
        super.onDestroyView()
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
