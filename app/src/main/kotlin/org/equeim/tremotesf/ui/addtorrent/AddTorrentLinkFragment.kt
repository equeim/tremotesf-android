// SPDX-FileCopyrightText: 2017-2022 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package org.equeim.tremotesf.ui.addtorrent

import android.os.Bundle
import android.view.DragEvent
import android.view.View
import android.view.View.OnDragListener
import androidx.core.text.trimmedLength
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.fragment.navArgs
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.equeim.tremotesf.R
import org.equeim.tremotesf.databinding.AddTorrentLinkFragmentBinding
import org.equeim.tremotesf.rpc.GlobalRpcClient
import org.equeim.tremotesf.rpc.getErrorString
import org.equeim.tremotesf.torrentfile.rpc.RpcRequestState
import org.equeim.tremotesf.torrentfile.rpc.requests.addTorrentLink
import org.equeim.tremotesf.torrentfile.rpc.requests.serversettings.DownloadingServerSettings
import org.equeim.tremotesf.ui.utils.ArrayDropdownAdapter
import org.equeim.tremotesf.ui.utils.FormatUtils
import org.equeim.tremotesf.ui.utils.extendWhenImeIsHidden
import org.equeim.tremotesf.ui.utils.hideKeyboard
import org.equeim.tremotesf.ui.utils.launchAndCollectWhenStarted
import org.equeim.tremotesf.ui.utils.textInputLayout
import org.equeim.tremotesf.ui.utils.viewLifecycleObject
import timber.log.Timber


class AddTorrentLinkFragment : AddTorrentFragment(
    R.layout.add_torrent_link_fragment,
    R.string.add_torrent_link,
    0
) {
    private val args: AddTorrentLinkFragmentArgs by navArgs()
    private val model: AddTorrentLinkModel by viewModels {
        viewModelFactory {
            initializer {
                AddTorrentLinkModel(
                    args.uri,
                    checkNotNull(get(ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY))
                )
            }
        }
    }

    private val binding by viewLifecycleObject(AddTorrentLinkFragmentBinding::bind)
    private var directoriesAdapter: AddTorrentDirectoriesAdapter by viewLifecycleObject()
    private var freeSpaceJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Timber.i("onCreate: arguments = $arguments")
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.priorityView.setAdapter(ArrayDropdownAdapter(priorityItems))

        directoriesAdapter = AddTorrentDirectoriesAdapter(
            viewLifecycleOwner.lifecycleScope,
            savedInstanceState
        )
        binding.downloadDirectoryLayout.downloadDirectoryEdit.setAdapter(directoriesAdapter)

        binding.downloadDirectoryLayout.downloadDirectoryEdit.doAfterTextChanged { path ->
            freeSpaceJob?.cancel()
            freeSpaceJob = null
            if (!path.isNullOrBlank()) {
                freeSpaceJob = lifecycleScope.launch {
                    binding.downloadDirectoryLayout.downloadDirectoryLayout.helperText = model.getFreeSpace(path.toString())?.let {
                        getString(
                            R.string.free_space,
                            FormatUtils.formatFileSize(requireContext(), it)
                        )
                    }
                    freeSpaceJob = null
                }
            }
        }
    }

    override fun onViewStateRestored(savedInstanceState: Bundle?) {
        super.onViewStateRestored(savedInstanceState)
        if (model.shouldSetTorrentLink) {
            viewLifecycleOwner.lifecycleScope.launch {
                model.getInitialTorrentLink()?.let(binding.torrentLinkEdit::setText)
                model.shouldSetTorrentLink = false
            }
        }
        binding.addButton.apply {
            setOnClickListener { addTorrentLink() }
            extendWhenImeIsHidden(requiredActivity.windowInsets, viewLifecycleOwner)
        }
        handleDragEvents()

        model.downloadingSettings.launchAndCollectWhenStarted(viewLifecycleOwner) {
            when (it) {
                is RpcRequestState.Loaded -> showView(it.response)
                is RpcRequestState.Loading -> showPlaceholder(getString(R.string.loading), showProgressBar = true)
                is RpcRequestState.Error -> showPlaceholder(it.error.getErrorString(requireContext()), showProgressBar = false)
            }
        }
    }

    private fun handleDragEvents() {
        val listener = OnDragListener { view, event ->
            when (event.action) {
                DragEvent.ACTION_DRAG_STARTED -> {
                    Timber.d("Handling drag start event on $view")
                    model.acceptDragStartEvent(event.clipDescription)
                }
                DragEvent.ACTION_DROP -> {
                    Timber.d("Handling drop event on $view")
                    model.getTorrentLinkFromDropEvent(event.clipData)?.let {
                        binding.torrentLinkEdit.setText(it)
                        true
                    } ?: false
                }
                /**
                 * Don't enter [also] branch to avoid log spam
                 */
                else -> return@OnDragListener false
            }.also {
                if (it) {
                    Timber.d("Accepting event")
                } else {
                    Timber.d("Rejecting event")
                }
            }
        }
        binding.root.setOnDragListener(listener)
        binding.torrentLinkEdit.setOnDragListener(listener)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        directoriesAdapter.saveInstanceState(outState)
    }

    private fun addTorrentLink(): Unit = with(binding) {
        var error = false

        torrentLinkEdit.textInputLayout.error =
            if (torrentLinkEdit.text?.trimmedLength() == 0) {
                error = true
                getString(R.string.empty_field_error)
            } else {
                null
            }

        val downloadDirectoryEdit = downloadDirectoryLayout.downloadDirectoryEdit
        val downloadDirectoryLayout = downloadDirectoryLayout.downloadDirectoryLayout

        downloadDirectoryLayout.error =
            if (downloadDirectoryEdit.text?.trimmedLength() == 0) {
                error = true
                getString(R.string.empty_field_error)
            } else {
                null
            }

        if (error) {
            return
        }

        GlobalRpcClient.performBackgroundRpcRequest(R.string.add_torrent_error) {
            addTorrentLink(
                torrentLinkEdit.text?.toString().orEmpty(),
                downloadDirectoryEdit.text.toString(),
                priorityItemEnums[priorityItems.indexOf(priorityView.text.toString())],
                startDownloadingCheckBox.isChecked,
            )
        }

        directoriesAdapter.save(binding.downloadDirectoryLayout.downloadDirectoryEdit)

        requiredActivity.onBackPressedDispatcher.onBackPressed()
    }

    private fun showPlaceholder(text: String, showProgressBar: Boolean) {
        hideKeyboard()
        with (binding) {
            scrollView.isVisible = false
            with (placeholderView) {
                root.isVisible = true
                progressBar.isVisible = showProgressBar
                placeholder.text = text
            }
        }
    }

    private suspend fun showView(downloadingSettings: DownloadingServerSettings) = with(binding) {
        if (model.shouldSetInitialRpcInputs) {
            downloadDirectoryLayout.downloadDirectoryEdit.setText(model.getInitialDownloadDirectory(downloadingSettings))
            startDownloadingCheckBox.isChecked = downloadingSettings.startAddedTorrents
            model.shouldSetInitialRpcInputs = false
        }
        if (model.shouldSetInitialLocalInputs) {
            torrentLinkEdit.setText(model.getInitialTorrentLink())
            priorityView.setText(R.string.normal_priority)
            model.shouldSetInitialLocalInputs = false
        }
        scrollView.isVisible = true
        placeholderView.root.isVisible = false
    }
}
