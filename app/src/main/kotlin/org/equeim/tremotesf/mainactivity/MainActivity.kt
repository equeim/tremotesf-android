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

package org.equeim.tremotesf.mainactivity

import java.util.concurrent.TimeUnit

import android.app.Activity
import android.app.Dialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.Spinner

import androidx.activity.addCallback
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.appcompat.widget.Toolbar
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager

import org.jetbrains.anko.AnkoLogger
import org.jetbrains.anko.design.longSnackbar
import org.jetbrains.anko.intentFor
import org.jetbrains.anko.startActivity

import org.equeim.tremotesf.AboutActivity
import org.equeim.tremotesf.AddTorrentFileActivity
import org.equeim.tremotesf.AddTorrentLinkActivity
import org.equeim.tremotesf.BaseActivity
import org.equeim.tremotesf.BuildConfig
import org.equeim.tremotesf.FilePickerActivity
import org.equeim.tremotesf.R
import org.equeim.tremotesf.Rpc
import org.equeim.tremotesf.RpcStatus
import org.equeim.tremotesf.Servers
import org.equeim.tremotesf.Settings
import org.equeim.tremotesf.SettingsActivity
import org.equeim.tremotesf.connectionsettingsactivity.ServerEditActivity
import org.equeim.tremotesf.connectionsettingsactivity.ConnectionSettingsActivity
import org.equeim.tremotesf.serversettingsactivity.ServerSettingsActivity
import org.equeim.tremotesf.torrentpropertiesactivity.TorrentFilesAdapter
import org.equeim.tremotesf.utils.ArraySpinnerAdapterWithHeader
import org.equeim.tremotesf.utils.Utils
import org.equeim.tremotesf.utils.setChildrenEnabled

import kotlinx.android.synthetic.main.side_panel_header.view.*
import kotlinx.android.synthetic.main.torrents_list_fragment.*


private const val SEARCH_QUERY_KEY = "org.equeim.tremotesf.MainActivity.searchQuery"

class MainActivity : BaseActivity(R.layout.main_activity, true)

class TorrentsListFragment : Fragment(R.layout.torrents_list_fragment), AnkoLogger {
    private lateinit var drawerToggle: ActionBarDrawerToggle

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

