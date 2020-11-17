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

import android.app.Activity
import android.app.Dialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Checkable

import androidx.activity.addCallback
import androidx.appcompat.widget.SearchView
import androidx.core.os.bundleOf
import androidx.fragment.app.viewModels
import androidx.navigation.ui.onNavDestinationSelected
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar

import org.equeim.tremotesf.BuildConfig
import org.equeim.tremotesf.R
import org.equeim.tremotesf.databinding.TorrentsListFragmentBinding
import org.equeim.tremotesf.rpc.Rpc
import org.equeim.tremotesf.rpc.RpcStatus
import org.equeim.tremotesf.rpc.Server
import org.equeim.tremotesf.rpc.ServerStats
import org.equeim.tremotesf.rpc.Servers
import org.equeim.tremotesf.rpc.Torrent
import org.equeim.tremotesf.ui.AboutFragment
import org.equeim.tremotesf.ui.NavigationDialogFragment
import org.equeim.tremotesf.ui.NavigationFragment
import org.equeim.tremotesf.ui.Settings
import org.equeim.tremotesf.ui.TorrentFileRenameDialogFragment
import org.equeim.tremotesf.ui.addtorrent.AddTorrentFragment
import org.equeim.tremotesf.ui.sidepanel.DirectoriesViewAdapter
import org.equeim.tremotesf.ui.sidepanel.StatusFilterViewAdapter
import org.equeim.tremotesf.ui.sidepanel.TrackersViewAdapter
import org.equeim.tremotesf.ui.utils.Utils
import org.equeim.tremotesf.ui.utils.popDialog
import org.equeim.tremotesf.ui.utils.showSnackbar
import org.equeim.tremotesf.ui.utils.viewBinding
import org.equeim.tremotesf.utils.Logger
import org.equeim.tremotesf.utils.collectWhenStarted

import java.util.concurrent.TimeUnit


