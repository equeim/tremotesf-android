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

package org.equeim.tremotesf.torrentpropertiesactivity

import android.content.Context

import android.os.Bundle

import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager

import android.support.v4.view.ViewPager
import android.support.v7.view.ActionMode
import android.support.v7.widget.Toolbar

import android.support.design.widget.AppBarLayout
import android.support.design.widget.Snackbar
import android.support.v4.app.Fragment
import android.support.v4.app.FragmentPagerAdapter

import org.jetbrains.anko.contentView
import org.jetbrains.anko.design.indefiniteSnackbar

import org.equeim.libtremotesf.BaseRpc
import org.equeim.libtremotesf.Torrent
import org.equeim.tremotesf.BaseActivity
import org.equeim.tremotesf.R
import org.equeim.tremotesf.Rpc
import org.equeim.tremotesf.Settings
import org.equeim.tremotesf.mainactivity.TorrentsAdapter

import kotlinx.android.synthetic.main.torrent_properties_activity.*


private const val TAB_DETAILS = 0
private const val TAB_FILES = 1
private const val TAB_TRACKERS = 2
private const val TAB_PEERS = 3
private const val TAB_LIMITS = 4
private const val TABS_COUNT = 5

class TorrentPropertiesActivity : BaseActivity() {
    companion object {
        const val HASH = "org.equeim.TorrentPropertiesActivity.HASH"
        const val NAME = "org.equeim.TorrentPropertiesActivity.NAME"
    }

    lateinit var hash: String

    var torrent: Torrent? = null
        set(value) {
            var needUpdate = (active || creating)
            if (value !== field) {
                field = value
                if (value == null) {
                    needUpdate = true
                    if (Rpc.instance.isConnected) {
                        placeholder.text = getString(R.string.torrent_removed)
                    }

                    supportFragmentManager.findFragmentByTag(TorrentsAdapter.SetLocationDialogFragment.TAG)
                            ?.let { fragment ->
                                supportFragmentManager.beginTransaction().remove(fragment).commit()
                    }

                    supportFragmentManager.findFragmentByTag(TorrentsAdapter.RemoveDialogFragment.TAG)?.let { fragment ->
                        supportFragmentManager.beginTransaction().remove(fragment).commit()
                    }

                    supportFragmentManager.findFragmentByTag(TorrentFilesAdapter.RenameDialogFragment.TAG)
                            ?.let { fragment ->
                                supportFragmentManager.beginTransaction().remove(fragment).commit()
                            }

                    supportFragmentManager.findFragmentByTag(TrackersAdapter.EditTrackerDialogFragment.TAG)
                            ?.let { fragment ->
                                supportFragmentManager.beginTransaction().remove(fragment).commit()
                    }

                    supportFragmentManager.findFragmentByTag(TrackersAdapter.RemoveDialogFragment.TAG)
                            ?.let { fragment ->
                                supportFragmentManager.beginTransaction().remove(fragment).commit()
                    }
                }
                updatePlaceholderVisibility()
            }
            if (needUpdate) {
                update()
            }
        }

    private val rpcStatusListener: (Int) -> Unit = { status ->
        when (status) {
            BaseRpc.Status.Disconnected -> {
                torrent = null
                snackbar = indefiniteSnackbar(contentView!!, "", getString(R.string.connect)) {
                    snackbar = null
                    Rpc.instance.connect()
                }
                placeholder.text = Rpc.instance.statusString
            }
            BaseRpc.Status.Connecting -> {
                if (snackbar != null) {
                    snackbar!!.dismiss()
                    snackbar = null
                }
                placeholder.text = getString(R.string.connecting)
            }
            BaseRpc.Status.Connected -> {
                torrent = Rpc.instance.torrents.find { it.hashString == hash }?.torrent
                if (torrent == null) {
                    placeholder.text = getString(R.string.torrent_not_found)
                }
            }
        }

        progress_bar.visibility = if (status == BaseRpc.Status.Connecting) {
            View.VISIBLE
        } else {
            View.GONE
        }
    }

