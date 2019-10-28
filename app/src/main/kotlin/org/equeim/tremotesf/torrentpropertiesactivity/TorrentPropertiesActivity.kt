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

import android.content.Context
import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager

import androidx.activity.OnBackPressedCallback
import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.viewpager.widget.ViewPager
import androidx.appcompat.view.ActionMode
import androidx.appcompat.widget.Toolbar
import androidx.core.content.getSystemService
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentPagerAdapter
import androidx.fragment.app.commit

import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.snackbar.Snackbar

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

import kotlinx.android.synthetic.main.torrent_properties_fragment.*


class TorrentPropertiesActivity : BaseActivity(R.layout.torrent_properties_activity, true), Selector.ActionModeActivity {
    override var actionMode: ActionMode? = null

    override fun onSupportActionModeStarted(mode: ActionMode) {
        super.onSupportActionModeStarted(mode)
        actionMode = mode
    }

    override fun onSupportActionModeFinished(mode: ActionMode) {
        super.onSupportActionModeFinished(mode)
        actionMode = null
    }
}

class TorrentPropertiesFragment : Fragment(R.layout.torrent_properties_fragment) {
    companion object {
        const val HASH = "org.equeim.TorrentPropertiesActivity.HASH"
        const val NAME = "org.equeim.TorrentPropertiesActivity.NAME"
    }

    lateinit var hash: String

    var torrent: TorrentData? = null
        set(value) {
            val activity = requireActivity() as BaseActivity
            var needUpdate = (activity.active || activity.creating)
            if (value !== field) {
                field = value
                if (value == null) {
                    needUpdate = true
                    if (Rpc.isConnected) {
                        placeholder.text = getString(R.string.torrent_removed)
                    }

                    requireFragmentManager().apply {
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
    private var startMenuItem: MenuItem? = null
    private var pauseMenuItem: MenuItem? = null

    private var snackbar: Snackbar? = null

    private var pagerAdapter: PagerAdapter? = null
    private var backPressedCallback: OnBackPressedCallback? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        hash = requireActivity().intent.getStringExtra(HASH)!!

        pagerAdapter = PagerAdapter(this, requireContext())
        pager.adapter = pagerAdapter
        tab_layout.setupWithViewPager(pager)

        val backPressedCallback = requireActivity().onBackPressedDispatcher.addCallback(this, false) {
            pagerAdapter?.filesFragment?.adapter?.navigateUp()
        }
        this.backPressedCallback = backPressedCallback
        setBackPressedCallbackEnabledState()

        pager.addOnPageChangeListener(object : ViewPager.SimpleOnPageChangeListener() {
            private var previousPage = -1
            private val inputManager = requireContext().getSystemService<InputMethodManager>()!!

            override fun onPageSelected(position: Int) {
                if (previousPage != -1) {
                    (requireActivity() as TorrentPropertiesActivity).apply {
                        actionMode?.finish()
                        currentFocus?.let { inputManager.hideSoftInputFromWindow(it.windowToken, 0) }
                    }
                }
                if (position == PagerAdapter.TAB_TRACKERS) {
                    fab.show()
                } else {
                    fab.hide()
                }
                setBackPressedCallbackEnabledState()
                previousPage = position
            }
        })

        fab.setOnClickListener {
            if (requireFragmentManager().findFragmentByTag(TrackersAdapter.EditTrackerDialogFragment.TAG) == null) {
                val fragment = TrackersAdapter.EditTrackerDialogFragment()
                fragment.show(requireFragmentManager(), TrackersAdapter.EditTrackerDialogFragment.TAG)
            }
        }

        rpcStatusListener(Rpc.status)
        if (!Rpc.isConnected) {
            updatePlaceholderVisibility()
        }

        Rpc.addStatusListener(rpcStatusListener)
        Rpc.addTorrentsUpdatedListener(torrentsUpdatedListener)
    }

    fun setBackPressedCallbackEnabledState() {
        backPressedCallback?.isEnabled = if (pager.currentItem == PagerAdapter.TAB_FILES) {
            !(pagerAdapter?.filesFragment?.adapter?.isAtRootDirectory ?: true)
        } else {
            false
        }
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        (requireActivity() as AppCompatActivity).apply {
            setSupportActionBar(toolbar as Toolbar)
            supportActionBar?.setDisplayHomeAsUpEnabled(true)
            title = intent.getStringExtra(NAME)
        }
    }

    override fun onStart() {
        super.onStart()
        if (!(requireActivity() as BaseActivity).creating) {
            torrentsUpdatedListener()
        }
    }

    override fun onStop() {
        super.onStop()
        Rpc.removeTorrentsUpdatedListener(torrentsUpdatedListener)
    }

    override fun onDestroyView() {
        menu = null
        startMenuItem = null
        pauseMenuItem = null
        snackbar = null
        pagerAdapter = null
        backPressedCallback = null

        Rpc.removeStatusListener(rpcStatusListener)
        Rpc.removeTorrentsUpdatedListener(torrentsUpdatedListener)

        if (requireActivity().isFinishing && torrent != null) {
            torrent?.torrent?.apply {
                setFilesEnabled(false)
                setPeersEnabled(false)
            }
        }

        super.onDestroyView()
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        this.menu = menu
        inflater.inflate(R.menu.torrent_properties_activity_menu, menu)
        startMenuItem = menu.findItem(R.id.start)
        pauseMenuItem = menu.findItem(R.id.pause)
        updateMenu()
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
                        .show(requireFragmentManager(), TorrentsAdapter.SetLocationDialogFragment.TAG)
                R.id.rename -> TorrentFilesAdapter.RenameDialogFragment.create(torrent.id,
                                                                               torrent.name,
                                                                               torrent.name)
                        .show(requireFragmentManager(), TorrentFilesAdapter.RenameDialogFragment.TAG)
                R.id.remove -> TorrentsAdapter.RemoveDialogFragment.create(intArrayOf(torrent.id)).show(requireFragmentManager(),
                                                                                                        TorrentsAdapter.RemoveDialogFragment.TAG)
                else -> return false
            }
        }
        return true
    }

    private fun update() {
        torrent?.let { torrent ->
            activity?.title = torrent.name
        }

        updateMenu()

        pagerAdapter?.apply {
            detailsFragment?.update()
            filesFragment?.update()
            trackersFragment?.update()
            peersFragment?.update()
        }
    }

    private fun updateMenu() {
        val menu = this.menu ?: return

        for (i in 0 until menu.size()) {
            menu.getItem(i).isVisible = (torrent != null)
        }
        torrent?.let { torrent ->
            startMenuItem?.isVisible = when (torrent.status) {
                Torrent.Status.Paused,
                Torrent.Status.Errored -> true
                else -> false
            }
            pauseMenuItem?.isVisible = !(startMenuItem?.isVisible ?: true)
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

    class PagerAdapter(private val mainFragment: TorrentPropertiesFragment, private val context: Context) : FragmentPagerAdapter(mainFragment.requireFragmentManager(), BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT) {
        companion object {
            const val TAB_DETAILS = 0
            const val TAB_FILES = 1
            const val TAB_TRACKERS = 2
            const val TAB_PEERS = 3
            const val TAB_LIMITS = 4
            const val TABS_COUNT = 5
        }

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
                    mainFragment.setBackPressedCallbackEnabledState()
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
                TAB_DETAILS -> context.getString(R.string.details)
                TAB_FILES -> context.getString(R.string.files)
                TAB_TRACKERS -> context.getString(R.string.trackers)
                TAB_PEERS -> context.getString(R.string.peers)
                TAB_LIMITS -> context.getString(R.string.limits)
                else -> ""
            }
        }
    }
}
