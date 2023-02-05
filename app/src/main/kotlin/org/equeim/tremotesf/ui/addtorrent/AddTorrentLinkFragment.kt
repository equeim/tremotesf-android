// SPDX-FileCopyrightText: 2017-2022 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package org.equeim.tremotesf.ui.addtorrent

import android.os.Bundle
import android.view.DragEvent
import android.view.View
import android.view.View.OnDragListener
import androidx.core.text.trimmedLength
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.fragment.navArgs
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch
import org.equeim.libtremotesf.RpcConnectionState
import org.equeim.tremotesf.R
import org.equeim.tremotesf.databinding.AddTorrentLinkFragmentBinding
import org.equeim.tremotesf.rpc.GlobalRpc
import org.equeim.tremotesf.rpc.statusString
import org.equeim.tremotesf.torrentfile.rpc.Rpc
import org.equeim.tremotesf.ui.utils.*
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
    private var connectSnackbar: Snackbar? by viewLifecycleObjectNullable()

    private var setInitialTorrentLink = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Timber.i("onCreate: arguments = $arguments")
        if (savedInstanceState == null) {
            setInitialTorrentLink = true
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.torrentLinkEdit.text = null
        with(binding) {
            priorityView.setAdapter(ArrayDropdownAdapter(priorityItems))
            priorityView.setText(R.string.normal_priority)
            startDownloadingCheckBox.isChecked = GlobalRpc.serverSettings.startAddedTorrents
        }
        directoriesAdapter = AddTorrentFileFragment.setupDownloadDirectoryEdit(
            binding.downloadDirectoryLayout,
            this,
            savedInstanceState
        )
    }

    override fun onViewStateRestored(savedInstanceState: Bundle?) {
        super.onViewStateRestored(savedInstanceState)
        if (setInitialTorrentLink) {
            lifecycleScope.launch {
                model.getInitialTorrentLink()?.let(binding.torrentLinkEdit::setText)
            }
        }
        binding.addButton.apply {
            setOnClickListener { addTorrentLink() }
            extendWhenImeIsHidden(requiredActivity.windowInsets, viewLifecycleOwner)
        }
        handleDragEvents()
        GlobalRpc.status.launchAndCollectWhenStarted(viewLifecycleOwner, ::updateView)
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

        GlobalRpc.nativeInstance.addTorrentLink(
            torrentLinkEdit.text?.toString() ?: "",
            downloadDirectoryEdit.text.toString(),
            priorityItemEnums[priorityItems.indexOf(priorityView.text.toString())],
            startDownloadingCheckBox.isChecked
        )

        directoriesAdapter.save()

        requiredActivity.onBackPressedDispatcher.onBackPressed()
    }

    private fun updateView(status: Rpc.Status) {
        with(binding) {
            when (status.connectionState) {
                RpcConnectionState.Disconnected -> {
                    placeholder.text = status.statusString
                    hideKeyboard()
                    if (connectSnackbar == null) {
                        connectSnackbar = coordinatorLayout.showSnackbar(
                            message = "",
                            duration = Snackbar.LENGTH_INDEFINITE,
                            actionText = R.string.connect,
                            action = GlobalRpc.nativeInstance::connect,
                            onDismissed = {
                                if (connectSnackbar == it) {
                                    connectSnackbar = null
                                }
                            }
                        )
                    }
                }
                RpcConnectionState.Connecting -> {
                    connectSnackbar?.dismiss()
                    connectSnackbar = null
                    placeholder.text = getString(R.string.connecting)
                }
                RpcConnectionState.Connected -> {
                    connectSnackbar?.dismiss()
                    connectSnackbar = null
                }
            }

            if (status.isConnected) {
                scrollView.visibility = View.VISIBLE
                placeholderLayout.visibility = View.GONE
                addButton.show()
            } else {
                placeholderLayout.visibility = View.VISIBLE
                scrollView.visibility = View.GONE
                addButton.hide()
            }

            progressBar.visibility = if (status.connectionState == RpcConnectionState.Connecting) {
                View.VISIBLE
            } else {
                View.GONE
            }
        }
    }
}
