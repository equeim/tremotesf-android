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
import android.widget.AdapterView
import android.widget.ImageButton
import android.widget.Spinner

import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.core.os.bundleOf
import androidx.core.view.GravityCompat
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.commit
import androidx.navigation.findNavController
import androidx.navigation.fragment.DialogFragmentNavigator
import androidx.navigation.fragment.findNavController
import androidx.navigation.ui.onNavDestinationSelected
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.android.synthetic.main.navigation_activity.*

import org.jetbrains.anko.AnkoLogger
import org.jetbrains.anko.design.longSnackbar
import org.jetbrains.anko.info

import org.equeim.tremotesf.AboutFragment
import org.equeim.tremotesf.AddTorrentFragmentArguments
import org.equeim.tremotesf.BuildConfig
import org.equeim.tremotesf.NavigationActivity
import org.equeim.tremotesf.NavigationFragment
import org.equeim.tremotesf.R
import org.equeim.tremotesf.Rpc
import org.equeim.tremotesf.RpcStatus
import org.equeim.tremotesf.Servers
import org.equeim.tremotesf.Settings
import org.equeim.tremotesf.torrentpropertiesfragment.TorrentFilesAdapter
import org.equeim.tremotesf.utils.ArraySpinnerAdapterWithHeader
import org.equeim.tremotesf.utils.Utils
import org.equeim.tremotesf.utils.setChildrenEnabled

import kotlinx.android.synthetic.main.torrents_list_fragment.*
import kotlinx.android.synthetic.main.side_panel_header.view.*