    private val rpcStatusListener = { status: Int ->
        if (status == RpcStatus.Disconnected || status == RpcStatus.Connected) {
            if (!Rpc.isConnected) {
                torrentsAdapter?.selector?.actionMode?.finish()

                requireFragmentManager().apply {
                    findFragmentByTag(TorrentsAdapter.SetLocationDialogFragment.TAG)
                            ?.let { commit { remove(it) } }
                    findFragmentByTag(TorrentFilesAdapter.RenameDialogFragment.TAG)
                            ?.let { commit { remove(it) } }
                    findFragmentByTag(TorrentsAdapter.RemoveDialogFragment.TAG)
                            ?.let { commit { remove(it) } }
                }

                searchMenuItem?.collapseActionView()
            }

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
        statusSpinnerAdapter?.update()
        trackersSpinnerAdapter?.update()
        directoriesSpinnerAdapter?.update()
        if ((activity as BaseActivity?)?.creating != true) {
            torrentsAdapter?.update()
        }
        updatePlaceholder()

        menu?.findItem(R.id.alternative_speed_limits)?.isChecked = Rpc.serverSettings.isAlternativeSpeedLimitsEnabled
    }

    private val serverStatsUpdatedListener = {
        updateSubtitle()
    }

    private val serversListener = {
        serversSpinnerAdapter?.update()
        serversSpinner?.isEnabled = Servers.hasServers
    }

    private val currentServerListener: () -> Unit = {
        updateTitle()
        serversSpinnerAdapter?.updateCurrentServer()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        (requireActivity() as AppCompatActivity).apply {
            setSupportActionBar(this@TorrentsListFragment.toolbar as Toolbar)
            supportActionBar?.apply {
                setDisplayHomeAsUpEnabled(true)
                setHomeButtonEnabled(true)
            }
        }

        drawer_layout.setDrawerShadow(R.drawable.drawer_shadow, GravityCompat.START)
        drawerToggle = ActionBarDrawerToggle(requireActivity(),
                                             drawer_layout,
                                             R.string.open_side_panel,
                                             R.string.close_side_panel)
        drawer_layout.addDrawerListener(drawerToggle)

        val torrentsAdapter = TorrentsAdapter(requireActivity() as MainActivity, this)
        this.torrentsAdapter = torrentsAdapter

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

        side_panel.setNavigationItemSelectedListener { menuItem ->
            drawer_layout.closeDrawers()
            when (menuItem.itemId) {
                R.id.settings -> requireContext().startActivity<SettingsActivity>()
                R.id.about -> requireContext().startActivity<AboutActivity>()
                R.id.quit -> Utils.shutdownApp(requireContext())
                else -> return@setNavigationItemSelectedListener false
            }
            return@setNavigationItemSelectedListener true
        }

        val sidePanelHeader = side_panel.getHeaderView(0)

        val serversSpinner = sidePanelHeader.servers_spinner
        this.serversSpinner = serversSpinner
        serversSpinner.isEnabled = Servers.hasServers
        val serversSpinnerAdapter = ServersSpinnerAdapter(serversSpinner)
        this.serversSpinnerAdapter = serversSpinnerAdapter
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
            drawer_layout.closeDrawers()
            requireContext().startActivity<ConnectionSettingsActivity>()
        }

        val listSettingsLayout = sidePanelHeader.list_settings_layout
        this.listSettingsLayout = listSettingsLayout
        listSettingsLayout.setChildrenEnabled(Rpc.isConnected)

        val sortSpinner = sidePanelHeader.sort_spinner
        this.sortSpinner = sortSpinner
        sortSpinner.adapter = ArraySpinnerAdapterWithHeader(resources.getStringArray(R.array.sort_spinner_items),
                                                            R.string.sort)
        sortSpinner.setSelection(Settings.torrentsSortMode.ordinal)
        sortSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?,
                                        view: View?,
                                        position: Int,
                                        id: Long) {
                torrentsAdapter.sortMode = TorrentsAdapter.SortMode.values()[position]
                if (Rpc.isConnected) {
                    Settings.torrentsSortMode = torrentsAdapter.sortMode
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>) {}
        }
        val sortOrderButton = sidePanelHeader.sort_order_button
        sortOrderButton.setImageResource(getSortOrderButtonIcon())
        sortOrderButton.setOnClickListener {
            torrentsAdapter.sortOrder = if (torrentsAdapter.sortOrder == TorrentsAdapter.SortOrder.Ascending) {
                TorrentsAdapter.SortOrder.Descending
            } else {
                TorrentsAdapter.SortOrder.Ascending
            }
            Settings.torrentsSortOrder = torrentsAdapter.sortOrder
            sortOrderButton.setImageResource(getSortOrderButtonIcon())
        }

