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

package org.equeim.tremotesf.torrentpropertiesactivity

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager

import androidx.viewpager.widget.ViewPager
import androidx.appcompat.view.ActionMode
import androidx.appcompat.widget.Toolbar
import androidx.core.content.getSystemService
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentPagerAdapter
import androidx.fragment.app.commit

import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.snackbar.Snackbar

import org.jetbrains.anko.contentView
import org.jetbrains.anko.design.indefiniteSnackbar

import org.equeim.libtremotesf.Torrent
import org.equeim.tremotesf.BaseActivity
import org.equeim.tremotesf.R
import org.equeim.tremotesf.Rpc
import org.equeim.tremotesf.RpcStatus
import org.equeim.tremotesf.Selector
import org.equeim.tremotesf.TorrentData
import org.equeim.tremotesf.mainactivity.TorrentsAdapter

import org.equeim.tremotesf.setFilesEnabled
import org.equeim.tremotesf.setPeersEnabled

import kotlinx.android.synthetic.main.torrent_properties_activity.*


private const val TAB_DETAILS = 0
private const val TAB_FILES = 1
private const val TAB_TRACKERS = 2
private const val TAB_PEERS = 3
private const val TAB_LIMITS = 4
private const val TABS_COUNT = 5

class TorrentPropertiesActivity : BaseActivity(R.layout.torrent_properties_activity, true), Selector.ActionModeActivity {
    companion object {
        const val HASH = "org.equeim.TorrentPropertiesActivity.HASH"
        const val NAME = "org.equeim.TorrentPropertiesActivity.NAME"
    }

    lateinit var hash: String