class TorrentsListFragment : NavigationFragment(R.layout.torrents_list_fragment,
                                                0,
                                                R.menu.main_activity_menu), AnkoLogger {
    companion object {
        private const val SEARCH_QUERY_KEY = "org.equeim.tremotesf.TorrentsListFragment.searchQuery"
    }

    private var menu: Menu? = null
    private var searchMenuItem: MenuItem? = null
    private var searchView: SearchView? = null
    private var restoredSearchQuery: String? = null

    private var serversSpinner: Spinner? = null
    private var serversSpinnerAdapter: ServersSpinnerAdapter? = null

    private var listSettingsLayout: ViewGroup? = null
    private var sortSpinner: Spinner? = null
    private var statusSpinner: Spinner? = null
    private var statusSpinnerAdapter: StatusFilterSpinnerAdapter? = null

    private var trackersSpinner: Spinner? = null
    private var trackersSpinnerAdapter: TrackersSpinnerAdapter? = null

    private var directoriesSpinner: Spinner? = null
    private var directoriesSpinnerAdapter: DirectoriesSpinnerAdapter? = null

    var torrentsAdapter: TorrentsAdapter? = null
        private set

    private var firstOnStartCalled = false

    private var connecting = false
    private var gotFirstUpdateAfterConnection = true

    private val rpcStatusListener = { status: Int ->
        if (status == RpcStatus.Disconnected || status == RpcStatus.Connected) {
            if (!Rpc.isConnected) {
                torrentsAdapter?.selector?.actionMode?.finish()

                val navController = findNavController()
                if (navController.currentDestination is DialogFragmentNavigator.Destination) {
                    navController.popBackStack()
                }

                searchMenuItem?.collapseActionView()
            } else {
                if (connecting) {
                    gotFirstUpdateAfterConnection = false
                }
            }

            connecting = false

            torrentsAdapter?.update()

            updateSubtitle()
            listSettingsLayout?.setChildrenEnabled(Rpc.isConnected)
            statusSpinnerAdapter?.update()

            trackersSpinnerAdapter?.update()
            directoriesSpinnerAdapter?.update()

            if (Rpc.isConnected) {
                trackersSpinnerAdapter?.let {
                    trackersSpinner?.setSelection(it.trackers.indexOf(Settings.torrentsTrackerFilter) + 1)
                }
                directoriesSpinnerAdapter?.let {
                    directoriesSpinner?.setSelection(it.directories.indexOf(Settings.torrentsDirectoryFilter) + 1)
                }
            }

            menu?.findItem(R.id.alternative_speed_limits)?.isChecked = Rpc.serverSettings.isAlternativeSpeedLimitsEnabled
        } else {
            connecting = true
        }

        updateMenuItems()
        updatePlaceholder()
    }

    private val rpcErrorListener: (Int) -> Unit = {
        updateTitle()
        updateMenuItems()
        serversSpinner?.isEnabled = Servers.hasServers
        updatePlaceholder()
    }

    private val torrentsUpdatedListener = {
        gotFirstUpdateAfterConnection = true
        statusSpinnerAdapter?.update()
        trackersSpinnerAdapter?.update()
        directoriesSpinnerAdapter?.update()
        if (firstOnStartCalled) {
            torrentsAdapter?.update()
        }
        updatePlaceholder()

        menu?.findItem(R.id.alternative_speed_limits)?.isChecked = Rpc.serverSettings.isAlternativeSpeedLimitsEnabled
    }

    private val serverStatsUpdatedListener = {
        updateSubtitle()
    }

    /*private val serversListener = {
        serversSpinnerAdapter?.update()
        serversSpinner?.isEnabled = Servers.hasServers
    }

    private val currentServerListener: () -> Unit = {
        updateTitle()
        serversSpinnerAdapter?.updateCurrentServer()
    }*/

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
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

        Settings.torrentCompactViewListener = {
            torrents_view.adapter = torrentsAdapter
        }
        Settings.torrentNameMultilineListener = {
            torrents_view.adapter = torrentsAdapter
        }

        updateTitle()

        Rpc.addStatusListener(rpcStatusListener)
        Rpc.addErrorListener(rpcErrorListener)
        //Servers.addServersListener(serversListener)
        //Servers.addCurrentServerListener(currentServerListener)

        Rpc.torrentAddDuplicateListener = {
            view.longSnackbar(R.string.torrent_duplicate)
        }
        Rpc.torrentAddErrorListener = {
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
                        findNavController().navigate(R.id.action_torrentsListFragment_to_donateDialogFragment)
                    }
                }
                torrentsAdapter.update()
            } else {
                findNavController().navigate(R.id.action_torrentsListFragment_to_serverEditFragment)
            }
        } else if (Rpc.isConnected) {
            restoredSearchQuery = savedInstanceState.getString(SEARCH_QUERY_KEY)
            restoredSearchQuery?.let { torrentsAdapter.filterString = it }
            torrentsAdapter.restoreInstanceState(savedInstanceState)
            torrentsAdapter.update()
            torrentsAdapter.selector.restoreInstanceState(savedInstanceState)
        }

        firstOnStartCalled = false
    }

    private fun setupDrawer() {
        val activity = requireActivity() as NavigationActivity

        val sidePanelHeader = activity.side_panel.getHeaderView(0)
        val serversSpinner = sidePanelHeader.servers_spinner
        serversSpinner.isEnabled = Servers.hasServers
        this.serversSpinner = serversSpinner

        val listSettingsLayout = sidePanelHeader.list_settings_layout
        this.listSettingsLayout = listSettingsLayout
        listSettingsLayout.setChildrenEnabled(Rpc.isConnected)

        val sortSpinner = sidePanelHeader.sort_spinner
        this.sortSpinner = sortSpinner

        val statusSpinner = sidePanelHeader.status_spinner
        this.statusSpinner = statusSpinner

        val trackersSpinner = sidePanelHeader.trackers_spinner
        this.trackersSpinner = trackersSpinner

        val directoriesSpinner = sidePanelHeader.directories_spinner
        this.directoriesSpinner = directoriesSpinner

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

                val serversSpinnerAdapter = ServersSpinnerAdapter(serversSpinner)
                serversSpinner.adapter = serversSpinnerAdapter
                serversSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                    override fun onItemSelected(parent: AdapterView<*>,
                                                view: View?,
                                                position: Int,
                                                id: Long) {
                        Servers.currentServer = serversSpinnerAdapter.servers[position]
                    }
                    override fun onNothingSelected(parent: AdapterView<*>) {}
                }

                sidePanelHeader.connection_settings_item.setOnClickListener {
                    try {
                        findNavController(R.id.nav_host).navigate(R.id.action_torrentsListFragment_to_connectionSettingsFragment)
                    } catch (ignore: IllegalArgumentException) {}
                }

                sortSpinner.adapter = ArraySpinnerAdapterWithHeader(resources.getStringArray(R.array.sort_spinner_items),
                                                                    R.string.sort)
                sortSpinner.setSelection(Settings.torrentsSortMode.ordinal)

                sidePanelHeader.sort_order_button.setImageResource(getSortOrderButtonIcon())

                statusSpinner.adapter = StatusFilterSpinnerAdapter(requireContext())
                trackersSpinner.adapter = TrackersSpinnerAdapter(requireContext())
                directoriesSpinner.adapter = DirectoriesSpinnerAdapter(requireContext())

                drawerSetUp = true
            }
        }

        serversSpinnerAdapter = serversSpinner.adapter as ServersSpinnerAdapter
        serversSpinnerAdapter?.update()
        sortSpinner.setSelection(Settings.torrentsSortMode.ordinal)
        statusSpinner.setSelection(Settings.torrentsStatusFilter.ordinal)
        statusSpinnerAdapter = statusSpinner.adapter as StatusFilterSpinnerAdapter
        trackersSpinnerAdapter = trackersSpinner.adapter as TrackersSpinnerAdapter
        directoriesSpinnerAdapter = directoriesSpinner.adapter as DirectoriesSpinnerAdapter

        setupDrawerListeners()
    }

    private fun setupDrawerListeners() {
        val activity = requireActivity() as NavigationActivity

        val sidePanelHeader = activity.side_panel.getHeaderView(0)

        sortSpinner?.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?,
                                        view: View?,
                                        position: Int,
                                        id: Long) {
                torrentsAdapter?.apply {
                    sortMode = TorrentsAdapter.SortMode.values()[position]
                    if (Rpc.isConnected) {
                        Settings.torrentsSortMode = sortMode
                    }
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        sidePanelHeader.sort_order_button.setOnClickListener {
            torrentsAdapter?.apply {
                sortOrder = if (sortOrder == TorrentsAdapter.SortOrder.Ascending) {
                    TorrentsAdapter.SortOrder.Descending
                } else {
                    TorrentsAdapter.SortOrder.Ascending
                }
                Settings.torrentsSortOrder = sortOrder
            }
            (it as ImageButton).setImageResource(getSortOrderButtonIcon())
        }

        statusSpinner?.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>,
                                        view: View?,
                                        position: Int,
                                        id: Long) {
                torrentsAdapter?.apply {
                    statusFilterMode = TorrentsAdapter.StatusFilterMode.values()[position]
                    updatePlaceholder()
                    if (Rpc.isConnected) {
                        Settings.torrentsStatusFilter = statusFilterMode
                    }
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        trackersSpinner?.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>,
                                        view: View?,
                                        position: Int,
                                        id: Long) {
                torrentsAdapter?.apply {
                    trackerFilter = if (position == 0) {
                        ""
                    } else {
                        trackersSpinnerAdapter?.trackers?.get(position - 1) ?: ""
                    }
                    updatePlaceholder()
                    if (Rpc.isConnected) {
                        Settings.torrentsTrackerFilter = trackerFilter
                    }
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        directoriesSpinner?.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>,
                                        view: View?,
                                        position: Int,
                                        id: Long) {
                torrentsAdapter?.apply {
                    directoryFilter = if (position == 0) {
                        ""
                    } else {
                        directoriesSpinnerAdapter?.directories?.get(position - 1) ?: ""
                    }
                    updatePlaceholder()
                    if (Rpc.isConnected) {
                        Settings.torrentsDirectoryFilter = directoryFilter
                    }
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>) {}
        }
    }

    private fun clearDrawerListeners() {
        sortSpinner?.onItemSelectedListener = null
        val sidePanelHeader = (requireActivity() as NavigationActivity).side_panel.getHeaderView(0)
        sidePanelHeader.sort_order_button.setOnClickListener(null)
        statusSpinner?.onItemSelectedListener = null
        trackersSpinner?.onItemSelectedListener = null
        directoriesSpinner?.onItemSelectedListener = null
    }

    override fun onStart() {
        super.onStart()
        torrentsUpdatedListener()
        serverStatsUpdatedListener()
        Rpc.addTorrentsUpdatedListener(torrentsUpdatedListener)
        Rpc.addServerStatsUpdatedListener(serverStatsUpdatedListener)
        firstOnStartCalled = true
    }

    override fun onStop() {
        super.onStop()
        Rpc.removeTorrentsUpdatedListener(torrentsUpdatedListener)
        Rpc.removeServerStatsUpdatedListener(serverStatsUpdatedListener)
    }

    override fun onDestroyView() {
        Rpc.removeStatusListener(rpcStatusListener)
        Rpc.removeErrorListener(rpcErrorListener)
        Rpc.torrentAddDuplicateListener = null
        Rpc.torrentAddErrorListener = null
        //Servers.removeServersListener(serversListener)
        //Servers.removeCurrentServerListener(currentServerListener)
        Settings.torrentCompactViewListener = null
        Settings.torrentNameMultilineListener = null

        menu = null
        searchMenuItem = null
        searchView = null
        restoredSearchQuery = null

        serversSpinner = null
        serversSpinnerAdapter = null

        listSettingsLayout = null
        sortSpinner = null
        statusSpinner = null
        statusSpinnerAdapter = null

        trackersSpinner = null
        trackersSpinnerAdapter = null

        directoriesSpinner = null
        directoriesSpinnerAdapter = null

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
        searchView?.let {
            if (!it.isIconified) {
                outState.putString(SEARCH_QUERY_KEY, it.query.toString())
            }
        }
    }

    private fun setupMenuItems() {
        val toolbar = this.toolbar ?: return

        val menu = toolbar.menu!!
        this.menu = menu

        val searchMenuItem = menu.findItem(R.id.search)
        this.searchMenuItem = searchMenuItem
        val searchView = searchMenuItem.actionView as SearchView
        this.searchView = searchView
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextChange(newText: String): Boolean {
                torrentsAdapter?.filterString = newText.trim()
                updatePlaceholder()
                return true
            }

            override fun onQueryTextSubmit(query: String): Boolean {
                return false
            }
        })

        if (restoredSearchQuery != null) {
            searchMenuItem.expandActionView()
            searchView.setQuery(restoredSearchQuery, true)
        }

        updateMenuItems()

        menu.findItem(R.id.alternative_speed_limits).isChecked = Rpc.serverSettings.isAlternativeSpeedLimitsEnabled
    }

    override fun onToolbarMenuItemClicked(menuItem: MenuItem): Boolean {
        when (menuItem.itemId) {
            R.id.connect -> {
                if (Rpc.status == RpcStatus.Disconnected) {
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
            else -> return menuItem.onNavDestinationSelected(findNavController())
        }
        return true
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        info("onActivityResult $resultCode $data")
        if (resultCode == Activity.RESULT_OK && data != null) {
            findNavController().navigate(R.id.action_torrentsListFragment_to_addTorrentFileFragment, bundleOf(AddTorrentFragmentArguments.URI to data.data!!.toString()))
        }
    }

    private fun updateTitle() {
        toolbar?.title = if (Servers.hasServers) {
            val currentServer = Servers.currentServer!!
            getString(R.string.current_server_string,
                      currentServer.name,
                      currentServer.address)
        } else {
            getString(R.string.app_name)
        }
    }

    private fun updateSubtitle() {
        toolbar?.subtitle = if (Rpc.isConnected) {
            getString(R.string.main_activity_subtitle,
                      Utils.formatByteSpeed(requireContext(), Rpc.serverStats.downloadSpeed()),
                      Utils.formatByteSpeed(requireContext(), Rpc.serverStats.uploadSpeed()))
        } else {
            null
        }
    }

    private fun updateMenuItems() {
        val menu = this.menu ?: return

        val connectMenuItem = menu.findItem(R.id.connect)
        connectMenuItem.isEnabled = Servers.hasServers
        connectMenuItem.title = when (Rpc.status) {
            RpcStatus.Disconnected -> getString(R.string.connect)
            RpcStatus.Connecting,
            RpcStatus.Connected -> getString(R.string.disconnect)
            else -> getString(R.string.connect)
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
        placeholder.text = if (Rpc.status == RpcStatus.Connected) {
            if (gotFirstUpdateAfterConnection && torrentsAdapter?.itemCount ?: 0 == 0) {
                getString(R.string.no_torrents)
            } else {
                null
            }
        } else {
            Rpc.statusString
        }

        progress_bar.visibility = if (Rpc.status == RpcStatus.Connecting) {
            View.VISIBLE
        } else {
            View.GONE
        }
    }

    private fun getSortOrderButtonIcon(): Int {
        return if (torrentsAdapter?.sortOrder == TorrentsAdapter.SortOrder.Ascending) {
            R.drawable.sort_ascending
        } else {
            R.drawable.sort_descending
        }
    }

    private fun startFilePickerActivity() {
        try {
            startActivityForResult(Intent(Intent.ACTION_GET_CONTENT)
                                           .addCategory(Intent.CATEGORY_OPENABLE)
                                           .setType("application/x-bittorrent"),
                                   0)
        } catch (error: ActivityNotFoundException) {
            findNavController().navigate(R.id.action_torrentsListFragment_to_filePickerFragment)
        }
    }

    class DonateDialogFragment : DialogFragment() {
        override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
            return AlertDialog.Builder(requireContext())
                    .setMessage(getString(R.string.donate_message) + "\n\n" + getString(R.string.donate_dialog_again))
                    .setNegativeButton(android.R.string.cancel, null)
                    .setPositiveButton(R.string.donate) { _, _ ->
                        findNavController().navigate(R.id.action_donateDialogFragment_to_aboutFragment, bundleOf(AboutFragment.DONATE to true))
                    }
                    .create()
        }
    }
}
