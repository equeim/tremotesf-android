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
import android.widget.Checkable
import androidx.activity.addCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.widget.SearchView
import androidx.fragment.app.viewModels
import androidx.navigation.ui.onNavDestinationSelected
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.snackbar.Snackbar
import org.equeim.tremotesf.R
import org.equeim.tremotesf.data.rpc.RpcConnectionState
import org.equeim.tremotesf.data.rpc.Server
import org.equeim.tremotesf.data.rpc.ServerStats
import org.equeim.tremotesf.data.rpc.Torrent
import org.equeim.tremotesf.databinding.TorrentsListFragmentBinding
import org.equeim.tremotesf.rpc.GlobalRpc
import org.equeim.tremotesf.rpc.GlobalServers
import org.equeim.tremotesf.rpc.statusString
import org.equeim.tremotesf.ui.NavigationFragment
import org.equeim.tremotesf.ui.Settings
import org.equeim.tremotesf.ui.TorrentFileRenameDialogFragment
import org.equeim.tremotesf.ui.sidepanel.DirectoriesViewAdapter
import org.equeim.tremotesf.ui.sidepanel.StatusFilterViewAdapter
import org.equeim.tremotesf.ui.sidepanel.TrackersViewAdapter
import org.equeim.tremotesf.ui.utils.FormatUtils
import org.equeim.tremotesf.ui.utils.popDialog
import org.equeim.tremotesf.ui.utils.showSnackbar
import org.equeim.tremotesf.ui.utils.viewBinding
import org.equeim.tremotesf.ui.utils.collectWhenStarted
import org.equeim.tremotesf.ui.utils.handleAndReset


class TorrentsListFragment : NavigationFragment(
    R.layout.torrents_list_fragment,
    0,
    R.menu.torrents_list_fragment_menu
) {
    private var menu: Menu? = null
    private var searchMenuItem: MenuItem? = null

    val binding by viewBinding(TorrentsListFragmentBinding::bind)
    private var torrentsAdapter: TorrentsAdapter? = null

    private val model by viewModels<TorrentsListFragmentViewModel>()

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

        setupDrawerListeners()

        binding.torrentsView.apply {
            adapter = torrentsAdapter
            layoutManager = LinearLayoutManager(requireContext())
            addItemDecoration(
                DividerItemDecoration(
                    requireContext(),
                    DividerItemDecoration.VERTICAL
                )
            )
            (itemAnimator as DefaultItemAnimator).supportsChangeAnimations = false
        }

        model.torrents.collectWhenStarted(viewLifecycleOwner, torrentsAdapter::update)

        GlobalRpc.torrents.collectWhenStarted(viewLifecycleOwner, ::onTorrentsUpdated)

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

    private fun setupDrawerListeners() {
        with(requiredActivity.sidePanelBinding) {
            sortView.setOnItemClickListener { _, _, position, _ ->
                model.apply {
                    sortMode.value = TorrentsListFragmentViewModel.SortMode.values()[position]
                    if (GlobalRpc.isConnected.value) {
                        Settings.torrentsSortMode = sortMode.value
                    }
                }
            }

            sortViewLayout.setStartIconOnClickListener {
                model.apply {
                    sortOrder.value = sortOrder.value.inverted()
                    Settings.torrentsSortOrder = sortOrder.value
                }
                (it as Checkable).isChecked =
                    Settings.torrentsSortOrder == TorrentsListFragmentViewModel.SortOrder.Descending
            }

            statusView.setOnItemClickListener { _, _, position, _ ->
                model.apply {
                    statusFilterMode.value =
                        TorrentsListFragmentViewModel.StatusFilterMode.values()[position]
                    if (GlobalRpc.isConnected.value) {
                        Settings.torrentsStatusFilter = statusFilterMode.value
                    }
                }
            }

            trackersView.setOnItemClickListener { _, _, position, _ ->
                model.apply {
                    trackerFilter.value =
                        (trackersView.adapter as TrackersViewAdapter).getTrackerFilter(position)
                    if (GlobalRpc.isConnected.value) {
                        Settings.torrentsTrackerFilter = trackerFilter.value
                    }
                }
            }

            directoriesView.setOnItemClickListener { _, _, position, _ ->
                model.apply {
                    directoryFilter.value =
                        (directoriesView.adapter as DirectoriesViewAdapter).getDirectoryFilter(
                            position
                        )
                    if (GlobalRpc.isConnected.value) {
                        Settings.torrentsDirectoryFilter = directoryFilter.value
                    }
                }
            }
        }
    }

    private fun clearDrawerListeners() {
        with(requiredActivity.sidePanelBinding) {
            sortView.onItemClickListener = null
            sortViewLayout.setStartIconOnClickListener(null)
            statusView.onItemClickListener = null
            trackersView.onItemClickListener = null
            directoriesView.onItemSelectedListener = null
        }
    }

    override fun onDestroyView() {
        menu = null
        searchMenuItem = null

        clearDrawerListeners()

        torrentsAdapter = null

        super.onDestroyView()
    }

    private fun setupMenuItems() {
        val toolbar = this.toolbar ?: return

        val menu = toolbar.menu!!
        this.menu = menu

        val searchMenuItem = menu.findItem(R.id.search)
        this.searchMenuItem = searchMenuItem
        val searchView = searchMenuItem.actionView as SearchView
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextChange(newText: String): Boolean {
                model.nameFilter.value = newText.trim()
                return true
            }

            override fun onQueryTextSubmit(query: String): Boolean {
                return false
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

    private fun onRpcConnectionStateChanged(connectionState: Int) {
        if (connectionState == RpcConnectionState.Disconnected || connectionState == RpcConnectionState.Connected) {
            if (connectionState == RpcConnectionState.Connected) {
                menu?.findItem(R.id.alternative_speed_limits)?.isChecked =
                    GlobalRpc.serverSettings.alternativeSpeedLimitsEnabled
            } else {
                requiredActivity.actionMode?.finish()
                searchMenuItem?.collapseActionView()
                if (navController.currentDestination?.id != R.id.donate_dialog) {
                    navController.popDialog()
                }
            }
        }

        updateMenuItems(connectionState)
    }

    private fun onTorrentsUpdated(torrents: List<Torrent>) {
        with(requiredActivity.sidePanelBinding) {
            (statusView.adapter as StatusFilterViewAdapter).update(torrents)
            (trackersView.adapter as TrackersViewAdapter).update(torrents)
            (directoriesView.adapter as DirectoriesViewAdapter).update(torrents)
        }

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

    private fun updateMenuItems(connectionState: Int) {
        val menu = this.menu ?: return

        val connectMenuItem = menu.findItem(R.id.connect)
        connectMenuItem.isEnabled = GlobalServers.hasServers
        connectMenuItem.title = when (connectionState) {
            RpcConnectionState.Disconnected -> getString(R.string.connect)
            RpcConnectionState.Connecting,
            RpcConnectionState.Connected -> getString(R.string.disconnect)
            else -> ""
        }

        val connected = (connectionState == RpcConnectionState.Connected)
        searchMenuItem?.isVisible = connected
        menu.findItem(R.id.add_torrent_file).isEnabled = connected
        menu.findItem(R.id.to_add_torrent_link_fragment).isEnabled = connected
        menu.findItem(R.id.to_server_settings_fragment).isEnabled = connected
        menu.findItem(R.id.alternative_speed_limits).isEnabled = connected
        menu.findItem(R.id.to_server_stats_dialog).isEnabled = connected
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
        } catch (error: ActivityNotFoundException) {
            navigate(TorrentsListFragmentDirections.toFilePickerFragment())
        }
    }
}
