// SPDX-FileCopyrightText: 2017-2025 Alexey Rochev <equeim@gmail.com>
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
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import org.equeim.tremotesf.R
import org.equeim.tremotesf.databinding.AddTorrentLinkFragmentBinding
import org.equeim.tremotesf.rpc.RpcRequestError
import org.equeim.tremotesf.rpc.RpcRequestState
import org.equeim.tremotesf.rpc.requests.serversettings.DownloadingServerSettings
import org.equeim.tremotesf.ui.utils.ArrayDropdownAdapter
import org.equeim.tremotesf.ui.utils.FormatUtils
import org.equeim.tremotesf.ui.utils.extendWhenImeIsHidden
import org.equeim.tremotesf.ui.utils.hide
import org.equeim.tremotesf.ui.utils.hideKeyboard
import org.equeim.tremotesf.ui.utils.launchAndCollectWhenStarted
import org.equeim.tremotesf.ui.utils.showError
import org.equeim.tremotesf.ui.utils.showLoading
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
                    binding.downloadDirectoryLayout.downloadDirectoryLayout.helperText =
                        model.getFreeSpace(path.toString())?.let {
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

        binding.addButton.apply {
            setOnClickListener { addTorrentLink() }
            extendWhenImeIsHidden(requiredActivity.windowInsets, viewLifecycleOwner)
        }
        handleDragEvents()

        model.downloadingSettings.launchAndCollectWhenStarted(viewLifecycleOwner) {
            when (it) {
                is RpcRequestState.Loaded -> showView(it.response)
                is RpcRequestState.Loading -> showPlaceholder(null)
                is RpcRequestState.Error -> showPlaceholder(it.error)
            }
        }

        model.addTorrentState.launchAndCollectWhenStarted(viewLifecycleOwner) {
            updateAddTorrentState(it, currentCoroutineContext(), binding.addButton) { torrentName ->
                AddTorrentLinkFragmentDirections.toMergingTrackersDialogFragment(torrentName, cancelable = true)
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

        val torrentLink = torrentLinkEdit.text?.toString().orEmpty()
        torrentLinkEdit.textInputLayout.error =
            if (torrentLink.trimmedLength() == 0) {
                error = true
                getString(R.string.empty_field_error)
            } else {
                null
            }

        val downloadDirectory =
            downloadDirectoryLayout.downloadDirectoryEdit.text?.toString().orEmpty()
        val downloadDirectoryLayout = downloadDirectoryLayout.downloadDirectoryLayout

        downloadDirectoryLayout.error =
            if (downloadDirectory.trimmedLength() == 0) {
                error = true
                getString(R.string.empty_field_error)
            } else {
                null
            }

        if (error) {
            return
        }

        model.addTorrentLink(
            torrentLink,
            downloadDirectory,
            priorityItemEnums[priorityItems.indexOf(priorityView.text.toString())],
            startDownloadingCheckBox.isChecked
        )
    }

    private fun showPlaceholder(error: RpcRequestError?) {
        hideKeyboard()
        with(binding) {
            scrollView.isVisible = false
            error?.let(placeholderView::showError) ?: placeholderView.showLoading()
        }
    }

    private suspend fun showView(downloadingSettings: DownloadingServerSettings) = with(binding) {
        if (model.shouldSetInitialRpcInputs) {
            downloadDirectoryLayout.downloadDirectoryEdit.setText(
                model.getInitialDownloadDirectory(
                    downloadingSettings
                )
            )
            startDownloadingCheckBox.isChecked =
                model.getInitialStartAfterAdding(downloadingSettings)
            model.shouldSetInitialRpcInputs = false
        }
        if (model.shouldSetInitialLocalInputs) {
            val initialTorrentLink = model.getInitialTorrentLink()
            initialTorrentLink?.let(torrentLinkEdit::setText)
            priorityView.setText(priorityItems[priorityItemEnums.indexOf(model.getInitialPriority())])
            model.shouldSetInitialLocalInputs = false

            if (initialTorrentLink != null) {
                model.checkIfTorrentExistsForInitialLink(initialTorrentLink)
            }
        }
        scrollView.isVisible = true
        placeholderView.hide()
    }

    override fun navigateBack() {
        if (!model.shouldSetInitialRpcInputs) {
            directoriesAdapter.save(binding.downloadDirectoryLayout.downloadDirectoryEdit)
        }
        super.navigateBack()
    }

    override fun onMergeTrackersDialogResult(result: MergingTrackersDialogFragment.Result) {
        super.onMergeTrackersDialogResult(result)
        model.onMergeTrackersDialogResult(result)
    }
}
