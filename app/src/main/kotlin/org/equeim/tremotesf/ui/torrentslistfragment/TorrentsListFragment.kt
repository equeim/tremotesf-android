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

import android.content.ActivityNotFoundException
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.activity.addCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.widget.SearchView
import androidx.navigation.navGraphViewModels
import androidx.navigation.ui.onNavDestinationSelected
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.snackbar.Snackbar
import org.equeim.libtremotesf.RpcConnectionState
import org.equeim.tremotesf.R
import org.equeim.tremotesf.torrentfile.rpc.Server
import org.equeim.tremotesf.torrentfile.rpc.ServerStats
import org.equeim.tremotesf.databinding.TorrentsListFragmentBinding
import org.equeim.tremotesf.rpc.GlobalRpc
import org.equeim.tremotesf.rpc.GlobalServers
import org.equeim.tremotesf.rpc.statusString
import org.equeim.tremotesf.ui.NavigationFragment
import org.equeim.tremotesf.ui.TorrentFileRenameDialogFragment
import org.equeim.tremotesf.ui.utils.BottomPaddingDecoration
import org.equeim.tremotesf.ui.utils.FormatUtils
import org.equeim.tremotesf.ui.utils.VerticalDividerItemDecoration
import org.equeim.tremotesf.ui.utils.collectWhenStarted
import org.equeim.tremotesf.ui.utils.handleAndReset
import org.equeim.tremotesf.ui.utils.popDialog
import org.equeim.tremotesf.ui.utils.showSnackbar
import org.equeim.tremotesf.ui.utils.viewBinding
import timber.log.Timber


