/*
 * Copyright (C) 2017-2019 Alexey Rochev <equeim@gmail.com>
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

package org.equeim.tremotesf.torrentslistfragment

import java.util.concurrent.TimeUnit

import android.app.Activity
import android.app.Dialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.AutoCompleteTextView
import android.widget.Checkable
import android.widget.ImageView

import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.core.os.bundleOf
import androidx.core.view.GravityCompat
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.observe
import androidx.navigation.findNavController
import androidx.navigation.fragment.findNavController
import androidx.navigation.ui.onNavDestinationSelected
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

import com.google.android.material.dialog.MaterialAlertDialogBuilder

import org.jetbrains.anko.AnkoLogger
import org.jetbrains.anko.childrenRecursiveSequence
import org.jetbrains.anko.info
import org.jetbrains.anko.design.longSnackbar

import org.equeim.libtremotesf.ServerStats
import org.equeim.tremotesf.AboutFragment
import org.equeim.tremotesf.AddTorrentFragment
import org.equeim.tremotesf.BuildConfig
import org.equeim.tremotesf.NavigationFragment
import org.equeim.tremotesf.R
import org.equeim.tremotesf.Rpc
import org.equeim.tremotesf.RpcStatus
import org.equeim.tremotesf.Servers
import org.equeim.tremotesf.Settings
import org.equeim.tremotesf.utils.ArrayDropdownAdapter
import org.equeim.tremotesf.utils.BasicMediatorLiveData
import org.equeim.tremotesf.utils.Utils
import org.equeim.tremotesf.utils.popDialog
import org.equeim.tremotesf.utils.setChildrenEnabled

import kotlinx.android.synthetic.main.navigation_activity.*
import kotlinx.android.synthetic.main.torrents_list_fragment.*
import kotlinx.android.synthetic.main.side_panel_header.view.*


class TorrentsListFragment : NavigationFragment(R.layout.torrents_list_fragment,
                                                0,
                                                R.menu.main_activity_menu), AnkoLogger {
    private companion object {
        const val NAVIGATED_FROM_KEY = "org.equeim.tremotesf.TorrentsListFragment.navigatedFrom"
    }

    private var menu: Menu? = null
    private var searchMenuItem: MenuItem? = null

    private var serversViewAdapter: ServersViewAdapter? = null

    private var listSettingsLayout: ViewGroup? = null
    private var sortView: AutoCompleteTextView? = null
    private var statusView: AutoCompleteTextView? = null
    private var statusViewAdapter: StatusFilterViewAdapter? = null

    private var trackersView: AutoCompleteTextView? = null
    private var trackersViewAdapter: TrackersViewAdapter? = null

    private var directoriesView: AutoCompleteTextView? = null
    private var directoriesViewAdapter: DirectoriesViewAdapter? = null

    var torrentsAdapter: TorrentsAdapter? = null
        private set

    private var navigatedFrom = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        navigatedFrom = savedInstanceState?.getBoolean(NAVIGATED_FROM_KEY) ?: false
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupMenuItems()

        val torrentsAdapter = TorrentsAdapter(requireActivity() as AppCompatActivity, this)
        this.torrentsAdapter = torrentsAdapter

        setupDrawer()

        torrents_view.adapter = torrentsAdapter
        torrents_view.layoutManager = LinearLayoutManager(requireContext())
        torrents_view.addItemDecoration(DividerItemDecoration(requireContext(), DividerItemDecoration.VERTICAL))
        (torrents_view.itemAnimator as DefaultItemAnimator).supportsChangeAnimations = false

        Rpc.torrents.observe(viewLifecycleOwner) { onTorrentsUpdated() }
        Rpc.status.observe(viewLifecycleOwner, ::onRpcStatusChanged)
        BasicMediatorLiveData<Nothing>(Rpc.status, Rpc.error)
                .observe(viewLifecycleOwner) { updatePlaceholder() }

        torrentsAdapter.registerAdapterDataObserver(object : RecyclerView.AdapterDataObserver() {
            override fun onItemRangeRemoved(positionStart: Int, itemCount: Int) {
                if (torrentsAdapter.itemCount == 0) {
                    updatePlaceholder()
                }
            }

            override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
                if (itemCount == torrentsAdapter.itemCount) {
                    updatePlaceholder()
                }
            }
        })

        Servers.currentServer.observe(viewLifecycleOwner) { updateTitle() }
        BasicMediatorLiveData<Nothing>(Rpc.status, Rpc.serverStats)
                .observe(viewLifecycleOwner) { updateSubtitle(Rpc.serverStats.value) }

        Rpc.torrentAddDuplicateEvent.observe(viewLifecycleOwner) {
            view.longSnackbar(R.string.torrent_duplicate)
        }
        Rpc.torrentAddErrorEvent.observe(viewLifecycleOwner) {
            view.longSnackbar(R.string.torrent_add_error)
        }


        if (savedInstanceState == null) {
            if (Servers.hasServers) {
                if (!Settings.donateDialogShown) {
                    val info = requireActivity().packageManager.getPackageInfo(BuildConfig.APPLICATION_ID, 0)
                    val currentTime = System.currentTimeMillis()
                    val installDays = TimeUnit.DAYS.convert(currentTime - info.firstInstallTime, TimeUnit.MILLISECONDS)
                    val updateDays = TimeUnit.DAYS.convert(currentTime - info.lastUpdateTime, TimeUnit.MILLISECONDS)
                    if (installDays >= 2 && updateDays >= 1) {
                        Settings.donateDialogShown = true
                        navController.navigate(R.id.action_torrentsListFragment_to_donateDialogFragment)
                    }
                }
            } else if (!navigatedFrom) {
                navController.navigate(R.id.action_torrentsListFragment_to_serverEditFragment)
            }
        }
    }

    private fun setupDrawer() {
        val activity = requiredActivity

        val sidePanelHeader = activity.side_panel.getHeaderView(0)

        val serversView = sidePanelHeader.servers_view
        serversView.isEnabled = Servers.hasServers

        val listSettingsLayout = sidePanelHeader.list_settings_layout
        this.listSettingsLayout = listSettingsLayout
        listSettingsLayout.setChildrenEnabled(Rpc.isConnected)

        val sortView = sidePanelHeader.sort_view
        this.sortView = sortView

        val statusView = sidePanelHeader.status_view
        this.statusView = statusView

        val trackersView = sidePanelHeader.trackers_view
        this.trackersView = trackersView

        val directoriesView = sidePanelHeader.directories_view
        this.directoriesView = directoriesView

        activity.apply {
            if (!drawerSetUp) {
                drawer_layout.setDrawerShadow(R.drawable.drawer_shadow, GravityCompat.START)

                side_panel.setNavigationItemSelectedListener { menuItem ->
                    return@setNavigationItemSelectedListener when (menuItem.itemId) {
                        R.id.quit -> {
                            Utils.shutdownApp(this)
                            true
                        }
                        else -> menuItem.onNavDestinationSelected(findNavController(R.id.nav_host))
                    }
                }

                val serversViewAdapter = ServersViewAdapter(serversView)
                serversView.setAdapter(serversViewAdapter)
                serversView.setOnItemClickListener { _, _, position, _ ->
                    serversViewAdapter.servers[position].let {
                        if (it != Servers.currentServer.value) {
                            Servers.currentServer.value = it
                        }
                    }
                }

                sidePanelHeader.connection_settings_item.setOnClickListener {
                    try {
                        findNavController(R.id.nav_host).navigate(R.id.action_torrentsListFragment_to_connectionSettingsFragment)
                    } catch (ignore: IllegalArgumentException) {}
                }

                sortView.setAdapter(ArrayDropdownAdapter(resources.getStringArray(R.array.sort_spinner_items)))

                statusView.setAdapter(StatusFilterViewAdapter(requireContext(), statusView))

                trackersView.setAdapter(TrackersViewAdapter(requireContext(), trackersView))
                directoriesView.setAdapter(DirectoriesViewAdapter(requireContext(), directoriesView))

                drawerSetUp = true
            }
        }

        serversViewAdapter = serversView.adapter as ServersViewAdapter
        serversViewAdapter?.update()

        sortView.setText(sortView.adapter.getItem(Settings.torrentsSortMode.ordinal) as String)
        sidePanelHeader.sort_view_layout.let { layout ->
            val startIconDrawable = layout.startIconDrawable
            for (child in layout.childrenRecursiveSequence()) {
                if (child is ImageView && child.drawable === startIconDrawable) {
                    (child as Checkable).isChecked = Settings.torrentsSortOrder == TorrentsAdapter.SortOrder.Descending
                    break
                }
            }
        }

        statusViewAdapter = statusView.adapter as StatusFilterViewAdapter
        trackersViewAdapter = trackersView.adapter as TrackersViewAdapter
        directoriesViewAdapter = directoriesView.adapter as DirectoriesViewAdapter

        setupDrawerListeners()
    }

    private fun setupDrawerListeners() {
        val sidePanelHeader = requiredActivity.side_panel.getHeaderView(0)

        sortView?.setOnItemClickListener { _, _, position, _ ->
            torrentsAdapter?.apply {
                sortMode = TorrentsAdapter.SortMode.values()[position]
                if (Rpc.isConnected) {
                    Settings.torrentsSortMode = sortMode
                }
            }
        }

        val sortViewLayout = sidePanelHeader.sort_view_layout

        sortViewLayout.setStartIconOnClickListener {
            torrentsAdapter?.apply {
                sortOrder = if (sortOrder == TorrentsAdapter.SortOrder.Ascending) {
                    TorrentsAdapter.SortOrder.Descending
                } else {
                    TorrentsAdapter.SortOrder.Ascending
                }
                Settings.torrentsSortOrder = sortOrder
            }
            (it as Checkable).isChecked = Settings.torrentsSortOrder == TorrentsAdapter.SortOrder.Descending
        }

        statusView?.setOnItemClickListener { _, _, position, _ ->
            torrentsAdapter?.apply {
                statusFilterMode = TorrentsAdapter.StatusFilterMode.values()[position]
                if (Rpc.isConnected) {
                    Settings.torrentsStatusFilter = statusFilterMode
                }
            }
        }

        trackersView?.setOnItemClickListener { _, _, position, _ ->
            torrentsAdapter?.apply {
                trackerFilter = trackersViewAdapter?.getTrackerFilter(position) ?: ""
                if (Rpc.isConnected) {
                    Settings.torrentsTrackerFilter = trackerFilter
                }
            }
        }

        directoriesView?.setOnItemClickListener { _, _, position, _ ->
            torrentsAdapter?.apply {
                directoryFilter = directoriesViewAdapter?.getDirectoryFilter(position) ?: ""
                if (Rpc.isConnected) {
                    Settings.torrentsDirectoryFilter = directoryFilter
                }
            }
        }
    }

    private fun clearDrawerListeners() {
        sortView?.onItemClickListener = null
        val sidePanelHeader = requiredActivity.side_panel.getHeaderView(0)
        sidePanelHeader.sort_view_layout.setStartIconOnClickListener(null)
        statusView?.onItemClickListener = null
        trackersView?.onItemClickListener = null
        directoriesView?.onItemSelectedListener = null
    }

    override fun onViewStateRestored(savedInstanceState: Bundle?) {
        super.onViewStateRestored(savedInstanceState)
        torrentsAdapter?.apply {
            restoreInstanceState(savedInstanceState)
            update()
            selector.restoreInstanceState(savedInstanceState)
        }
    }

    override fun onDestroyView() {
        menu = null
        searchMenuItem = null

        serversViewAdapter = null

        listSettingsLayout = null
        sortView = null
        statusView = null
        statusViewAdapter = null

        trackersView = null
        trackersViewAdapter = null

        directoriesView = null
        directoriesViewAdapter = null

        clearDrawerListeners()

        torrentsAdapter = null

        super.onDestroyView()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        torrentsAdapter?.apply {
            saveInstanceState(outState)
            selector.saveInstanceState(outState)
        }

        outState.putBoolean(NAVIGATED_FROM_KEY, navigatedFrom)
    }

    override fun onNavigatedFrom() {
        navigatedFrom = true
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
                torrentsAdapter?.filterString = newText.trim()
                return true
            }

            override fun onQueryTextSubmit(query: String): Boolean {
                return false
            }
        })
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
                Rpc.serverSettings.isAlternativeSpeedLimitsEnabled = menuItem.isChecked
            }
            else -> return menuItem.onNavDestinationSelected(navController)
        }
        return true
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        info("onActivityResult $resultCode $data")
        if (resultCode == Activity.RESULT_OK && data != null) {
            navController.navigate(R.id.action_torrentsListFragment_to_addTorrentFileFragment, bundleOf(AddTorrentFragment.URI to data.data!!.toString()))
        }
    }

    private fun onRpcStatusChanged(status: Int) {
        if (status == RpcStatus.Disconnected || status == RpcStatus.Connected) {
            if (Rpc.isConnected) {
                menu?.findItem(R.id.alternative_speed_limits)?.isChecked = Rpc.serverSettings.isAlternativeSpeedLimitsEnabled
            } else {
                torrentsAdapter?.selector?.actionMode?.finish()
                searchMenuItem?.collapseActionView()
                navController.popDialog()
            }
            listSettingsLayout?.setChildrenEnabled(Rpc.isConnected)
        }

        updateMenuItems()
    }

    private fun onTorrentsUpdated() {
        statusViewAdapter?.update()
        trackersViewAdapter?.update()
        directoriesViewAdapter?.update()
        torrentsAdapter?.update()

        menu?.findItem(R.id.alternative_speed_limits)?.isChecked =
                if (Rpc.isConnected) Rpc.serverSettings.isAlternativeSpeedLimitsEnabled else false
    }

    private fun updateTitle() {
        toolbar?.title = if (Servers.hasServers) {
            val currentServer = Servers.currentServer.value!!
            getString(R.string.current_server_string,
                      currentServer.name,
                      currentServer.address)
        } else {
            getString(R.string.app_name)
        }
    }

    private fun updateSubtitle(serverStats: ServerStats) {
        toolbar?.subtitle = if (Rpc.isConnected) {
            getString(R.string.main_activity_subtitle,
                      Utils.formatByteSpeed(requireContext(), serverStats.downloadSpeed()),
                      Utils.formatByteSpeed(requireContext(), serverStats.uploadSpeed()))
        } else {
            null
        }
    }

    private fun updateMenuItems() {
        val menu = this.menu ?: return

        val connectMenuItem = menu.findItem(R.id.connect)
        connectMenuItem.isEnabled = Servers.hasServers
        connectMenuItem.title = when (Rpc.status.value) {
            RpcStatus.Disconnected -> getString(R.string.connect)
            RpcStatus.Connecting,
            RpcStatus.Connected -> getString(R.string.disconnect)
            else -> ""
        }

        val connected = Rpc.isConnected
        searchMenuItem?.isVisible = connected
        menu.findItem(R.id.add_torrent_file).isEnabled = connected
        menu.findItem(R.id.action_torrentsListFragment_to_addTorrentLinkFragment).isEnabled = connected
        menu.findItem(R.id.action_torrentsListFragment_to_serverSettingsFragment).isEnabled = connected
        menu.findItem(R.id.alternative_speed_limits).isEnabled = connected
        menu.findItem(R.id.action_torrentsListFragment_to_serverStatsDialogFragment).isEnabled = connected
    }

    private fun updatePlaceholder() {
        placeholder.text = when {
            torrentsAdapter?.itemCount ?: 0 != 0 -> null
            Rpc.isConnected -> getString(R.string.no_torrents)
            else -> Rpc.statusString
        }

        progress_bar.visibility = if (Rpc.status.value == RpcStatus.Connecting && torrentsAdapter?.itemCount ?: 0 == 0) {
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
            navController.navigate(R.id.action_torrentsListFragment_to_filePickerFragment)
        }
    }

    class DonateDialogFragment : DialogFragment() {
        override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
            return MaterialAlertDialogBuilder(requireContext())
                    .setMessage(getString(R.string.donate_message) + "\n\n" + getString(R.string.donate_dialog_again))
                    .setNegativeButton(android.R.string.cancel, null)
                    .setPositiveButton(R.string.donate) { _, _ ->
                        findNavController().navigate(R.id.action_donateDialogFragment_to_aboutFragment, bundleOf(AboutFragment.DONATE to true))
                    }
                    .create()
        }
    }
}