    private val torrentsUpdatedListener = {
        torrent = Rpc.instance.torrents.find { it.hashString == hash }?.torrent
    }

    private var menu: Menu? = null
    private lateinit var startMenuItem: MenuItem
    private lateinit var pauseMenuItem: MenuItem

    private var snackbar: Snackbar? = null

    private lateinit var pagerAdapter: PagerAdapter

    var actionMode: ActionMode? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setTheme(Settings.themeNoActionBar)
        setContentView(R.layout.torrent_properties_activity)
        setPreLollipopShadow()

        hash = intent.getStringExtra(HASH)

        setSupportActionBar(toolbar as Toolbar)
        supportActionBar!!.setDisplayHomeAsUpEnabled(true)

        title = intent.getStringExtra(NAME)

        pager.addOnPageChangeListener(object : ViewPager.SimpleOnPageChangeListener() {
            private var previousPage = -1
            private val inputManager = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager

            override fun onPageSelected(position: Int) {
                if (previousPage != -1) {
                    actionMode?.finish()
                    currentFocus?.let { inputManager.hideSoftInputFromWindow(it.windowToken, 0) }
                }
                if (position == TAB_TRACKERS) {
                    fab.show()
                } else {
                    fab.hide()
                }
                previousPage = position
            }
        })

        pagerAdapter = PagerAdapter()
        pager.adapter = pagerAdapter
        tab_layout.setupWithViewPager(pager)

        fab.setOnClickListener {
            if (supportFragmentManager.findFragmentByTag(TrackersAdapter.EditTrackerDialogFragment.TAG) == null) {
                val fragment = TrackersAdapter.EditTrackerDialogFragment()
                fragment.show(supportFragmentManager, TrackersAdapter.EditTrackerDialogFragment.TAG)
            }
        }

        rpcStatusListener(Rpc.instance.status())
        if (!Rpc.instance.isConnected) {
            updatePlaceholderVisibility()
        }

