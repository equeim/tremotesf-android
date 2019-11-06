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

package org.equeim.tremotesf.torrentpropertiesfragment

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View

import androidx.activity.addCallback
import androidx.annotation.StringRes
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.navigation.fragment.DialogFragmentNavigator
import androidx.navigation.fragment.findNavController
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2

import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayoutMediator

import org.jetbrains.anko.design.indefiniteSnackbar

import org.equeim.libtremotesf.Torrent
import org.equeim.tremotesf.R
import org.equeim.tremotesf.Rpc
import org.equeim.tremotesf.RpcStatus
import org.equeim.tremotesf.TorrentData
import org.equeim.tremotesf.NavigationActivity
import org.equeim.tremotesf.NavigationFragment
import org.equeim.tremotesf.setFilesEnabled
import org.equeim.tremotesf.setPeersEnabled
import org.equeim.tremotesf.torrentslistfragment.TorrentsAdapter
import org.equeim.tremotesf.utils.findFragment
import org.equeim.tremotesf.utils.hideKeyboard

import kotlinx.android.synthetic.main.torrent_properties_fragment.*


class TorrentPropertiesFragment : NavigationFragment(R.layout.torrent_properties_fragment,
                                                     0,
                                                     R.menu.torrent_properties_activity_menu) {
    companion object {
        const val HASH = "hash"
        const val NAME = "name"
    }

    private var firstStart = true
    lateinit var hash: String
    var torrent: TorrentData? = null

    private val rpcStatusListener: (Int) -> Unit = { status ->
        when (status) {
            RpcStatus.Disconnected -> {
                updateTorrent(null)
                snackbar = view?.indefiniteSnackbar("", getString(R.string.connect)) {
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
                updateTorrent(findTorrent())
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
        updateTorrent(findTorrent(), true)
    }

    private var menu: Menu? = null
    private var startMenuItem: MenuItem? = null
    private var pauseMenuItem: MenuItem? = null

    private var snackbar: Snackbar? = null

    private var pagerAdapter: PagerAdapter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        hash = requireArguments().getString(HASH)!!
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        toolbar?.title = requireArguments().getString(NAME)
        setupToolbarMenu()

        val pagerAdapter = PagerAdapter(this)
        this.pagerAdapter = pagerAdapter
        pager.adapter = pagerAdapter
        TabLayoutMediator(tab_layout, pager) { tab, position ->
            tab.setText(PagerAdapter.getTitle(position))
        }.attach()

        requireActivity().onBackPressedDispatcher.addCallback(this) {
            if (pager.currentItem != PagerAdapter.Tab.Files.ordinal ||
                    findFragment<TorrentFilesFragment>()?.adapter?.navigateUp() != true) {
                isEnabled = false
                requireActivity().onBackPressedDispatcher.onBackPressed()
            }
        }

        pager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            private var previousPage = -1

            override fun onPageSelected(position: Int) {
                if (previousPage != -1) {
                    (requireActivity() as NavigationActivity).apply {
                        actionMode?.finish()
                        hideKeyboard()
                    }
                }
                if (position == PagerAdapter.Tab.Trackers.ordinal) {
                    fab.show()
                } else {
                    fab.hide()
                }
                previousPage = position
            }
        })

        fab.setOnClickListener {
            findNavController().navigate(R.id.action_torrentPropertiesFragment_to_editTrackerDialogFragment)
        }

        rpcStatusListener(Rpc.status)
        if (!Rpc.isConnected) {
            updatePlaceholderVisibility()
        }

        Rpc.addStatusListener(rpcStatusListener)

        firstStart = true
    }

    override fun onStart() {
        super.onStart()
        if (!firstStart) {
            torrentsUpdatedListener()
        }
        Rpc.addTorrentsUpdatedListener(torrentsUpdatedListener)
        firstStart = false
    }

    override fun onStop() {
        Rpc.removeTorrentsUpdatedListener(torrentsUpdatedListener)
        super.onStop()
    }

    override fun onDestroyView() {
        menu = null
        startMenuItem = null
        pauseMenuItem = null
        snackbar = null

        Rpc.removeStatusListener(rpcStatusListener)
        pagerAdapter = null

        if (isRemoving) {
            torrent?.torrent?.apply {
                setFilesEnabled(false)
                setPeersEnabled(false)
            }
        }

        super.onDestroyView()
    }

    private fun setupToolbarMenu() {
        toolbar?.menu?.let { menu ->
            this.menu = menu
            startMenuItem = menu.findItem(R.id.start)
            pauseMenuItem = menu.findItem(R.id.pause)
        }
    }

    override fun onToolbarMenuItemClicked(menuItem: MenuItem): Boolean {
        torrent?.let { torrent ->
            when (menuItem.itemId) {
                R.id.start -> Rpc.nativeInstance.startTorrents(intArrayOf(torrent.id))
                R.id.pause -> Rpc.nativeInstance.pauseTorrents(intArrayOf(torrent.id))
                R.id.check -> Rpc.nativeInstance.checkTorrents(intArrayOf(torrent.id))
                R.id.reannounce -> Rpc.nativeInstance.reannounceTorrents(intArrayOf(torrent.id))
                R.id.set_location -> findNavController().navigate(R.id.action_torrentPropertiesFragment_to_setLocationDialogFragment,
                                                                  bundleOf(TorrentsAdapter.SetLocationDialogFragment.TORRENT_IDS to intArrayOf(torrent.id),
                                                                           TorrentsAdapter.SetLocationDialogFragment.LOCATION to torrent.downloadDirectory))
                R.id.rename ->
                    findNavController().navigate(R.id.action_torrentPropertiesFragment_to_torrentRenameDialogFragment,
                                                 bundleOf(TorrentFilesAdapter.TorrentRenameDialogFragment.TORRENT_ID to torrent.id,
                                                          TorrentFilesAdapter.TorrentRenameDialogFragment.FILE_PATH to torrent.name,
                                                          TorrentFilesAdapter.TorrentRenameDialogFragment.FILE_NAME to torrent.name))
                R.id.remove -> findNavController().navigate(R.id.action_torrentPropertiesFragment_to_removeTorrentDialogFragment,
                                                            bundleOf(TorrentsAdapter.RemoveTorrentDialogFragment.TORRENT_IDS to intArrayOf(torrent.id)))
                else -> return false
            }
        }
        return true
    }

    private fun findTorrent(): TorrentData? {
        return Rpc.torrents.find { it.hashString == hash }
    }

    private fun updateTorrent(newTorrent: TorrentData?, forceUpdateView: Boolean = false) {
        var updateView = forceUpdateView || firstStart || lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        if (newTorrent !== torrent) {
            torrent = newTorrent
            if (newTorrent == null) {
                updateView = true
                if (Rpc.isConnected) {
                    placeholder.text = getString(R.string.torrent_removed)
                }

                val navController = findNavController()
                if (navController.currentDestination is DialogFragmentNavigator.Destination) {
                    navController.popBackStack()
                }
            }
            updatePlaceholderVisibility()
        }
        if (updateView) {
            updateView()
        }
    }

    private fun updateView() {
        torrent?.let { torrent ->
            toolbar?.title = torrent.name
        }

        updateMenu()

        for (fragment in childFragmentManager.fragments) {
            (fragment as? PagerFragment)?.update()
        }
    }

    private fun updateMenu() {
        val menu = this.menu ?: return
        if (torrent == null) {
            toolbar?.hideOverflowMenu()
            menu.setGroupVisible(0, false)
        } else {
            menu.setGroupVisible(0, true)
            val paused = when (torrent?.status) {
                Torrent.Status.Paused,
                Torrent.Status.Errored -> true
                else -> false
            }
            if (paused) {
                pauseMenuItem
            } else {
                startMenuItem
            }?.isVisible = false
        }
    }

    private fun updatePlaceholderVisibility() {
        if (Rpc.isConnected && torrent != null) {
            (toolbar?.layoutParams as AppBarLayout.LayoutParams?)?.scrollFlags =
                    AppBarLayout.LayoutParams.SCROLL_FLAG_SCROLL or
                            AppBarLayout.LayoutParams.SCROLL_FLAG_SNAP or
                            AppBarLayout.LayoutParams.SCROLL_FLAG_ENTER_ALWAYS
            tab_layout.visibility = View.VISIBLE
            pager.visibility = View.VISIBLE
            placeholder_layout.visibility = View.GONE
        } else {
            (toolbar?.layoutParams as AppBarLayout.LayoutParams?)?.scrollFlags = 0
            tab_layout.visibility = View.GONE
            pager.visibility = View.GONE
            pager.currentItem = 0
            placeholder_layout.visibility = View.VISIBLE
        }
    }

    class PagerAdapter(fragment: Fragment) : FragmentStateAdapter(fragment) {
        companion object {
            private val tabs = Tab.values()

            @StringRes
            fun getTitle(position: Int): Int {
                return when (tabs[position]) {
                    Tab.Details -> R.string.details
                    Tab.Files -> R.string.files
                    Tab.Trackers -> R.string.trackers
                    Tab.Peers -> R.string.peers
                    Tab.Limits -> R.string.limits
                }
            }
        }

        enum class Tab {
            Details,
            Files,
            Trackers,
            Peers,
            Limits
        }

        override fun getItemCount(): Int {
            return tabs.size
        }

        override fun createFragment(position: Int): Fragment {
            return when (tabs[position]) {
                Tab.Details -> TorrentDetailsFragment()
                Tab.Files -> TorrentFilesFragment()
                Tab.Trackers -> TrackersFragment()
                Tab.Peers -> PeersFragment()
                Tab.Limits -> TorrentLimitsFragment()
            }
        }
    }

    interface PagerFragment {
        fun update()
    }
}
