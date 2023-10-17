// SPDX-FileCopyrightText: 2017-2023 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package org.equeim.tremotesf.ui.torrentpropertiesfragment

import android.os.Bundle
import androidx.fragment.app.viewModels
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.coroutines.flow.StateFlow
import org.equeim.tremotesf.R
import org.equeim.tremotesf.databinding.TorrentFilesFragmentBinding
import org.equeim.tremotesf.ui.utils.hide
import org.equeim.tremotesf.ui.utils.launchAndCollectWhenStarted
import org.equeim.tremotesf.ui.utils.showError
import org.equeim.tremotesf.ui.utils.showLoading
import org.equeim.tremotesf.ui.utils.viewLifecycleObject


class TorrentFilesFragment :
    TorrentPropertiesFragment.PagerFragment(
        R.layout.torrent_files_fragment,
        TorrentPropertiesFragment.PagerAdapter.Tab.Files
    ) {

    private val torrentPropertiesFragmentViewModel by TorrentPropertiesFragmentViewModel.from(this)
    private val model by viewModels<TorrentFilesFragmentViewModel>(::requireParentFragment) {
        viewModelFactory {
            initializer {
                TorrentFilesFragmentViewModel(
                    torrentPropertiesFragmentViewModel.args.torrentHashString,
                    createSavedStateHandle(),
                    torrentPropertiesFragmentViewModel.torrentFileRenamedEvents
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

        model.state.launchAndCollectWhenStarted(viewLifecycleOwner, ::updatePlaceholder)

        model.filesTree.items.launchAndCollectWhenStarted(viewLifecycleOwner, filesAdapter::update)
    }

    fun isAtRootOfTree(): StateFlow<Boolean> = model.filesTree.isAtRoot
    fun navigateUp(): Boolean = model.filesTree.navigateUp()

    override fun onToolbarClicked() {
        binding.filesView.scrollToPosition(0)
    }

    override fun onNavigatedFromParent() {
        model.destroy()
    }

    private fun updatePlaceholder(modelState: TorrentFilesFragmentViewModel.State) = with(binding.placeholderView) {
        if (modelState is TorrentFilesFragmentViewModel.State.TreeCreated && !model.filesTree.isEmpty) {
            hide()
        } else {
            when (modelState) {
                is TorrentFilesFragmentViewModel.State.CreatingTree, is TorrentFilesFragmentViewModel.State.Loading -> showLoading()
                is TorrentFilesFragmentViewModel.State.Error -> showError(modelState.error)
                is TorrentFilesFragmentViewModel.State.TorrentNotFound -> showError(getText(R.string.torrent_not_found))
                is TorrentFilesFragmentViewModel.State.TreeCreated -> showError(getText(R.string.no_files))
            }
        }
    }
}
