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

package org.equeim.tremotesf.ui.torrentslistfragment

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.activity.addCallback
import androidx.appcompat.widget.SearchView
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.view.children
import androidx.core.view.isVisible
import androidx.navigation.navGraphViewModels
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.behavior.HideBottomViewOnScrollBehavior
import com.google.android.material.snackbar.Snackbar
import org.equeim.libtremotesf.RpcConnectionState
import org.equeim.tremotesf.R
import org.equeim.tremotesf.databinding.TorrentsListFragmentBinding
import org.equeim.tremotesf.rpc.GlobalRpc
import org.equeim.tremotesf.rpc.GlobalServers
import org.equeim.tremotesf.rpc.statusString
import org.equeim.tremotesf.torrentfile.rpc.Server
import org.equeim.tremotesf.torrentfile.rpc.ServerStats
import org.equeim.tremotesf.ui.NavigationFragment
import org.equeim.tremotesf.ui.Settings
import org.equeim.tremotesf.ui.TorrentFileRenameDialogFragment
import org.equeim.tremotesf.ui.utils.FormatUtils
import org.equeim.tremotesf.ui.utils.Utils
import org.equeim.tremotesf.ui.utils.collectWhenStarted
import org.equeim.tremotesf.ui.utils.handleAndReset
import org.equeim.tremotesf.ui.utils.popDialog
import org.equeim.tremotesf.ui.utils.showSnackbar
import org.equeim.tremotesf.ui.utils.viewBinding