    var torrent: TorrentData? = null
        set(value) {
            var needUpdate = (active || creating)
            if (value !== field) {
                field = value
                if (value == null) {
                    needUpdate = true
                    if (Rpc.isConnected) {
                        placeholder.text = getString(R.string.torrent_removed)
                    }

                    supportFragmentManager.apply {
                        val findAndRemove = { tag: String ->
                            findFragmentByTag(tag)?.let { commit { remove(it) } }
                        }
                        findAndRemove(TorrentsAdapter.SetLocationDialogFragment.TAG)
                        findAndRemove(TorrentsAdapter.RemoveDialogFragment.TAG)
                        findAndRemove(TorrentFilesAdapter.RenameDialogFragment.TAG)
                        findAndRemove(TrackersAdapter.EditTrackerDialogFragment.TAG)
                        findAndRemove(TrackersAdapter.RemoveDialogFragment.TAG)
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
            RpcStatus.Disconnected -> {
                torrent = null
                snackbar = contentView?.indefiniteSnackbar("", getString(R.string.connect)) {
                    snackbar = null
                    Rpc.nativeInstance.connect()
                }
                placeholder.text = Rpc.statusString
            }
            RpcStatus.Connecting -> {
                snackbar?.dismiss()
                snackbar = null
                placeholder.text = getString(R.string.connecting)
            }
            RpcStatus.Connected -> {
                torrent = Rpc.torrents.find { it.hashString == hash }
                if (torrent == null) {
                    placeholder.text = getString(R.string.torrent_not_found)
                }
            }
        }

        progress_bar.visibility = if (status == RpcStatus.Connecting) {
            View.VISIBLE
        } else {
            View.GONE
        }
    }

    private val torrentsUpdatedListener = {
        torrent = Rpc.torrents.find { it.hashString == hash }
    }

    private var menu: Menu? = null
    private lateinit var startMenuItem: MenuItem
    private lateinit var pauseMenuItem: MenuItem

    private var snackbar: Snackbar? = null

    private lateinit var pagerAdapter: PagerAdapter

    override var actionMode: ActionMode? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        hash = intent.getStringExtra(HASH)!!

        setSupportActionBar(toolbar as Toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        title = intent.getStringExtra(NAME)

        pager.addOnPageChangeListener(object : ViewPager.SimpleOnPageChangeListener() {
            private var previousPage = -1
            private val inputManager = getSystemService<InputMethodManager>()!!

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

        rpcStatusListener(Rpc.status)
        if (!Rpc.isConnected) {
            updatePlaceholderVisibility()
        }

        Rpc.addStatusListener(rpcStatusListener)
        Rpc.addTorrentsUpdatedListener(torrentsUpdatedListener)
    }

    override fun onStart() {
        super.onStart()
        if (!creating) {
            torrentsUpdatedListener()
        }
    }

    override fun onStop() {
        super.onStop()
        Rpc.removeTorrentsUpdatedListener(torrentsUpdatedListener)
    }

    override fun onDestroy() {
        Rpc.removeStatusListener(rpcStatusListener)
        Rpc.removeTorrentsUpdatedListener(torrentsUpdatedListener)

        if (isFinishing && torrent != null) {
            torrent?.torrent?.apply {
                setFilesEnabled(false)
                setPeersEnabled(false)
            }
        }

        super.onDestroy()
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
                R.id.start -> Rpc.nativeInstance.startTorrents(intArrayOf(torrent.id))
                R.id.pause -> Rpc.nativeInstance.pauseTorrents(intArrayOf(torrent.id))
                R.id.check -> Rpc.nativeInstance.checkTorrents(intArrayOf(torrent.id))
                R.id.reannounce -> Rpc.nativeInstance.reannounceTorrents(intArrayOf(torrent.id))
                R.id.set_location -> TorrentsAdapter.SetLocationDialogFragment.create(intArrayOf(torrent.id),
                                                                                      torrent.downloadDirectory)
                        .show(supportFragmentManager, TorrentsAdapter.SetLocationDialogFragment.TAG)
                R.id.rename -> TorrentFilesAdapter.RenameDialogFragment.create(torrent.id,
                                                                               torrent.name,
                                                                               torrent.name)
                        .show(supportFragmentManager, TorrentFilesAdapter.RenameDialogFragment.TAG)
                R.id.remove -> TorrentsAdapter.RemoveDialogFragment.create(intArrayOf(torrent.id)).show(supportFragmentManager,
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
                pagerAdapter.filesFragment?.onBackPressed() == true)) {
            super.onBackPressed()
        }
    }

    private fun update() {
        torrent?.let { torrent ->
            title = torrent.name
        }

        updateMenu()

        pagerAdapter.detailsFragment?.update()
        pagerAdapter.filesFragment?.update()
        pagerAdapter.trackersFragment?.update()
        pagerAdapter.peersFragment?.update()
    }

    private fun updateMenu() {
        val menu = this.menu ?: return

        for (i in 0 until menu.size()) {
            menu.getItem(i).isVisible = (torrent != null)
        }
        torrent?.let { torrent ->
            startMenuItem.isVisible = when (torrent.status) {
                Torrent.Status.Paused,
                Torrent.Status.Errored -> true
                else -> false
            }
            pauseMenuItem.isVisible = !startMenuItem.isVisible
        }
    }

    private fun updatePlaceholderVisibility() {
        if (Rpc.isConnected && torrent != null) {
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

    inner class PagerAdapter : FragmentPagerAdapter(supportFragmentManager, BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT) {
        var detailsFragment: TorrentDetailsFragment? = null
        var filesFragment: TorrentFilesFragment? = null
        var trackersFragment: TrackersFragment? = null
        var peersFragment: PeersFragment? = null
        private var limitsFragment: TorrentLimitsFragment? = null

        override fun getCount(): Int {
            return TABS_COUNT
        }

        override fun getItem(position: Int): Fragment {
            return when (position) {
                TAB_DETAILS -> TorrentDetailsFragment()
                TAB_FILES -> TorrentFilesFragment()
                TAB_TRACKERS -> TrackersFragment()
                TAB_PEERS -> PeersFragment()
                TAB_LIMITS -> TorrentLimitsFragment()
                else -> Fragment()
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