        val statusSpinner = sidePanelHeader.status_spinner
        this.statusSpinner = statusSpinner
        val statusSpinnerAdapter = StatusFilterSpinnerAdapter(requireContext())
        this.statusSpinnerAdapter = statusSpinnerAdapter
        statusSpinner.adapter = statusSpinnerAdapter
        statusSpinner.setSelection(Settings.torrentsStatusFilter.ordinal)
        statusSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            private var previousPosition = -1
            override fun onItemSelected(parent: AdapterView<*>,
                                        view: View?,
                                        position: Int,
                                        id: Long) {
                if (previousPosition != -1) {
                    torrentsAdapter.statusFilterMode = TorrentsAdapter.StatusFilterMode.values()[position]
                    updatePlaceholder()
                    if (Rpc.isConnected) {
                        Settings.torrentsStatusFilter = torrentsAdapter.statusFilterMode
                    }
                }
                previousPosition = position
            }

            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        val trackersSpinner = sidePanelHeader.trackers_spinner
        this.trackersSpinner = trackersSpinner
        val trackersSpinnerAdapter = TrackersSpinnerAdapter(requireContext())
        this.trackersSpinnerAdapter = trackersSpinnerAdapter
        trackersSpinner.adapter = trackersSpinnerAdapter
        trackersSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            private var previousPosition = -1
            override fun onItemSelected(parent: AdapterView<*>,
                                        view: View?,
                                        position: Int,
                                        id: Long) {
                if (previousPosition != -1) {
                    torrentsAdapter.trackerFilter = if (position == 0) {
                        ""
                    } else {
                        trackersSpinnerAdapter.trackers[position - 1]
                    }
                    updatePlaceholder()
                    if (Rpc.isConnected) {
                        Settings.torrentsTrackerFilter = torrentsAdapter.trackerFilter
                    }
                }
                previousPosition = position
            }

            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        val directoriesSpinner = sidePanelHeader.directories_spinner
        this.directoriesSpinner = directoriesSpinner
        val directoriesSpinnerAdapter = DirectoriesSpinnerAdapter(requireContext())
        this.directoriesSpinnerAdapter = directoriesSpinnerAdapter
        directoriesSpinner.adapter = directoriesSpinnerAdapter
        directoriesSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            private var previousPosition = -1
            override fun onItemSelected(parent: AdapterView<*>,
                                        view: View?,
                                        position: Int,
                                        id: Long) {
                if (previousPosition != -1) {
                    torrentsAdapter.directoryFilter = if (position == 0) {
                        ""
                    } else {
                        directoriesSpinnerAdapter.directories[position - 1]
                    }
                    updatePlaceholder()
                    if (Rpc.isConnected) {
                        Settings.torrentsDirectoryFilter = torrentsAdapter.directoryFilter
                    }
                }
                previousPosition = position
            }

            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        updateTitle()

        Rpc.addStatusListener(rpcStatusListener)
        Rpc.addErrorListener(rpcErrorListener)
        Servers.addServersListener(serversListener)
        Servers.addCurrentServerListener(currentServerListener)

