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
import android.widget.ImageButton
import android.widget.Spinner

import android.support.v7.app.ActionBarDrawerToggle

import android.support.v7.widget.DefaultItemAnimator
import android.support.v7.widget.DividerItemDecoration
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.SearchView
import android.support.v7.widget.Toolbar

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

import org.equeim.tremotesf.utils.ArraySpinnerAdapterWithHeader
import org.equeim.tremotesf.utils.Logger
import org.equeim.tremotesf.utils.Utils
import org.equeim.tremotesf.utils.setChildrenEnabled

import kotlinx.android.synthetic.main.main_activity.*
import org.equeim.tremotesf.AboutActivity


private const val SEARCH_QUERY_KEY = "org.equeim.tremotesf.MainActivity.searchQuery"

class MainActivity : BaseActivity() {
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
    private lateinit var trackersSpinnerAdapter: TrackersSpinnerAdapter

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
                statusSpinner.setSelection(0)

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
        serversSpinner.isEnabled = Servers.hasServers
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

        setSupportActionBar(toolbar as Toolbar)
        supportActionBar!!.setDisplayHomeAsUpEnabled(true)
        supportActionBar!!.setHomeButtonEnabled(true)

        drawer_layout.setDrawerShadow(R.drawable.drawer_shadow, Gravity.START)
        drawerToggle = ActionBarDrawerToggle(this,
                                             drawer_layout,
                                             R.string.open_side_panel,
                                             R.string.close_side_panel)

        torrentsAdapter = TorrentsAdapter(this)

        torrents_view.adapter = torrentsAdapter
        torrents_view.layoutManager = LinearLayoutManager(this)
        torrents_view.addItemDecoration(DividerItemDecoration(this, DividerItemDecoration.VERTICAL))
        (torrents_view.itemAnimator as DefaultItemAnimator).supportsChangeAnimations = false

        side_panel.setNavigationItemSelectedListener { menuItem ->
            drawer_layout.closeDrawers()
            when (menuItem.itemId) {
                R.id.settings -> startActivity(Intent(this, SettingsActivity::class.java))
                R.id.servers -> startActivity(Intent(this, ServersActivity::class.java))
                R.id.about -> startActivity(Intent(this, AboutActivity::class.java))
                R.id.quit -> Utils.shutdownApp(this)
                else -> return@setNavigationItemSelectedListener false
            }
            return@setNavigationItemSelectedListener true
        }

        val sidePanelHeader = side_panel.getHeaderView(0)

        serversSpinner = sidePanelHeader.findViewById(R.id.servers_spinner) as Spinner
        serversSpinner.isEnabled = Servers.hasServers
        serversSpinnerAdapter = ServersSpinnerAdapter(serversSpinner)
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

        listSettingsLayout = sidePanelHeader.findViewById(R.id.list_settings_layout) as ViewGroup
        listSettingsLayout.setChildrenEnabled(Rpc.connected)

        sortSpinner = sidePanelHeader.findViewById(R.id.sort_spinner) as Spinner
        sortSpinner.adapter = ArraySpinnerAdapterWithHeader(resources.getStringArray(R.array.sort_spinner_items),
                                                            R.string.sort)
        sortSpinner.setSelection(Settings.torrentsSortMode.ordinal)
        sortSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?,
                                        view: View?,
                                        position: Int,
                                        id: Long) {
                torrentsAdapter.sortMode = TorrentsAdapter.SortMode.values()[position]
                if (Rpc.connected) {
                    Settings.torrentsSortMode = torrentsAdapter.sortMode
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>) {}
        }
        val sortOrderButton = sidePanelHeader.findViewById(R.id.sort_order_button) as ImageButton
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

        statusSpinner = sidePanelHeader.findViewById(R.id.status_spinner) as Spinner
        statusSpinnerAdapter = StatusFilterSpinnerAdapter(this)
        statusSpinner.adapter = statusSpinnerAdapter
        statusSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            private var previousPosition = -1
            override fun onItemSelected(parent: AdapterView<*>,
                                        view: View?,
                                        position: Int,
                                        id: Long) {
                if (previousPosition != -1) {
                    torrentsAdapter.statusFilterMode = TorrentsAdapter.StatusFilterMode.values()[position]
                    updatePlaceholder()
                }
                previousPosition = position
            }

            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        val trackersSpinner = sidePanelHeader.findViewById(R.id.trackers_spinner) as Spinner
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
                        String()
                    } else {
                        trackersSpinnerAdapter.trackers[position - 1]
                    }
                    updatePlaceholder()
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

        Rpc.torrentDuplicateListener = {
            Snackbar.make(coordinator_layout,
                          R.string.torrent_duplicate,
                          Snackbar.LENGTH_LONG).show()
        }
        Rpc.torrentAddErrorListener = {
            Snackbar.make(coordinator_layout,
                          R.string.torrent_add_error,
                          Snackbar.LENGTH_LONG).show()
        }

        if (savedInstanceState == null) {
            if (!Servers.hasServers) {
                startActivity(Intent(this, ServerEditActivity::class.java))
            }
        } else if (Rpc.connected) {
            restoredSearchQuery = savedInstanceState.getString(SEARCH_QUERY_KEY)
            if (restoredSearchQuery != null) {
                torrentsAdapter.filterString = restoredSearchQuery!!
            }
            torrentsAdapter.restoreInstanceState(savedInstanceState)
            torrentsAdapter.update()
            torrentsAdapter.selector.restoreInstanceState(savedInstanceState)
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

        progress_bar.visibility = if (Rpc.status == Rpc.Status.Connecting) {
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
            val intent = Intent(Intent.ACTION_GET_CONTENT)
            intent.addCategory(Intent.CATEGORY_OPENABLE)
            intent.type = "application/x-bittorrent"
            startActivityForResult(intent, 0)
        } catch (error: ActivityNotFoundException) {
            startActivityForResult(Intent(this, FilePickerActivity::class.java), 0)
        }
    }

    override fun onBackPressed() {
        if (drawer_layout.isDrawerOpen(Gravity.START)) {
            drawer_layout.closeDrawer(Gravity.START)
        } else {
            super.onBackPressed()
        }
    }
}