class TorrentsListFragment : NavigationFragment(
    R.layout.torrents_list_fragment,
    0,
    R.menu.torrents_list_fragment_menu
) {
    private var bottomMenu: Menu? = null

    val binding by viewBinding(TorrentsListFragmentBinding::bind)
    private var torrentsAdapter: TorrentsAdapter? = null

    private val model by navGraphViewModels<TorrentsListFragmentViewModel>(R.id.torrents_list_fragment)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        TorrentFileRenameDialogFragment.setFragmentResultListenerForRpc(this)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupBottomBar()

        val torrentsAdapter = TorrentsAdapter(this)
        this.torrentsAdapter = torrentsAdapter

        binding.torrentsView.apply {
            adapter = torrentsAdapter
            layoutManager = LinearLayoutManager(requireContext())
            if (Settings.torrentCompactView) {
                addItemDecoration(DividerItemDecoration(requireContext(), DividerItemDecoration.VERTICAL))
            }
            (itemAnimator as DefaultItemAnimator).supportsChangeAnimations = false
        }

        model.torrents.collectWhenStarted(viewLifecycleOwner, torrentsAdapter::update)

        GlobalRpc.isConnected.collectWhenStarted(viewLifecycleOwner, ::onRpcConnectedChanged)

        model.placeholderUpdateData.collectWhenStarted(viewLifecycleOwner, ::updatePlaceholder)

        GlobalServers.currentServer.collectWhenStarted(viewLifecycleOwner, ::updateTitle)
        model.subtitleUpdateData.collectWhenStarted(viewLifecycleOwner, ::updateSubtitle)

        model.showAddTorrentDuplicateError.handleAndReset {
            view.showSnackbar(R.string.torrent_duplicate, Snackbar.LENGTH_LONG)
        }.collectWhenStarted(viewLifecycleOwner)

        model.showAddTorrentError.handleAndReset {
            view.showSnackbar(R.string.torrent_add_error, Snackbar.LENGTH_LONG)
        }.collectWhenStarted(viewLifecycleOwner)
    }

    override fun onDestroyView() {
        bottomMenu = null
        torrentsAdapter = null
        super.onDestroyView()
    }

    private fun setupBottomBar() {
        requireActivity().menuInflater.inflate(R.menu.torrents_list_fragment_bottom_menu, binding.bottomMenuView.menu)
        val bottomMenu: Menu = binding.bottomMenuView.menu
        this.bottomMenu = bottomMenu

        binding.searchView.apply {
            setOnQueryTextListener(object : SearchView.OnQueryTextListener {
                override fun onQueryTextChange(newText: String): Boolean {
                    model.nameFilter.value = newText.trim()
                    return true
                }

                override fun onQueryTextSubmit(query: String): Boolean {
                    return false
                }
            })

            setOnSearchClickListener {
                binding.bottomMenuView.isVisible = false
                binding.addTorrentButton.hide()
            }
            setOnCloseListener {
                binding.bottomMenuView.isVisible = true
                binding.addTorrentButton.show()
                false
            }
        }

        requiredActivity.onBackPressedDispatcher.addCallback(viewLifecycleOwner) {
            if (!binding.searchView.collapse()) {
                isEnabled = false
                requiredActivity.onBackPressedDispatcher.onBackPressed()
            }
        }

        binding.bottomMenuView.setOnMenuItemClickListener {
            when (it.itemId) {
                R.id.torrents_filters -> navigate(TorrentsListFragmentDirections.toTorrentsFiltersDialogFragment())
                R.id.transmission_settings -> navigate(TorrentsListFragmentDirections.toTransmissionSettingsDialogFragment())
                else -> return@setOnMenuItemClickListener false
            }
            true
        }

        binding.addTorrentButton.setOnClickListener {
            navigate(TorrentsListFragmentDirections.toAddTorrentMenuFragment())
        }
    }

    override fun onToolbarMenuItemClicked(menuItem: MenuItem): Boolean {
        when (menuItem.itemId) {
            R.id.settings -> navigate(TorrentsListFragmentDirections.toSettingsFragment())
            R.id.about -> navigate(TorrentsListFragmentDirections.toAboutFragment())
            R.id.quit -> Utils.shutdownApp(requireContext())
            else -> return false
        }
        return true
    }

    private fun onRpcConnectedChanged(connected: Boolean) {
        if (!connected) {
            requiredActivity.actionMode?.finish()
            binding.searchView.collapse()
            when (navController.currentDestination?.id) {
                R.id.donate_dialog, R.id.transmission_settings_dialog_fragment -> Unit
                else -> navController.popDialog()
            }
        }

        bottomMenu?.findItem(R.id.torrents_filters)?.isEnabled = connected
        binding.searchView.findViewById<View>(R.id.search_button).isEnabled = connected

        binding.bottomToolbar.apply {
            hideOnScroll = connected
            if (!connected) performShow()
        }

        binding.addTorrentButton.apply {
            isEnabled = connected
            if (!connected) {
                ((layoutParams as CoordinatorLayout.LayoutParams).behavior as HideBottomViewOnScrollBehavior).slideUp(this)
            }
        }
    }

    private fun updateTitle(currentServer: Server?) {
        toolbar?.title = if (currentServer != null) {
            getString(
                R.string.current_server_string,
                currentServer.name,
                currentServer.address
            )
        } else {
            getString(R.string.app_name)
        }
    }

    private fun updateSubtitle(subtitleData: Pair<ServerStats, Boolean>) {
        val (stats, isConnected) = subtitleData
        toolbar?.subtitle = if (isConnected) {
            getString(
                R.string.main_activity_subtitle,
                FormatUtils.formatByteSpeed(requireContext(), stats.downloadSpeed),
                FormatUtils.formatByteSpeed(requireContext(), stats.uploadSpeed)
            )
        } else {
            null
        }
    }

    private fun updatePlaceholder(data: TorrentsListFragmentViewModel.PlaceholderUpdateData) = with(binding) {
        val (status, hasTorrents, hasServers) = data

        progressBar.isVisible = status.connectionState == RpcConnectionState.Connecting && !hasTorrents
        placeholderText.apply {
            text = when {
                hasTorrents -> null
                (status.connectionState == RpcConnectionState.Connected) -> getString(R.string.no_torrents)
                else -> status.statusString
            }
            isVisible = !text.isNullOrEmpty()
        }
        actionButton.apply {
            text = when {
                !hasServers -> {
                    setOnClickListener { navigate(TorrentsListFragmentDirections.toServerEditFragment()) }
                    getText(R.string.add_server)
                }
                status.connectionState == RpcConnectionState.Disconnected -> {
                    setOnClickListener { GlobalRpc.nativeInstance.connect() }
                    getText(R.string.connect)
                }
                else -> {
                    setOnClickListener(null)
                    null
                }
            }
            isVisible = !text.isNullOrEmpty()
        }
        placeholderView.apply {
            isVisible = children.any { it.isVisible }
        }
    }
}

private fun SearchView.collapse(): Boolean {
    return if (!isIconified) {
        // Doing it once only clears text
        isIconified = true
        isIconified = true
        true
    } else {
        false
    }
}