        Rpc.torrentAddDuplicateListener = {
            coordinator_layout.longSnackbar(R.string.torrent_duplicate)
        }
        Rpc.torrentAddErrorListener = {
            coordinator_layout.longSnackbar(R.string.torrent_add_error)
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
                        DonateDialogFragment().show(requireFragmentManager(), null)
                    }
                }
            } else {
                requireContext().startActivity<ServerEditActivity>()
            }
        } else if (Rpc.isConnected) {
            restoredSearchQuery = savedInstanceState.getString(SEARCH_QUERY_KEY)
            restoredSearchQuery?.let { torrentsAdapter.filterString = it }
            torrentsAdapter.restoreInstanceState(savedInstanceState)
            torrentsAdapter.update()
            torrentsAdapter.selector.restoreInstanceState(savedInstanceState)
        }
    }

    override fun onViewStateRestored(savedInstanceState: Bundle?) {
        super.onViewStateRestored(savedInstanceState)
        drawerToggle.syncState()

        val backPressedCallback = requireActivity().onBackPressedDispatcher.addCallback(this) {
            drawer_layout.closeDrawer(GravityCompat.START)
            isEnabled = false
        }

        backPressedCallback.isEnabled = drawer_layout.isDrawerOpen(GravityCompat.START)

        drawer_layout.addDrawerListener(object : DrawerLayout.DrawerListener {
            override fun onDrawerStateChanged(newState: Int) {

            }

            override fun onDrawerSlide(drawerView: View, slideOffset: Float) {

            }

            override fun onDrawerOpened(drawerView: View) {
                backPressedCallback.isEnabled = true
            }

            override fun onDrawerClosed(drawerView: View) {
                backPressedCallback.isEnabled = false
            }
        })
    }

    override fun onStart() {
        super.onStart()
        torrentsUpdatedListener()
        serverStatsUpdatedListener()
        Rpc.addTorrentsUpdatedListener(torrentsUpdatedListener)
        Rpc.addServerStatsUpdatedListener(serverStatsUpdatedListener)
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
        Servers.removeServersListener(serversListener)
        Servers.removeCurrentServerListener(currentServerListener)
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

        torrentsAdapter = null

        super.onDestroyView()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        drawerToggle.onConfigurationChanged(newConfig)
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

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        this.menu = menu
        inflater.inflate(R.menu.main_activity_menu, menu)

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

    override fun onOptionsItemSelected(menuItem: MenuItem): Boolean {
        if (drawerToggle.onOptionsItemSelected(menuItem)) {
            return true
        }

        when (menuItem.itemId) {
            R.id.connect -> {
                if (Rpc.status == RpcStatus.Disconnected) {
                    Rpc.nativeInstance.connect()
                } else {
                    Rpc.nativeInstance.disconnect()
                }
            }
            R.id.add_torrent_file -> startFilePickerActivity()
            R.id.add_torrent_link -> requireContext().startActivity<AddTorrentLinkActivity>()
            R.id.server_settings -> requireContext().startActivity<ServerSettingsActivity>()
            R.id.alternative_speed_limits -> {
                menuItem.isChecked = !menuItem.isChecked
                Rpc.serverSettings.isAlternativeSpeedLimitsEnabled = menuItem.isChecked
            }
            R.id.server_stats -> ServerStatsDialogFragment().show(requireFragmentManager(), null)
            else -> return false
        }

        return true
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == Activity.RESULT_OK && data != null) {
            requireContext().startActivity(requireContext().intentFor<AddTorrentFileActivity>().setData(data.data))
        }
    }

    private fun updateTitle() {
        requireActivity().title = if (Servers.hasServers) {
            val currentServer = Servers.currentServer!!
            getString(R.string.current_server_string,
                      currentServer.name,
                      currentServer.address)
        } else {
            getString(R.string.app_name)
        }
    }

    private fun updateSubtitle() {
        (requireActivity() as AppCompatActivity).supportActionBar?.subtitle = if (Rpc.isConnected) {
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
        menu.findItem(R.id.add_torrent_link).isEnabled = connected
        menu.findItem(R.id.server_settings).isEnabled = connected
        menu.findItem(R.id.alternative_speed_limits).isEnabled = connected
        menu.findItem(R.id.server_stats).isEnabled = connected
    }

    private fun updatePlaceholder() {
        placeholder.text = if (Rpc.status == RpcStatus.Connected) {
            if (torrentsAdapter?.itemCount ?: 0 == 0) {
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
        val ta = requireContext().obtainStyledAttributes(intArrayOf(if (torrentsAdapter?.sortOrder == TorrentsAdapter.SortOrder.Ascending) {
            R.attr.sortAscendingIcon
        } else {
            R.attr.sortDescendingIcon
        }))
        val resId = ta.getResourceId(0, 0)
        ta.recycle()
        return resId
    }

    private fun startFilePickerActivity() {
        try {
            startActivityForResult(Intent(Intent.ACTION_GET_CONTENT)
                                           .addCategory(Intent.CATEGORY_OPENABLE)
                                           .setType("application/x-bittorrent"),
                                   0)
        } catch (error: ActivityNotFoundException) {
            startActivityForResult(requireContext().intentFor<FilePickerActivity>(), 0)
        }
    }

    class DonateDialogFragment : DialogFragment() {
        override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
            return AlertDialog.Builder(requireContext())
                    .setMessage(getString(R.string.donate_message) + "\n\n" + getString(R.string.donate_dialog_again))
                    .setNegativeButton(android.R.string.cancel, null)
                    .setPositiveButton(R.string.donate) { _, _ ->
                        context?.startActivity<AboutActivity>("donate" to true)
                    }
                    .create()
        }
    }
}