class TorrentsListFragment : NavigationFragment(R.layout.torrents_list_fragment,
                                                0,
                                                R.menu.torrents_list_fragment_menu), TorrentFileRenameDialogFragment.PrimaryFragment, Logger {
    private var menu: Menu? = null
    private var searchMenuItem: MenuItem? = null

    val binding by viewBinding(TorrentsListFragmentBinding::bind)
    private var torrentsAdapter: TorrentsAdapter? = null

    private val model by viewModels<TorrentsListFragmentViewModel>()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupMenuItems()

        val torrentsAdapter = TorrentsAdapter(this, savedInstanceState)
        this.torrentsAdapter = torrentsAdapter

        setupDrawerListeners()

        binding.torrentsView.apply {
            adapter = torrentsAdapter
            layoutManager = LinearLayoutManager(requireContext())
            addItemDecoration(DividerItemDecoration(requireContext(), DividerItemDecoration.VERTICAL))
            (itemAnimator as DefaultItemAnimator).supportsChangeAnimations = false
        }

        model.torrents.collectWhenStarted(viewLifecycleOwner, torrentsAdapter::update)

        Rpc.torrents.collectWhenStarted(viewLifecycleOwner, ::onTorrentsUpdated)

        Rpc.status.collectWhenStarted(viewLifecycleOwner, ::onRpcStatusChanged)
        Rpc.statusString.collectWhenStarted(viewLifecycleOwner) {
            updatePlaceholder(Rpc.status.value, it)
        }

        torrentsAdapter.registerAdapterDataObserver(object : RecyclerView.AdapterDataObserver() {
            override fun onItemRangeRemoved(positionStart: Int, itemCount: Int) {
                // Adapter is now empty
                if (itemCount > 0 && torrentsAdapter.itemCount == 0) {
                    updatePlaceholder()
                }
            }

            override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
                // Adapter was empty
                if (itemCount > 0 && itemCount == torrentsAdapter.itemCount) {
                    updatePlaceholder()
                }
            }
        })

        Servers.currentServer.collectWhenStarted(viewLifecycleOwner, ::updateTitle)
        model.subtitleUpdateData.collectWhenStarted(viewLifecycleOwner, ::updateSubtitle)

        Rpc.torrentAddDuplicateEvents.collectWhenStarted(viewLifecycleOwner) {
            view.showSnackbar(R.string.torrent_duplicate, Snackbar.LENGTH_LONG)
        }
        Rpc.torrentAddErrorEvents.collectWhenStarted(viewLifecycleOwner) {
            view.showSnackbar(R.string.torrent_add_error, Snackbar.LENGTH_LONG)
        }

        if (!model.navigatedFromFragment.value) {
            if (Servers.hasServers) {
                if (model.shouldShowDonateDialog()) {
                    Settings.donateDialogShown = true
                    navigate(R.id.action_torrentsListFragment_to_donateDialogFragment)
                }
            } else {
                navigate(R.id.action_torrentsListFragment_to_serverEditFragment)
            }
        }
    }

    private fun setupDrawerListeners() {
        with(requiredActivity.sidePanelBinding) {
            sortView.setOnItemClickListener { _, _, position, _ ->
                model.apply {
                    sortMode.value = TorrentsListFragmentViewModel.SortMode.values()[position]
                    if (Rpc.isConnected.value) {
                        Settings.torrentsSortMode = sortMode.value
                    }
                }
            }

            sortViewLayout.setStartIconOnClickListener {
                model.apply {
                    sortOrder.value = sortOrder.value.inverted()
                    Settings.torrentsSortOrder = sortOrder.value
                }
                (it as Checkable).isChecked = Settings.torrentsSortOrder == TorrentsListFragmentViewModel.SortOrder.Descending
            }

            statusView.setOnItemClickListener { _, _, position, _ ->
                model.apply {
                    statusFilterMode.value = TorrentsListFragmentViewModel.StatusFilterMode.values()[position]
                    if (Rpc.isConnected.value) {
                        Settings.torrentsStatusFilter = statusFilterMode.value
                    }
                }
            }

            trackersView.setOnItemClickListener { _, _, position, _ ->
                model.apply {
                    trackerFilter.value = (trackersView.adapter as TrackersViewAdapter).getTrackerFilter(position)
                    if (Rpc.isConnected.value) {
                        Settings.torrentsTrackerFilter = trackerFilter.value
                    }
                }
            }

            directoriesView.setOnItemClickListener { _, _, position, _ ->
                model.apply {
                    directoryFilter.value = (directoriesView.adapter as DirectoriesViewAdapter).getDirectoryFilter(position)
                    if (Rpc.isConnected.value) {
                        Settings.torrentsDirectoryFilter = directoryFilter.value
                    }
                }
            }
        }
    }

    private fun clearDrawerListeners() {
        with (requiredActivity.sidePanelBinding) {
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

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        torrentsAdapter?.saveInstanceState(outState)
    }

    override fun onNavigatedFrom() {
        model.navigatedFromFragment.value = true
    }

    override fun onRenameFile(torrentId: Int, filePath: String, newName: String) {
        Rpc.nativeInstance.renameTorrentFile(torrentId, filePath, newName)
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

        requiredActivity.onBackPressedDispatcher.addCallback(this) {
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
                if (Rpc.status.value == RpcStatus.Disconnected) {
                    Rpc.nativeInstance.connect()
                } else {
                    Rpc.nativeInstance.disconnect()
                }
            }
            R.id.add_torrent_file -> startFilePickerActivity()
            R.id.alternative_speed_limits -> {
                menuItem.isChecked = !menuItem.isChecked
                Rpc.serverSettings.alternativeSpeedLimitsEnabled = menuItem.isChecked
            }
            else -> return menuItem.onNavDestinationSelected(navController)
        }
        return true
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        info("onActivityResult $resultCode $data")
        if (resultCode == Activity.RESULT_OK && data != null) {
            navigate(R.id.action_torrentsListFragment_to_addTorrentFileFragment, bundleOf(AddTorrentFragment.URI to data.data!!.toString()))
        }
    }

    private fun onRpcStatusChanged(rpcStatus: Int) {
        if (rpcStatus == RpcStatus.Disconnected || rpcStatus == RpcStatus.Connected) {
            if (rpcStatus == RpcStatus.Connected) {
                menu?.findItem(R.id.alternative_speed_limits)?.isChecked = Rpc.serverSettings.alternativeSpeedLimitsEnabled
            } else {
                requiredActivity.actionMode?.finish()
                searchMenuItem?.collapseActionView()
                if (navController.currentDestination?.id != R.id.donateDialogFragment) {
                    navController.popDialog()
                }
            }
        }

        updateMenuItems(rpcStatus)
    }

    private fun onTorrentsUpdated(torrents: List<Torrent>) {
        with (requiredActivity.sidePanelBinding) {
            (statusView.adapter as StatusFilterViewAdapter).update(torrents)
            (trackersView.adapter as TrackersViewAdapter).update(torrents)
            (directoriesView.adapter as DirectoriesViewAdapter).update(torrents)
        }

        menu?.findItem(R.id.alternative_speed_limits)?.isChecked =
                if (Rpc.isConnected.value) Rpc.serverSettings.alternativeSpeedLimitsEnabled else false
    }

    private fun updateTitle(currentServer: Server?) {
        toolbar?.title = if (currentServer != null) {
            getString(R.string.current_server_string,
                      currentServer.name,
                      currentServer.address)
        } else {
            getString(R.string.app_name)
        }
    }

    private fun updateSubtitle(subtitleData: Pair<ServerStats, Boolean>) {
        val (stats, isConnected) = subtitleData
        toolbar?.subtitle = if (isConnected) {
            getString(R.string.main_activity_subtitle,
                      Utils.formatByteSpeed(requireContext(), stats.downloadSpeed),
                      Utils.formatByteSpeed(requireContext(), stats.uploadSpeed))
        } else {
            null
        }
    }

    private fun updateMenuItems(rpcStatus: Int) {
        val menu = this.menu ?: return

        val connectMenuItem = menu.findItem(R.id.connect)
        connectMenuItem.isEnabled = Servers.hasServers
        connectMenuItem.title = when (rpcStatus) {
            RpcStatus.Disconnected -> getString(R.string.connect)
            RpcStatus.Connecting,
            RpcStatus.Connected -> getString(R.string.disconnect)
            else -> ""
        }

        val connected = (rpcStatus == RpcStatus.Connected)
        searchMenuItem?.isVisible = connected
        menu.findItem(R.id.add_torrent_file).isEnabled = connected
        menu.findItem(R.id.action_torrentsListFragment_to_addTorrentLinkFragment).isEnabled = connected
        menu.findItem(R.id.action_torrentsListFragment_to_serverSettingsFragment).isEnabled = connected
        menu.findItem(R.id.alternative_speed_limits).isEnabled = connected
        menu.findItem(R.id.action_torrentsListFragment_to_serverStatsDialogFragment).isEnabled = connected
    }

    private fun updatePlaceholder() {
        updatePlaceholder(Rpc.status.value, Rpc.statusString.value)
    }

    private fun updatePlaceholder(status: Int, statusString: String) {
        binding.placeholder.text = when {
            torrentsAdapter?.itemCount ?: 0 != 0 -> null
            (status == RpcStatus.Connected) -> getString(R.string.no_torrents)
            else -> statusString
        }

        binding.progressBar.visibility = if (status == RpcStatus.Connecting && torrentsAdapter?.itemCount ?: 0 == 0) {
            View.VISIBLE
        } else {
            View.GONE
        }
    }

    private fun startFilePickerActivity() {
        try {
            startActivityForResult(Intent(Intent.ACTION_GET_CONTENT)
                                           .addCategory(Intent.CATEGORY_OPENABLE)
                                           .setType("application/x-bittorrent"),
                                   0)
        } catch (error: ActivityNotFoundException) {
            navigate(R.id.action_torrentsListFragment_to_filePickerFragment)
        }
    }

    class DonateDialogFragment : NavigationDialogFragment() {
        override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
            return MaterialAlertDialogBuilder(requireContext())
                    .setMessage(getString(R.string.donations_description) + "\n\n" + getString(R.string.donate_dialog_again))
                    .setNegativeButton(android.R.string.cancel, null)
                    .setPositiveButton(R.string.donations_donate) { _, _ ->
                        navigate(R.id.action_donateDialogFragment_to_aboutFragment, bundleOf(AboutFragment.DONATE to true))
                    }
                    .create()
        }
    }
}