class TorrentsListFragment : NavigationFragment(
    R.layout.torrents_list_fragment,
    0,
    R.menu.torrents_list_fragment_menu
) {
    private var menu: Menu? = null
    private var bottomMenu: Menu? = null

    val binding by viewBinding(TorrentsListFragmentBinding::bind)
    private var torrentsAdapter: TorrentsAdapter? = null

    private val model by navGraphViewModels<TorrentsListFragmentViewModel>(R.id.torrents_list_fragment)

    private lateinit var getContentActivityLauncher: ActivityResultLauncher<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        getContentActivityLauncher =
            registerForActivityResult(ActivityResultContracts.GetContent()) {
                if (it != null) {
                    navigate(TorrentsListFragmentDirections.toAddTorrentFileFragment(it))
                }
            }

        TorrentFileRenameDialogFragment.setFragmentResultListenerForRpc(this)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupMenuItems()

        val torrentsAdapter = TorrentsAdapter(this)
        this.torrentsAdapter = torrentsAdapter

        binding.torrentsView.apply {
            adapter = torrentsAdapter
            layoutManager = LinearLayoutManager(requireContext())
            addItemDecoration(VerticalDividerItemDecoration(requireContext()))
            addItemDecoration(BottomPaddingDecoration(this, R.dimen.mtrl_bottomappbar_height))
            (itemAnimator as DefaultItemAnimator).supportsChangeAnimations = false
        }

        model.torrents.collectWhenStarted(viewLifecycleOwner, torrentsAdapter::update)

        GlobalRpc.torrents.collectWhenStarted(viewLifecycleOwner) { onTorrentsUpdated() }

        GlobalRpc.connectionState.collectWhenStarted(viewLifecycleOwner, ::onRpcConnectionStateChanged)

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
        menu = null
        bottomMenu = null

        torrentsAdapter = null

        super.onDestroyView()
    }

    private fun setupMenuItems() {
        val toolbar = this.toolbar ?: return

        menu = toolbar.menu
        val bottomMenu: Menu = binding.bottomToolbar.menu
        this.bottomMenu = bottomMenu

        val searchMenuItem = checkNotNull(bottomMenu.findItem(R.id.search))

        (searchMenuItem.actionView as SearchView).setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextChange(newText: String): Boolean {
                model.nameFilter.value = newText.trim()
                return true
            }

            override fun onQueryTextSubmit(query: String): Boolean {
                return false
            }
        })

        searchMenuItem.setOnActionExpandListener(object : MenuItem.OnActionExpandListener {
            override fun onMenuItemActionExpand(item: MenuItem): Boolean {
                bottomMenu.setGroupVisible(R.id.bottom_menu_hideable_items, false)
                return true
            }

            override fun onMenuItemActionCollapse(item: MenuItem): Boolean {
                bottomMenu.setGroupVisible(R.id.bottom_menu_hideable_items, true)
                return true
            }
        })

        requiredActivity.onBackPressedDispatcher.addCallback(viewLifecycleOwner) {
            if (searchMenuItem.isActionViewExpanded) {
                searchMenuItem.collapseActionView()
            } else {
                isEnabled = false
                requiredActivity.onBackPressedDispatcher.onBackPressed()
            }
        }

        binding.bottomToolbar.setOnMenuItemClickListener {
            when (it.itemId) {
                R.id.torrents_filters -> navigate(TorrentsListFragmentDirections.toTorrentsFiltersDialogFragment())
                else -> return@setOnMenuItemClickListener false
            }
            true
        }
    }

    override fun onToolbarMenuItemClicked(menuItem: MenuItem): Boolean {
        when (menuItem.itemId) {
            R.id.connect -> {
                if (GlobalRpc.connectionState.value == RpcConnectionState.Disconnected) {
                    GlobalRpc.nativeInstance.connect()
                } else {
                    GlobalRpc.nativeInstance.disconnect()
                }
            }
            R.id.add_torrent_file -> startFilePickerActivity()
            R.id.alternative_speed_limits -> {
                menuItem.isChecked = !menuItem.isChecked
                GlobalRpc.serverSettings.alternativeSpeedLimitsEnabled = menuItem.isChecked
            }
            else -> return menuItem.onNavDestinationSelected(navController)
        }
        return true
    }

    private fun onRpcConnectionStateChanged(connectionState: RpcConnectionState) {
        if (connectionState == RpcConnectionState.Disconnected || connectionState == RpcConnectionState.Connected) {
            if (connectionState == RpcConnectionState.Connected) {
                menu?.findItem(R.id.alternative_speed_limits)?.isChecked =
                    GlobalRpc.serverSettings.alternativeSpeedLimitsEnabled
            } else {
                requiredActivity.actionMode?.finish()
                bottomMenu?.findItem(R.id.search)?.collapseActionView()
                if (navController.currentDestination?.id != R.id.donate_dialog) {
                    navController.popDialog()
                }
            }
        }

        updateMenuItems(connectionState)
    }

    private fun onTorrentsUpdated() {
        menu?.findItem(R.id.alternative_speed_limits)?.isChecked =
            if (GlobalRpc.isConnected.value) GlobalRpc.serverSettings.alternativeSpeedLimitsEnabled else false
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

    private fun updateMenuItems(connectionState: RpcConnectionState) {
        val menu = this.menu ?: return
        val bottomMenu = this.bottomMenu ?: return

        menu.findItem(R.id.connect)?.apply {
            isEnabled = GlobalServers.hasServers
            title = when (connectionState) {
                RpcConnectionState.Disconnected -> getString(R.string.connect)
                RpcConnectionState.Connecting,
                RpcConnectionState.Connected -> getString(R.string.disconnect)
                else -> ""
            }
        }

        val connected = (connectionState == RpcConnectionState.Connected)

        listOf(
            R.id.add_torrent_file,
            R.id.to_add_torrent_link_fragment,
            R.id.to_server_settings_fragment,
            R.id.alternative_speed_limits,
            R.id.to_server_stats_dialog
        ).forEach { menu.findItem(it)?.isEnabled = connected }

        listOf(
            R.id.search,
            R.id.torrents_filters
        ).forEach { bottomMenu.findItem(it).isEnabled = connected }
    }

    private fun updatePlaceholder(data: TorrentsListFragmentViewModel.PlaceholderUpdateData) {
        val (status, hasTorrents) = data
        binding.placeholder.text = when {
            hasTorrents -> null
            (status.connectionState == RpcConnectionState.Connected) -> getString(R.string.no_torrents)
            else -> status.statusString
        }

        binding.progressBar.visibility =
            if (status.connectionState == RpcConnectionState.Connecting && !hasTorrents) {
                View.VISIBLE
            } else {
                View.GONE
            }
    }

    private fun startFilePickerActivity() {
        try {
            getContentActivityLauncher.launch("application/x-bittorrent")
        } catch (e: ActivityNotFoundException) {
            Timber.e(e, "Failed to start activity")
        }
    }
}
