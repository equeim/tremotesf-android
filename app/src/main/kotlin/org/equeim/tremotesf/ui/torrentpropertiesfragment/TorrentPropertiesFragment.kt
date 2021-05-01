/*
 * Copyright (C) 2017-2020 Alexey Rochev <equeim@gmail.com>
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

package org.equeim.tremotesf.ui.torrentpropertiesfragment

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.activity.addCallback
import androidx.annotation.LayoutRes
import androidx.annotation.StringRes
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.navigation.FloatingWindow
import androidx.navigation.NavDestination
import androidx.navigation.fragment.navArgs
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayoutMediator
import kotlinx.coroutines.flow.combine
import org.equeim.libtremotesf.TorrentData
import org.equeim.tremotesf.R
import org.equeim.tremotesf.data.rpc.Rpc
import org.equeim.tremotesf.data.rpc.RpcConnectionState
import org.equeim.tremotesf.data.rpc.Torrent
import org.equeim.tremotesf.databinding.TorrentPropertiesFragmentBinding
import org.equeim.tremotesf.ui.NavigationFragment
import org.equeim.tremotesf.ui.TorrentFileRenameDialogFragment
import org.equeim.tremotesf.ui.addNavigationBarBottomPadding
import org.equeim.tremotesf.ui.torrentpropertiesfragment.TorrentPropertiesFragmentViewModel.Companion.hasTorrent
import org.equeim.tremotesf.ui.utils.Utils
import org.equeim.tremotesf.ui.utils.findFragment
import org.equeim.tremotesf.ui.utils.hideKeyboard
import org.equeim.tremotesf.ui.utils.popDialog
import org.equeim.tremotesf.ui.utils.showSnackbar
import org.equeim.tremotesf.ui.utils.viewBinding
import org.equeim.tremotesf.utils.collectWhenStarted
import org.equeim.tremotesf.utils.handleAndReset


class TorrentPropertiesFragment : NavigationFragment(
    R.layout.torrent_properties_fragment,
    0,
    R.menu.torrent_properties_fragment_menu
) {
    private val args: TorrentPropertiesFragmentArgs by navArgs()

    private val model by TorrentPropertiesFragmentViewModel.getLazy(this)

    private var menu: Menu? = null
    val binding by viewBinding(TorrentPropertiesFragmentBinding::bind)
    private var pagerAdapter: PagerAdapter? = null
    private var snackbar: Snackbar? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        TorrentFileRenameDialogFragment.setFragmentResultListenerForRpc(this)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        toolbar?.let {
            it.title = args.name
            menu = it.menu
        }

        val pagerAdapter = PagerAdapter(this)
        this.pagerAdapter = pagerAdapter
        binding.pager.adapter = pagerAdapter
        TabLayoutMediator(binding.tabLayout, binding.pager) { tab, position ->
            tab.setText(PagerAdapter.getTitle(position))
        }.attach()

        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner) {
            if (binding.pager.currentItem != PagerAdapter.Tab.Files.ordinal ||
                findFragment<TorrentFilesFragment>()?.model?.filesTree?.navigateUp() != true
            ) {
                isEnabled = false
                requireActivity().onBackPressedDispatcher.onBackPressed()
            }
        }

        binding.pager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            private var previousPage = -1

            override fun onPageSelected(position: Int) {
                if (previousPage != -1) {
                    requiredActivity.apply {
                        actionMode?.finish()
                        hideKeyboard()
                    }
                }
                if (position == PagerAdapter.Tab.Trackers.ordinal) {
                    binding.fab.show()
                } else {
                    binding.fab.hide()
                }
                previousPage = position
            }
        })

        binding.fab.setOnClickListener {
            navigate(TorrentPropertiesFragmentDirections.toEditTrackerDialog())
        }

        model.showTorrentRemovedMessage.handleAndReset(::showTorrentRemovedMessage)
            .collectWhenStarted(viewLifecycleOwner)

        Rpc.connectionState.collectWhenStarted(viewLifecycleOwner, ::onConnectionStateChanged)

        combine(Rpc.status, model.torrent, ::Pair)
            .collectWhenStarted(viewLifecycleOwner) { (status, torrent) ->
                updatePlaceholderText(status, torrent)
            }

        model.torrent.hasTorrent().collectWhenStarted(viewLifecycleOwner, ::onHasTorrentChanged)
        model.torrent.collectWhenStarted(viewLifecycleOwner, ::onTorrentChanged)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        with(binding.pager) {
            if (isVisible) {
                model.rememberedPagerItem = currentItem
            }
        }
    }

    override fun onDestroyView() {
        menu = null
        pagerAdapter = null
        snackbar = null
        super.onDestroyView()
    }

    override fun onNavigatedFrom(newDestination: NavDestination) {
        if (newDestination !is FloatingWindow) {
            for (fragment in childFragmentManager.fragments) {
                (fragment as? PagerFragment)?.onNavigatedFromParent()
            }
        }
    }

    override fun onToolbarMenuItemClicked(menuItem: MenuItem): Boolean {
        val torrent = model.torrent.value ?: return false
        when (menuItem.itemId) {
            R.id.start -> Rpc.nativeInstance.startTorrents(intArrayOf(torrent.id))
            R.id.pause -> Rpc.nativeInstance.pauseTorrents(intArrayOf(torrent.id))
            R.id.check -> Rpc.nativeInstance.checkTorrents(intArrayOf(torrent.id))
            R.id.start_now -> Rpc.nativeInstance.startTorrentsNow(intArrayOf(torrent.id))
            R.id.reannounce -> Rpc.nativeInstance.reannounceTorrents(intArrayOf(torrent.id))
            R.id.set_location -> navigate(
                TorrentPropertiesFragmentDirections.toTorrentSetLocationDialog(
                    intArrayOf(torrent.id),
                    torrent.downloadDirectory
                )
            )
            R.id.rename -> navigate(
                TorrentPropertiesFragmentDirections.toTorrentFileRenameDialog(
                    torrent.name,
                    torrent.name,
                    torrent.id
                )
            )
            R.id.remove -> navigate(
                TorrentPropertiesFragmentDirections.toRemoveTorrentDialog(
                    intArrayOf(torrent.id),
                    true
                )
            )
            R.id.share -> Utils.shareTorrents(listOf(torrent.data.magnetLink), requireContext())
            else -> return false
        }
        return true
    }

    private fun showTorrentRemovedMessage() {
        snackbar?.dismiss()
        snackbar = binding.coordinatorLayout.showSnackbar(R.string.torrent_removed, Snackbar.LENGTH_INDEFINITE) {
            snackbar = null
        }
    }

    private fun onConnectionStateChanged(connectionState: Int) {
        snackbar?.dismiss()
        if (connectionState == RpcConnectionState.Disconnected) {
            snackbar = binding.coordinatorLayout.showSnackbar(
                "",
                Snackbar.LENGTH_INDEFINITE,
                R.string.connect,
                Rpc.nativeInstance::connect
            ) {
                snackbar = null
            }
        }

        binding.progressBar.isVisible = connectionState == RpcConnectionState.Connecting
    }

    private fun updatePlaceholderText(status: Rpc.Status, torrent: Torrent?) {
        with(binding.placeholder) {
            when (status.connectionState) {
                RpcConnectionState.Disconnected -> {
                    text = status.statusString
                }
                RpcConnectionState.Connecting -> {
                    text = getText(R.string.connecting)
                }
                RpcConnectionState.Connected -> {
                    if (torrent == null) {
                        text = getText(R.string.torrent_not_found)
                    }
                }
            }
        }
    }

    private fun onHasTorrentChanged(hasTorrent: Boolean) {
        updateViewVisibility(hasTorrent)
        if (!hasTorrent) {
            navController.popDialog()
        }
    }

    private fun updateViewVisibility(hasTorrent: Boolean) {
        with(binding) {
            if (hasTorrent) {
                (toolbar.layoutParams as AppBarLayout.LayoutParams?)?.scrollFlags =
                    AppBarLayout.LayoutParams.SCROLL_FLAG_SCROLL or
                            AppBarLayout.LayoutParams.SCROLL_FLAG_SNAP or
                            AppBarLayout.LayoutParams.SCROLL_FLAG_ENTER_ALWAYS
                tabLayout.visibility = View.VISIBLE
                pager.visibility = View.VISIBLE
                placeholderLayout.visibility = View.GONE

                if (model.rememberedPagerItem != -1) {
                    pager.setCurrentItem(model.rememberedPagerItem, false)
                    model.rememberedPagerItem = -1
                }
            } else {
                (toolbar.layoutParams as AppBarLayout.LayoutParams?)?.scrollFlags = 0
                tabLayout.visibility = View.GONE
                pager.visibility = View.GONE
                pager.currentItem = 0
                placeholderLayout.visibility = View.VISIBLE
            }
        }
    }

    private fun onTorrentChanged(torrent: Torrent?) {
        updateMenu(torrent)
        if (torrent != null) {
            toolbar?.title = torrent.name
        }
    }

    private fun updateMenu(torrent: Torrent?) {
        val menu = this.menu ?: return
        if (torrent == null) {
            toolbar?.hideOverflowMenu()
            menu.setGroupVisible(0, false)
        } else {
            menu.setGroupVisible(0, true)
            val paused = when (torrent.status) {
                TorrentData.Status.Paused,
                TorrentData.Status.Errored -> true
                else -> false
            }

            if (paused) {
                intArrayOf(R.id.pause)
            } else {
                intArrayOf(R.id.start, R.id.start_now)
            }.forEach { menu.findItem(it).isVisible = false }
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

    abstract class PagerFragment(@LayoutRes contentLayoutId: Int) : Fragment(contentLayoutId) {
        open fun onNavigatedFromParent() = Unit

        override fun onViewStateRestored(savedInstanceState: Bundle?) {
            super.onViewStateRestored(savedInstanceState)
            addNavigationBarBottomPadding(true)
        }
    }
}
