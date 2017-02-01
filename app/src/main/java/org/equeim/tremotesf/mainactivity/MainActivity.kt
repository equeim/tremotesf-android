/*
 * Copyright (C) 2017 Alexey Rochev <equeim@gmail.com>
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

import android.app.Activity

import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.res.Configuration

import android.os.Bundle

import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup

import android.widget.AdapterView
import android.widget.Spinner
import android.widget.TextView

import android.support.v4.widget.DrawerLayout

import android.support.v7.app.ActionBarDrawerToggle

import android.support.v7.widget.DefaultItemAnimator
import android.support.v7.widget.DividerItemDecoration
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.support.v7.widget.SearchView
import android.support.v7.widget.Toolbar

import android.support.design.widget.NavigationView
import android.support.design.widget.Snackbar

import org.equeim.tremotesf.AddTorrentFileActivity
import org.equeim.tremotesf.AddTorrentLinkActivity
import org.equeim.tremotesf.BaseActivity
import org.equeim.tremotesf.FilePickerActivity
import org.equeim.tremotesf.R
import org.equeim.tremotesf.Rpc
import org.equeim.tremotesf.Servers
import org.equeim.tremotesf.Settings
import org.equeim.tremotesf.SettingsActivity

import org.equeim.tremotesf.serversactivity.ServerEditActivity
import org.equeim.tremotesf.serversactivity.ServersActivity

import org.equeim.tremotesf.serversettingsactivity.ServerSettingsActivity

import org.equeim.tremotesf.utils.ArraySpinnerAdapter
import org.equeim.tremotesf.utils.Logger
import org.equeim.tremotesf.utils.Utils
import org.equeim.tremotesf.utils.setChildrenEnabled


private const val SEARCH_QUERY_KEY = "org.equeim.tremotesf.MainActivity.searchQuery"

class MainActivity : BaseActivity() {
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var drawerToggle: ActionBarDrawerToggle

    private var menu: Menu? = null
    private lateinit var searchMenuItem: MenuItem
    private lateinit var searchView: SearchView
    private var restoredSearchQuery: String? = null

    private lateinit var serversSpinnerLayout: ViewGroup
    private lateinit var serversSpinnerAdapter: ServersSpinnerAdapter

    private lateinit var listSettingsLayout: ViewGroup
    private lateinit var sortSpinner: Spinner
    private lateinit var statusSpinner: Spinner
    private lateinit var statusSpinnerAdapter: StatusFilterSpinnerAdapter
    private lateinit var trackersSpinnerAdapter: TrackersSpinnerAdapter

    private lateinit var progressBar: View
    private lateinit var placeholder: TextView
    lateinit var torrentsView: RecyclerView
        private set
    lateinit var torrentsAdapter: TorrentsAdapter
        private set

    private val rpcStatusListener = { status: Rpc.Status ->
        if (status == Rpc.Status.Disconnected || status == Rpc.Status.Connected) {
            if (!Rpc.connected) {
                torrentsAdapter.selector.actionMode?.finish()

                fragmentManager.findFragmentByTag(TorrentsAdapter.SetLocationDialogFragment.TAG)
                        ?.let { fragment ->
                            fragmentManager.beginTransaction().remove(fragment).commit()
                        }

                fragmentManager.findFragmentByTag(TorrentsAdapter.RemoveDialogFragment.TAG)
                        ?.let { fragment ->
                            fragmentManager.beginTransaction().remove(fragment).commit()
                        }

                sortSpinner.setSelection(0)
                statusSpinner.setSelection(StatusFilterSpinnerAdapter.ALL)

                if (menu != null) {
                    searchMenuItem.collapseActionView()
                }
            }

            torrentsAdapter.update()

            updateSubtitle()
            listSettingsLayout.setChildrenEnabled(Rpc.connected)
            statusSpinnerAdapter.update()
            trackersSpinnerAdapter.update()
        }

        updateMenuItems()
        updatePlaceholder()
    }

    private val rpcErrorListener = { error: Rpc.Error ->
        updateTitle()
        updateMenuItems()
        serversSpinnerLayout.setChildrenEnabled(Servers.hasServers)
        updatePlaceholder()
    }

    private val rpcUpdatedListener = {
        updateSubtitle()
        statusSpinnerAdapter.update()
        trackersSpinnerAdapter.update()
        torrentsAdapter.update()
        updatePlaceholder()
    }

    private val serversListener = { serversSpinnerAdapter.update() }
    private val currentServerListener = { updateTitle() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Logger.d("MainActivity onCreate")

        setTheme(Settings.themeNoActionBar)

        setContentView(R.layout.main_activity)
        setPreLollipopShadow()

        setSupportActionBar(findViewById(R.id.toolbar) as Toolbar)
        supportActionBar!!.setDisplayHomeAsUpEnabled(true)
        supportActionBar!!.setHomeButtonEnabled(true)

        drawerLayout = findViewById(R.id.drawer_layout) as DrawerLayout
        drawerLayout.setDrawerShadow(R.drawable.drawer_shadow, Gravity.START)
        drawerToggle = ActionBarDrawerToggle(this,
                                             drawerLayout,
                                             R.string.open_side_panel,
                                             R.string.close_side_panel)

        progressBar = findViewById(R.id.progress_bar)
        placeholder = findViewById(R.id.placeholder) as TextView

        torrentsView = findViewById(R.id.torrents_view) as RecyclerView
        torrentsAdapter = TorrentsAdapter(this)

        torrentsView.adapter = torrentsAdapter
        torrentsView.layoutManager = LinearLayoutManager(this)
        torrentsView.addItemDecoration(DividerItemDecoration(this, DividerItemDecoration.VERTICAL))
        (torrentsView.itemAnimator as DefaultItemAnimator).supportsChangeAnimations = false

        val sidePanel = findViewById(R.id.side_panel) as NavigationView
        sidePanel.setNavigationItemSelectedListener { menuItem ->
            drawerLayout.closeDrawers()
            when (menuItem.itemId) {
                R.id.settings -> startActivity(Intent(application, SettingsActivity::class.java))
                R.id.servers -> startActivity(Intent(application, ServersActivity::class.java))
                R.id.quit -> Utils.shutdownApp(this)
                else -> return@setNavigationItemSelectedListener false
            }
            return@setNavigationItemSelectedListener true
        }

        val sidePanelHeader = sidePanel.getHeaderView(0)

        serversSpinnerLayout = sidePanelHeader.findViewById(R.id.servers_spinner_layout) as ViewGroup
        serversSpinnerLayout.setChildrenEnabled(Servers.hasServers)
        val serversSpinner = sidePanelHeader.findViewById(R.id.servers_spinner) as Spinner
        serversSpinnerAdapter = ServersSpinnerAdapter(serversSpinner)
        serversSpinner.adapter = serversSpinnerAdapter
        serversSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>,
                                        view: View?,
                                        position: Int,
                                        id: Long) {
                Servers.currentServer = serversSpinnerAdapter.servers[position]
                updateTitle()
            }

            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        listSettingsLayout = sidePanelHeader.findViewById(R.id.list_settings_layout) as ViewGroup
        listSettingsLayout.setChildrenEnabled(Rpc.connected)

        sortSpinner = sidePanelHeader.findViewById(R.id.sort_spinner) as Spinner
        sortSpinner.adapter = ArraySpinnerAdapter(this,
                                                  resources.getStringArray(R.array.sort_spinner_items))
        sortSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?,
                                        view: View?,
                                        position: Int,
                                        id: Long) {
                torrentsAdapter.sortMode = when (position) {
                    0 -> TorrentsAdapter.SortMode.Name
                    1 -> TorrentsAdapter.SortMode.Status
                    2 -> TorrentsAdapter.SortMode.Progress
                    3 -> TorrentsAdapter.SortMode.Eta
                    4 -> TorrentsAdapter.SortMode.Ratio
                    5 -> TorrentsAdapter.SortMode.Size
                    6 -> TorrentsAdapter.SortMode.AddedDate
                    else -> TorrentsAdapter.SortMode.Name
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        statusSpinner = sidePanelHeader.findViewById(R.id.status_spinner) as Spinner
        statusSpinnerAdapter = StatusFilterSpinnerAdapter(this)
        statusSpinner.adapter = statusSpinnerAdapter
        statusSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>,
                                        view: View?,
                                        position: Int,
                                        id: Long) {
                torrentsAdapter.statusFilterMode = when (position) {
                    StatusFilterSpinnerAdapter.ALL -> TorrentsAdapter.StatusFilterMode.All
                    StatusFilterSpinnerAdapter.ACTIVE -> TorrentsAdapter.StatusFilterMode.Active
                    StatusFilterSpinnerAdapter.DOWNLOADING -> TorrentsAdapter.StatusFilterMode.Downloading
                    StatusFilterSpinnerAdapter.SEEDING -> TorrentsAdapter.StatusFilterMode.Seeding
                    StatusFilterSpinnerAdapter.PAUSED -> TorrentsAdapter.StatusFilterMode.Paused
                    StatusFilterSpinnerAdapter.CHECKING -> TorrentsAdapter.StatusFilterMode.Checking
                    StatusFilterSpinnerAdapter.ERRORED -> TorrentsAdapter.StatusFilterMode.Errored
                    else -> TorrentsAdapter.StatusFilterMode.All
                }
                updatePlaceholder()
            }

            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        val trackersSpinner = sidePanelHeader.findViewById(R.id.trackers_spinner) as Spinner
        trackersSpinnerAdapter = TrackersSpinnerAdapter(this)
        trackersSpinner.adapter = trackersSpinnerAdapter
        trackersSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>,
                                        view: View?,
                                        position: Int,
                                        id: Long) {
                torrentsAdapter.trackerFilter = if (position == 0) {
                    String()
                } else {
                    trackersSpinnerAdapter.trackers[position - 1]
                }
                updatePlaceholder()
            }

            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        updateTitle()

        Rpc.addStatusListener(rpcStatusListener)
        Rpc.addErrorListener(rpcErrorListener)
        Servers.addServersListener(serversListener)
        Servers.addCurrentServerListener(currentServerListener)

        Rpc.torrentDuplicateListener = {
            Snackbar.make(findViewById(R.id.coordinator_layout),
                          R.string.torrent_duplicate,
                          Snackbar.LENGTH_LONG).show()
        }
        Rpc.torrentAddErrorListener = {
            Snackbar.make(findViewById(R.id.coordinator_layout),
                          R.string.torrent_add_error,
                          Snackbar.LENGTH_LONG).show()
        }

        if (savedInstanceState == null) {
            if (!Servers.hasServers) {
                startActivity(Intent(this, ServerEditActivity::class.java))
            }
        } else if (Rpc.connected) {
            torrentsAdapter.update()
            torrentsAdapter.selector.restoreInstanceState(savedInstanceState)
            restoredSearchQuery = savedInstanceState.getString(SEARCH_QUERY_KEY)
        }
    }

    override fun onStart() {
        super.onStart()
        rpcUpdatedListener()
        Rpc.addUpdatedListener(rpcUpdatedListener)
    }

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)
        drawerToggle.syncState()
    }

    override fun onStop() {
        super.onStop()
        Rpc.removeUpdatedListener(rpcUpdatedListener)
    }

    override fun onDestroy() {
        super.onDestroy()
        Logger.d("MainActivity onDestroy")
        Rpc.removeStatusListener(rpcStatusListener)
        Rpc.removeErrorListener(rpcErrorListener)
        Rpc.torrentDuplicateListener = null
        Rpc.torrentAddErrorListener = null
        Servers.removeServersListener(serversListener)
        Servers.removeCurrentServerListener(currentServerListener)
    }

    override fun onConfigurationChanged(newConfig: Configuration?) {
        super.onConfigurationChanged(newConfig)
        drawerToggle.onConfigurationChanged(newConfig)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
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

        return true
    }

    override fun onOptionsItemSelected(menuItem: MenuItem): Boolean {
        if (drawerToggle.onOptionsItemSelected(menuItem)) {
            return true
        }

        when (menuItem.itemId) {
            R.id.connect -> {
                if (Rpc.status == Rpc.Status.Disconnected) {
                    Rpc.connect()
                } else {
                    Rpc.disconnect()
                }
            }
            R.id.add_torrent_file -> startFilePickerActivity()
            R.id.add_torrent_link -> startActivity(Intent(this, AddTorrentLinkActivity::class.java))
            R.id.server_settings -> startActivity(Intent(this, ServerSettingsActivity::class.java))
            R.id.server_stats -> ServerStatsDialogFragment().show(fragmentManager, null)
            else -> return false
        }

        return true
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (resultCode == Activity.RESULT_OK && data != null) {
            val intent = Intent(this, AddTorrentFileActivity::class.java)
            intent.data = data.data
            startActivity(intent)
        }
    }

    private fun updateTitle() {
        if (Servers.hasServers) {
            val currentServer = Servers.currentServer!!
            title = getString(R.string.current_server_string,
                              currentServer.name,
                              currentServer.address)
        } else {
            title = getString(R.string.app_name)
        }
    }

    private fun updateMenuItems() {
        if (menu == null) {
            return
        }

        val connectMenuItem = menu!!.findItem(R.id.connect)
        connectMenuItem.isEnabled = Rpc.canConnect
        connectMenuItem.title = when (Rpc.status) {
            Rpc.Status.Disconnected -> getString(R.string.connect)
            Rpc.Status.Connecting,
            Rpc.Status.Connected -> getString(R.string.disconnect)
        }

        val connected = Rpc.connected
        searchMenuItem.isVisible = connected
        menu!!.findItem(R.id.add_torrent_file).isEnabled = connected
        menu!!.findItem(R.id.add_torrent_link).isEnabled = connected
        menu!!.findItem(R.id.server_settings).isEnabled = connected
        menu!!.findItem(R.id.server_stats).isEnabled = connected
    }

    private fun updatePlaceholder() {
        placeholder.text = if (Rpc.status == Rpc.Status.Connected) {
            if (torrentsAdapter.itemCount == 0) {
                getString(R.string.no_torrents)
            } else {
                null
            }
        } else {
            Rpc.statusString
        }

        progressBar.visibility = if (Rpc.status == Rpc.Status.Connecting) {
            View.VISIBLE
        } else {
            View.GONE
        }
    }

    private fun updateSubtitle() {
        supportActionBar!!.subtitle = if (Rpc.connected) {
            getString(R.string.main_activity_subtitle,
                      Utils.formatByteSpeed(this, Rpc.serverStats.downloadSpeed),
                      Utils.formatByteSpeed(this, Rpc.serverStats.uploadSpeed))
        } else {
            null
        }
    }

    private fun startFilePickerActivity() {
        try {
            val intent = Intent(Intent.ACTION_GET_CONTENT)
            intent.addCategory(Intent.CATEGORY_OPENABLE)
            intent.type = "application/x-bittorrent"
            startActivityForResult(intent, 0)
        } catch (error: ActivityNotFoundException) {
            startActivityForResult(Intent(this, FilePickerActivity::class.java), 0)
        }
    }

    override fun onBackPressed() {
        if (drawerLayout.isDrawerOpen(Gravity.START)) {
            drawerLayout.closeDrawer(Gravity.START)
        } else {
            super.onBackPressed()
        }
    }
}