/*
 * Copyright (C) 2017-2018 Alexey Rochev <equeim@gmail.com>
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
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.Spinner

import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.view.ActionMode
import androidx.appcompat.widget.SearchView
import androidx.appcompat.widget.Toolbar
import androidx.core.view.GravityCompat
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.transaction
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager

import org.jetbrains.anko.AnkoLogger
import org.jetbrains.anko.debug
import org.jetbrains.anko.design.longSnackbar
import org.jetbrains.anko.intentFor
import org.jetbrains.anko.startActivity

import org.equeim.libtremotesf.BaseRpc
import org.equeim.tremotesf.AboutActivity
import org.equeim.tremotesf.AddTorrentFileActivity
import org.equeim.tremotesf.AddTorrentLinkActivity
import org.equeim.tremotesf.BaseActivity
import org.equeim.tremotesf.BuildConfig
import org.equeim.tremotesf.FilePickerActivity
import org.equeim.tremotesf.R
import org.equeim.tremotesf.Rpc
import org.equeim.tremotesf.Selector
import org.equeim.tremotesf.Servers
import org.equeim.tremotesf.Settings
import org.equeim.tremotesf.SettingsActivity
import org.equeim.tremotesf.serversactivity.ServerEditActivity
import org.equeim.tremotesf.serversactivity.ServersActivity
import org.equeim.tremotesf.serversettingsactivity.ServerSettingsActivity
import org.equeim.tremotesf.torrentpropertiesactivity.TorrentFilesAdapter
import org.equeim.tremotesf.utils.ArraySpinnerAdapterWithHeader
import org.equeim.tremotesf.utils.Utils
import org.equeim.tremotesf.utils.setChildrenEnabled

import kotlinx.android.synthetic.main.main_activity.*
import kotlinx.android.synthetic.main.side_panel_header.view.*


private const val SEARCH_QUERY_KEY = "org.equeim.tremotesf.MainActivity.searchQuery"

class MainActivity : BaseActivity(), Selector.ActionModeActivity, AnkoLogger {
    private lateinit var drawerToggle: ActionBarDrawerToggle

    private var menu: Menu? = null
    private lateinit var searchMenuItem: MenuItem
    private lateinit var searchView: SearchView
    private var restoredSearchQuery: String? = null

    private lateinit var serversSpinner: Spinner
    private lateinit var serversSpinnerAdapter: ServersSpinnerAdapter

    private lateinit var listSettingsLayout: ViewGroup
    private lateinit var sortSpinner: Spinner
    private lateinit var statusSpinner: Spinner
    private lateinit var statusSpinnerAdapter: StatusFilterSpinnerAdapter

    private lateinit var trackersSpinner: Spinner
    private lateinit var trackersSpinnerAdapter: TrackersSpinnerAdapter

    private lateinit var directoriesSpinner: Spinner
    private lateinit var directoriesSpinnerAdapter: DirectoriesSpinnerAdapter

    lateinit var torrentsAdapter: TorrentsAdapter
        private set

    override val actionMode: ActionMode?
        get() = torrentsAdapter.selector.actionMode

    private val rpcStatusListener = { status: Int ->
        if (status == BaseRpc.Status.Disconnected || status == BaseRpc.Status.Connected) {
            if (!Rpc.instance.isConnected) {
                torrentsAdapter.selector.actionMode?.finish()

                supportFragmentManager.apply {
                    findFragmentByTag(TorrentsAdapter.SetLocationDialogFragment.TAG)
                            ?.let { transaction { remove(it) } }
                    findFragmentByTag(TorrentFilesAdapter.RenameDialogFragment.TAG)
                            ?.let { transaction { remove(it) } }
                    findFragmentByTag(TorrentsAdapter.RemoveDialogFragment.TAG)
                            ?.let { transaction { remove(it) } }
                }

                if (menu != null) {
                    searchMenuItem.collapseActionView()
                }
            }

            torrentsAdapter.update()

            updateSubtitle()
            listSettingsLayout.setChildrenEnabled(Rpc.instance.isConnected)
            statusSpinnerAdapter.update()

            trackersSpinnerAdapter.update()
            directoriesSpinnerAdapter.update()
            
            if (Rpc.instance.isConnected) {
                trackersSpinner.setSelection(trackersSpinnerAdapter.trackers.indexOf(Settings.torrentsTrackerFilter) + 1)
                directoriesSpinner.setSelection(directoriesSpinnerAdapter.directories.indexOf(Settings.torrentsDirectoryFilter) + 1)
            }

            menu?.findItem(R.id.alternative_speed_limits)?.isChecked = Rpc.instance.serverSettings.isAlternativeSpeedLimitsEnabled
        }

        updateMenuItems()
        updatePlaceholder()
    }

    private val rpcErrorListener: (Int) -> Unit = {
        updateTitle()
        updateMenuItems()
        serversSpinner.isEnabled = Servers.hasServers
        updatePlaceholder()
    }

    private val torrentsUpdatedListener = {
        statusSpinnerAdapter.update()
        trackersSpinnerAdapter.update()
        directoriesSpinnerAdapter.update()
        torrentsAdapter.update()
        updatePlaceholder()

        menu?.findItem(R.id.alternative_speed_limits)?.isChecked = Rpc.instance.serverSettings.isAlternativeSpeedLimitsEnabled
    }

    private val serverStatsUpdatedListener = {
        updateSubtitle()
    }

    private val serversListener = {
        serversSpinnerAdapter.update()
        serversSpinner.isEnabled = Servers.hasServers
    }

    private val currentServerListener = {
        updateTitle()
        serversSpinnerAdapter.updateCurrentServer()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        debug("MainActivity onCreate")

        setTheme(Settings.themeNoActionBar)

        setContentView(R.layout.main_activity)
        setPreLollipopShadow()

        setSupportActionBar(toolbar as Toolbar)
        supportActionBar!!.setDisplayHomeAsUpEnabled(true)
        supportActionBar!!.setHomeButtonEnabled(true)

        drawer_layout.setDrawerShadow(R.drawable.drawer_shadow, GravityCompat.START)
        drawerToggle = ActionBarDrawerToggle(this,
                                             drawer_layout,
                                             R.string.open_side_panel,
                                             R.string.close_side_panel)

        torrentsAdapter = TorrentsAdapter(this)

        torrents_view.adapter = torrentsAdapter
        torrents_view.layoutManager = LinearLayoutManager(this)
        torrents_view.addItemDecoration(DividerItemDecoration(this, DividerItemDecoration.VERTICAL))
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
                R.id.settings -> startActivity<SettingsActivity>()
                R.id.servers -> startActivity<ServersActivity>()
                R.id.about -> startActivity<AboutActivity>()
                R.id.quit -> Utils.shutdownApp()
                else -> return@setNavigationItemSelectedListener false
            }
            return@setNavigationItemSelectedListener true
        }

        val sidePanelHeader = side_panel.getHeaderView(0)

        serversSpinner = sidePanelHeader.servers_spinner
        serversSpinner.isEnabled = Servers.hasServers
        serversSpinnerAdapter = ServersSpinnerAdapter(serversSpinner)
        serversSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>,
                                        view: View?,
                                        position: Int,
                                        id: Long) {
                Servers.currentServer = serversSpinnerAdapter.servers[position]
            }

            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        listSettingsLayout = sidePanelHeader.list_settings_layout
        listSettingsLayout.setChildrenEnabled(Rpc.instance.isConnected)

        sortSpinner = sidePanelHeader.sort_spinner
        sortSpinner.adapter = ArraySpinnerAdapterWithHeader(resources.getStringArray(R.array.sort_spinner_items),
                                                            R.string.sort)
        sortSpinner.setSelection(Settings.torrentsSortMode.ordinal)
        sortSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?,
                                        view: View?,
                                        position: Int,
                                        id: Long) {
                torrentsAdapter.sortMode = TorrentsAdapter.SortMode.values()[position]
                if (Rpc.instance.isConnected) {
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

        statusSpinner = sidePanelHeader.status_spinner
        statusSpinnerAdapter = StatusFilterSpinnerAdapter(this)
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
                    if (Rpc.instance.isConnected) {
                        Settings.torrentsStatusFilter = torrentsAdapter.statusFilterMode
                    }
                }
                previousPosition = position
            }

            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        trackersSpinner = sidePanelHeader.trackers_spinner
        trackersSpinnerAdapter = TrackersSpinnerAdapter(this)
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
                    if (Rpc.instance.isConnected) {
                        Settings.torrentsTrackerFilter = torrentsAdapter.trackerFilter
                    }
                }
                previousPosition = position
            }

            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        directoriesSpinner = sidePanelHeader.directories_spinner
        directoriesSpinnerAdapter = DirectoriesSpinnerAdapter(this)
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
                    if (Rpc.instance.isConnected) {
                        Settings.torrentsDirectoryFilter = torrentsAdapter.directoryFilter
                    }
                }
                previousPosition = position
            }

            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        updateTitle()

        Rpc.instance.addStatusListener(rpcStatusListener)
        Rpc.instance.addErrorListener(rpcErrorListener)
        Servers.addServersListener(serversListener)
        Servers.addCurrentServerListener(currentServerListener)

        Rpc.instance.torrentAddDuplicateListener = {
            coordinator_layout.longSnackbar(R.string.torrent_duplicate)
        }
        Rpc.instance.torrentAddErrorListener = {
            coordinator_layout.longSnackbar(R.string.torrent_add_error)
        }

        if (savedInstanceState == null) {
            if (Servers.hasServers) {
                if (!Settings.donateDialogShown) {
                    val info = packageManager.getPackageInfo(BuildConfig.APPLICATION_ID, 0)
                    val currentTime = System.currentTimeMillis()
                    val installDays = TimeUnit.DAYS.convert(currentTime - info.firstInstallTime, TimeUnit.MILLISECONDS)
                    val updateDays = TimeUnit.DAYS.convert(currentTime - info.lastUpdateTime, TimeUnit.MILLISECONDS)
                    if (installDays >= 2 && updateDays >= 1) {
                        Settings.donateDialogShown = true
                        DonateDialogFragment().show(supportFragmentManager, null)
                    }
                }
            } else {
                startActivity<ServerEditActivity>()
            }
        } else if (Rpc.instance.isConnected) {
            restoredSearchQuery = savedInstanceState.getString(SEARCH_QUERY_KEY)
            if (restoredSearchQuery != null) {
                torrentsAdapter.filterString = restoredSearchQuery!!
            }
            torrentsAdapter.restoreInstanceState(savedInstanceState)
            torrentsAdapter.selector.restoreInstanceState(savedInstanceState)
        }
    }

    override fun onStart() {
        super.onStart()
        torrentsUpdatedListener()
        serverStatsUpdatedListener()
        Rpc.instance.addTorrentsUpdatedListener(torrentsUpdatedListener)
        Rpc.instance.addServerStatsUpdatedListener(serverStatsUpdatedListener)
    }

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)
        drawerToggle.syncState()
    }

    override fun onStop() {
        super.onStop()
        Rpc.instance.removeTorrentsUpdatedListener(torrentsUpdatedListener)
        Rpc.instance.removeServerStatsUpdatedListener(serverStatsUpdatedListener)
    }

    override fun onDestroy() {
        super.onDestroy()
        debug("MainActivity onDestroy")
        Rpc.instance.removeStatusListener(rpcStatusListener)
        Rpc.instance.removeErrorListener(rpcErrorListener)
        Rpc.instance.torrentAddDuplicateListener = null
        Rpc.instance.torrentAddErrorListener = null
        Servers.removeServersListener(serversListener)
        Servers.removeCurrentServerListener(currentServerListener)
        Settings.torrentCompactViewListener = null
        Settings.torrentNameMultilineListener = null
    }

    override fun onConfigurationChanged(newConfig: Configuration?) {
        super.onConfigurationChanged(newConfig)
        drawerToggle.onConfigurationChanged(newConfig)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        torrentsAdapter.saveInstanceState(outState)
        torrentsAdapter.selector.saveInstanceState(outState)
        if (menu != null && !searchView.isIconified) {
            outState.putString(SEARCH_QUERY_KEY, searchView.query.toString())
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        this.menu = menu
        menuInflater.inflate(R.menu.main_activity_menu, menu)

        searchMenuItem = menu.findItem(R.id.search)
        searchView = searchMenuItem.actionView as SearchView
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextChange(newText: String): Boolean {
                torrentsAdapter.filterString = newText.trim()
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

        menu.findItem(R.id.alternative_speed_limits).isChecked = Rpc.instance.serverSettings.isAlternativeSpeedLimitsEnabled

        return true
    }

    override fun onOptionsItemSelected(menuItem: MenuItem): Boolean {
        if (drawerToggle.onOptionsItemSelected(menuItem)) {
            return true
        }

        when (menuItem.itemId) {
            R.id.connect -> {
                if (Rpc.instance.status() == BaseRpc.Status.Disconnected) {
                    Rpc.instance.connect()
                } else {
                    Rpc.instance.disconnect()
                }
            }
            R.id.add_torrent_file -> startFilePickerActivity()
            R.id.add_torrent_link -> startActivity<AddTorrentLinkActivity>()
            R.id.server_settings -> startActivity<ServerSettingsActivity>()
            R.id.alternative_speed_limits -> {
                menuItem.isChecked = !menuItem.isChecked
                Rpc.instance.serverSettings.isAlternativeSpeedLimitsEnabled = menuItem.isChecked
            }
            R.id.server_stats -> ServerStatsDialogFragment().show(supportFragmentManager, null)
            else -> return false
        }

        return true
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (resultCode == Activity.RESULT_OK && data != null) {
            startActivity(intentFor<AddTorrentFileActivity>().setData(data.data))
        }
    }

    private fun updateTitle() {
        title = if (Servers.hasServers) {
            val currentServer = Servers.currentServer!!
            getString(R.string.current_server_string,
                      currentServer.name,
                      currentServer.address)
        } else {
            getString(R.string.app_name)
        }
    }

    private fun updateMenuItems() {
        if (menu == null) {
            return
        }

        val connectMenuItem = menu!!.findItem(R.id.connect)
        connectMenuItem.isEnabled = Servers.hasServers
        connectMenuItem.title = when (Rpc.instance.status()) {
            BaseRpc.Status.Disconnected -> getString(R.string.connect)
            BaseRpc.Status.Connecting,
            BaseRpc.Status.Connected -> getString(R.string.disconnect)
            else -> getString(R.string.connect)
        }

        val connected = Rpc.instance.isConnected
        searchMenuItem.isVisible = connected
        menu!!.findItem(R.id.add_torrent_file).isEnabled = connected
        menu!!.findItem(R.id.add_torrent_link).isEnabled = connected
        menu!!.findItem(R.id.server_settings).isEnabled = connected
        menu!!.findItem(R.id.alternative_speed_limits).isEnabled = connected
        menu!!.findItem(R.id.server_stats).isEnabled = connected
    }

    private fun updatePlaceholder() {
        placeholder.text = if (Rpc.instance.status() == BaseRpc.Status.Connected) {
            if (torrentsAdapter.itemCount == 0) {
                getString(R.string.no_torrents)
            } else {
                null
            }
        } else {
            Rpc.instance.statusString
        }

        progress_bar.visibility = if (Rpc.instance.status() == BaseRpc.Status.Connecting) {
            View.VISIBLE
        } else {
            View.GONE
        }
    }

    private fun updateSubtitle() {
        supportActionBar!!.subtitle = if (Rpc.instance.isConnected) {
            getString(R.string.main_activity_subtitle,
                      Utils.formatByteSpeed(this, Rpc.instance.serverStats.downloadSpeed()),
                      Utils.formatByteSpeed(this, Rpc.instance.serverStats.uploadSpeed()))
        } else {
            null
        }
    }

    private fun getSortOrderButtonIcon(): Int {
        val ta = obtainStyledAttributes(intArrayOf(if (torrentsAdapter.sortOrder == TorrentsAdapter.SortOrder.Ascending) {
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
            startActivityForResult(intentFor<FilePickerActivity>(), 0)
        }
    }

    override fun onBackPressed() {
        if (drawer_layout.isDrawerOpen(GravityCompat.START)) {
            drawer_layout.closeDrawer(GravityCompat.START)
        } else {
            super.onBackPressed()
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