        Rpc.instance.addStatusListener(rpcStatusListener)
        Rpc.instance.addTorrentsUpdatedListener(torrentsUpdatedListener)
    }

    override fun onStart() {
        super.onStart()
        if (!creating) {
            torrentsUpdatedListener()
        }
    }

    override fun onStop() {
        super.onStop()
        Rpc.instance.removeTorrentsUpdatedListener(torrentsUpdatedListener)
    }

    override fun onDestroy() {
        super.onDestroy()

        Rpc.instance.removeStatusListener(rpcStatusListener)
        Rpc.instance.removeTorrentsUpdatedListener(torrentsUpdatedListener)

        if (isFinishing && torrent != null) {
            Rpc.instance.setTorrentFilesEnabled(torrent, false)
            Rpc.instance.setTorrentPeersEnabled(torrent, false)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        this.menu = menu
        menuInflater.inflate(R.menu.torrent_properties_activity_menu, menu)
        startMenuItem = menu.findItem(R.id.start)
        pauseMenuItem = menu.findItem(R.id.pause)
        updateMenu()
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        torrent?.let { torrent ->
            when (item.itemId) {
                R.id.start -> Rpc.instance.startTorrents(intArrayOf(torrent.id()))
                R.id.pause -> Rpc.instance.pauseTorrents(intArrayOf(torrent.id()))
                R.id.check -> Rpc.instance.checkTorrents(intArrayOf(torrent.id()))
                R.id.reannounce -> Rpc.instance.reannounceTorrents(intArrayOf(torrent.id()))
                R.id.set_location -> TorrentsAdapter.SetLocationDialogFragment.create(intArrayOf(torrent.id()),
                                                                                      torrent.downloadDirectory())
                        .show(supportFragmentManager, TorrentsAdapter.SetLocationDialogFragment.TAG)
                R.id.remove -> TorrentsAdapter.RemoveDialogFragment.create(intArrayOf(torrent.id())).show(supportFragmentManager,
                                                                                                          TorrentsAdapter.RemoveDialogFragment.TAG)
                else -> return false
            }
        }
        return true
    }

    override fun onSupportActionModeStarted(mode: ActionMode) {
        super.onSupportActionModeStarted(mode)
        actionMode = mode
    }

    override fun onSupportActionModeFinished(mode: ActionMode) {
        super.onSupportActionModeFinished(mode)
        actionMode = null
    }

    override fun onBackPressed() {
        if (!(pager.currentItem == TAB_FILES &&
                pagerAdapter.filesFragment != null &&
                pagerAdapter.filesFragment!!.onBackPressed())) {
            super.onBackPressed()
        }
    }

    private fun update() {
        if (torrent != null) {
            title = torrent!!.name()
        }

        if (menu != null) {
            updateMenu()
        }

        pagerAdapter.detailsFragment?.update()
        pagerAdapter.filesFragment?.update()
        pagerAdapter.trackersFragment?.update()
        pagerAdapter.peersFragment?.update()
    }

    private fun updateMenu() {
        for (i in 0 until (menu!!.size() - 1)) {
            menu!!.getItem(i).isVisible = (torrent != null)
        }
        if (torrent != null) {
            startMenuItem.isVisible = when (torrent!!.status()) {
                Torrent.Status.Paused,
                Torrent.Status.Errored -> true
                else -> false
            }
            pauseMenuItem.isVisible = !startMenuItem.isVisible
        }
    }

    private fun updatePlaceholderVisibility() {
        if (Rpc.instance.isConnected && torrent != null) {
            (toolbar.layoutParams as AppBarLayout.LayoutParams).scrollFlags =
                    AppBarLayout.LayoutParams.SCROLL_FLAG_SCROLL or
                            AppBarLayout.LayoutParams.SCROLL_FLAG_SNAP or
                            AppBarLayout.LayoutParams.SCROLL_FLAG_ENTER_ALWAYS
            tab_layout.visibility = View.VISIBLE
            pager.visibility = View.VISIBLE
            placeholder_layout.visibility = View.GONE
        } else {
            (toolbar.layoutParams as AppBarLayout.LayoutParams).scrollFlags = 0
            tab_layout.visibility = View.GONE
            pager.visibility = View.GONE
            pager.currentItem = 0
            placeholder_layout.visibility = View.VISIBLE
        }
    }

    inner class PagerAdapter : FragmentPagerAdapter(supportFragmentManager) {
        var detailsFragment: TorrentDetailsFragment? = null
        var filesFragment: TorrentFilesFragment? = null
        var trackersFragment: TrackersFragment? = null
        var peersFragment: PeersFragment? = null
        private var limitsFragment: TorrentLimitsFragment? = null

        override fun getCount(): Int {
            return TABS_COUNT
        }

        override fun getItem(position: Int): Fragment? {
            return when (position) {
                TAB_DETAILS -> TorrentDetailsFragment()
                TAB_FILES -> TorrentFilesFragment()
                TAB_TRACKERS -> TrackersFragment()
                TAB_PEERS -> PeersFragment()
                TAB_LIMITS -> TorrentLimitsFragment()
                else -> null
            }
        }


        override fun instantiateItem(container: ViewGroup, position: Int): Any {
            val fragment = super.instantiateItem(container, position)
            when (position) {
                TAB_DETAILS -> {
                    detailsFragment = fragment as TorrentDetailsFragment
                }
                TAB_FILES -> {
                    filesFragment = fragment as TorrentFilesFragment
                }
                TAB_TRACKERS -> {
                    trackersFragment = fragment as TrackersFragment
                }
                TAB_PEERS -> {
                    peersFragment = fragment as PeersFragment
                }
                TAB_LIMITS -> {
                    limitsFragment = fragment as TorrentLimitsFragment
                    //limitsFragment!!.update()
                }
            }
            return fragment
        }

        override fun getPageTitle(position: Int): CharSequence {
            return when (position) {
                TAB_DETAILS -> getString(R.string.details)
                TAB_FILES -> getString(R.string.files)
                TAB_TRACKERS -> getString(R.string.trackers)
                TAB_PEERS -> getString(R.string.peers)
                TAB_LIMITS -> getString(R.string.limits)
                else -> ""
            }
        }
    }
}