// SPDX-FileCopyrightText: 2017-2023 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package org.equeim.tremotesf.ui.torrentpropertiesfragment

import android.os.Bundle
import android.view.MenuItem
import android.view.View
import androidx.activity.addCallback
import androidx.annotation.LayoutRes
import androidx.annotation.StringRes
import androidx.appcompat.widget.TooltipCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.FloatingWindow
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.fragment.navArgs
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.tabs.TabLayoutMediator
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.equeim.tremotesf.R
import org.equeim.tremotesf.databinding.TorrentPropertiesFragmentBinding
import org.equeim.tremotesf.rpc.GlobalRpcClient
import org.equeim.tremotesf.rpc.RpcRequestState
import org.equeim.tremotesf.rpc.requests.TorrentStatus
import org.equeim.tremotesf.rpc.requests.reannounceTorrents
import org.equeim.tremotesf.rpc.requests.removeTorrents
import org.equeim.tremotesf.rpc.toNativeSeparators
import org.equeim.tremotesf.ui.NavigationFragment
import org.equeim.tremotesf.ui.RemoveTorrentDialogFragment
import org.equeim.tremotesf.ui.Settings
import org.equeim.tremotesf.ui.TorrentFileRenameDialogFragment
import org.equeim.tremotesf.ui.applyNavigationBarBottomInset
import org.equeim.tremotesf.ui.utils.Utils
import org.equeim.tremotesf.ui.utils.currentItemFlow
import org.equeim.tremotesf.ui.utils.findFragment
import org.equeim.tremotesf.ui.utils.hide
import org.equeim.tremotesf.ui.utils.hideKeyboard
import org.equeim.tremotesf.ui.utils.launchAndCollectWhenStarted
import org.equeim.tremotesf.ui.utils.popDialog
import org.equeim.tremotesf.ui.utils.showError
import org.equeim.tremotesf.ui.utils.showLoading
import org.equeim.tremotesf.ui.utils.viewLifecycleObject
import timber.log.Timber


class TorrentPropertiesFragment : NavigationFragment(
    R.layout.torrent_properties_fragment,
    0,
    R.menu.torrent_properties_fragment_menu
) {
    private val args by navArgs<TorrentPropertiesFragmentArgs>()
    private val model by TorrentPropertiesFragmentViewModel.from(this)

    val binding by viewLifecycleObject(TorrentPropertiesFragmentBinding::bind)

    override fun onViewStateRestored(savedInstanceState: Bundle?) {
        super.onViewStateRestored(savedInstanceState)
        toolbar.title = args.torrentName

        viewLifecycleOwner.lifecycleScope.launch {
            if (Settings.quickReturn.get()) {
                toolbar.setOnClickListener {
                    val tab = PagerAdapter.Tab.entries[binding.pager.currentItem]
                    childFragmentManager.fragments
                        .asSequence()
                        .filterIsInstance<PagerFragment>()
                        .find { it.tab == tab }
                        ?.onToolbarClicked()
                }
            }
        }

        binding.pager.adapter = PagerAdapter(this)
        TabLayoutMediator(binding.tabLayout, binding.pager) { tab, position ->
            tab.setText(PagerAdapter.getTitle(position))
        }.attach()

        val backCallback = requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner) {
            childFragmentManager.findFragment<TorrentFilesFragment>()?.navigateUp()
        }
        @OptIn(ExperimentalCoroutinesApi::class)
        binding.pager.currentItemFlow.flatMapLatest { tab ->
            if (tab == PagerAdapter.Tab.Files.ordinal) {
                val fragment = childFragmentManager.findFragment<TorrentFilesFragment>()
                val isAtRootOfTree = fragment?.isAtRootOfTree() ?: flowOf(true)
                combine(isAtRootOfTree, requiredActivity.actionMode) { isAtRoot, actionMode ->
                    !isAtRoot && actionMode == null
                }
            } else {
                flowOf(false)
            }
        }.distinctUntilChanged().launchAndCollectWhenStarted(viewLifecycleOwner, backCallback::isEnabled::set)

        binding.pager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            private var previousPage = -1

            override fun onPageSelected(position: Int) {
                if (previousPage != -1) {
                    requiredActivity.apply {
                        actionMode.value?.finish()
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

        binding.fab.apply {
            TooltipCompat.setTooltipText(this, contentDescription)
            setOnClickListener {
                navigate(TorrentPropertiesFragmentDirections.toAddTrackersDialog())
            }
        }

        model.torrentDetails
            .map { (it as? RpcRequestState.Loaded)?.response?.name }
            .filterNotNull()
            .distinctUntilChanged()
            .launchAndCollectWhenStarted(viewLifecycleOwner) {
                toolbar.title = it
            }
        model.torrentDetails
            .map { state -> (state as? RpcRequestState.Loaded)?.response?.status?.let { it == TorrentStatus.Paused } }
            .distinctUntilChanged()
            .launchAndCollectWhenStarted(viewLifecycleOwner) {
                updateMenu(it)
            }
        model.torrentDetails
            .map { (it as? RpcRequestState.Loaded)?.response != null }
            .distinctUntilChanged()
            .launchAndCollectWhenStarted(viewLifecycleOwner) {
                updateViewVisibility(it)
                if (!it) {
                    navController.popDialog()
                }
            }
        model.torrentDetails.launchAndCollectWhenStarted(viewLifecycleOwner) {
            updatePlaceholder(it)
        }

        RemoveTorrentDialogFragment.setFragmentResultListener(this) {
            GlobalRpcClient.performBackgroundRpcRequest(R.string.torrents_remove_error) {
                removeTorrents(
                    it.torrentHashStrings,
                    it.deleteFiles
                )
            }
            lifecycleScope.launch {
                navController.currentBackStackEntryFlow.first { it.destination.id == R.id.torrent_properties_fragment }
                navController.popBackStack()
            }
        }

        TorrentFileRenameDialogFragment.setFragmentResultListener(this) { (_, filePath, newName) ->
            model.renameTorrentFile(filePath, newName)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        with(binding.pager) {
            if (isVisible) {
                model.rememberedPagerItem = currentItem
            }
        }
    }

    override fun onNavigatedFrom(newDestination: NavDestination) {
        if (newDestination !is FloatingWindow) {
            for (fragment in childFragmentManager.fragments) {
                (fragment as? PagerFragment)?.onNavigatedFromParent()
            }
        }
    }

    override fun onToolbarMenuItemClicked(menuItem: MenuItem): Boolean {
        val torrentDetails = (model.torrentDetails.value as? RpcRequestState.Loaded)?.response ?: return false
        when (menuItem.itemId) {
            R.id.start -> model.startTorrent(torrentDetails.id, now = false)
            R.id.pause -> model.pauseTorrent(torrentDetails.id)
            R.id.check -> model.checkTorrent(torrentDetails.id)
            R.id.start_now -> model.startTorrent(torrentDetails.id, now = true)
            R.id.reannounce -> GlobalRpcClient.performBackgroundRpcRequest(R.string.torrents_reannounce_error) {
                reannounceTorrents(
                    listOf(torrentDetails.id)
                )
            }

            R.id.set_location -> navigate(
                TorrentPropertiesFragmentDirections.toTorrentSetLocationDialog(
                    torrentHashStrings = arrayOf(args.torrentHashString),
                    location = torrentDetails.downloadDirectory.toNativeSeparators()
                )
            )

            R.id.rename -> navigate(
                TorrentPropertiesFragmentDirections.toTorrentFileRenameDialog(
                    torrentDetails.name,
                    torrentDetails.name,
                    torrentDetails.hashString
                )
            )

            R.id.remove -> navigate(
                TorrentPropertiesFragmentDirections.toRemoveTorrentDialog(
                    arrayOf(args.torrentHashString),
                    true
                )
            )

            R.id.share -> Utils.shareTorrents(listOf(torrentDetails.magnetLink), requireContext())
            else -> return false
        }
        return true
    }

    private fun updateMenu(torrentIsPaused: Boolean?) {
        val menu = toolbar.menu
        if (torrentIsPaused == null) {
            toolbar.hideOverflowMenu()
            menu.setGroupVisible(0, false)
        } else {
            menu.setGroupVisible(0, true)
            if (torrentIsPaused) {
                intArrayOf(R.id.pause)
            } else {
                intArrayOf(R.id.start, R.id.start_now)
            }.forEach { menu.findItem(it).isVisible = false }
        }
    }

    private fun updateViewVisibility(hasTorrentDetails: Boolean) {
        Timber.d("updateViewVisibility() called with: hasTorrentDetails = $hasTorrentDetails")
        with(binding) {
            if (hasTorrentDetails) {
                (toolbar.layoutParams as AppBarLayout.LayoutParams?)?.scrollFlags =
                    AppBarLayout.LayoutParams.SCROLL_FLAG_SCROLL or
                            AppBarLayout.LayoutParams.SCROLL_FLAG_SNAP or
                            AppBarLayout.LayoutParams.SCROLL_FLAG_ENTER_ALWAYS
                tabLayout.visibility = View.VISIBLE
                pager.visibility = View.VISIBLE

                if (model.rememberedPagerItem != -1) {
                    pager.setCurrentItem(model.rememberedPagerItem, false)
                    model.rememberedPagerItem = -1
                }
            } else {
                (toolbar.layoutParams as AppBarLayout.LayoutParams?)?.scrollFlags = 0
                tabLayout.visibility = View.GONE
                pager.visibility = View.GONE
                pager.currentItem = 0
            }
        }
    }

    private fun updatePlaceholder(requestState: RpcRequestState<*>) {
        with(binding.placeholderView) {
            when (requestState) {
                is RpcRequestState.Loading -> showLoading()
                is RpcRequestState.Loaded -> if (requestState.response == null) {
                    showError(getText(R.string.torrent_not_found))
                } else {
                    hide()
                }
                is RpcRequestState.Error -> showError(requestState.error)
            }
        }
    }

    class PagerAdapter(fragment: Fragment) : FragmentStateAdapter(fragment) {
        companion object {
            @StringRes
            fun getTitle(position: Int): Int {
                return when (Tab.entries[position]) {
                    Tab.Details -> R.string.details
                    Tab.Files -> R.string.files
                    Tab.Trackers -> R.string.trackers
                    Tab.Peers -> R.string.peers
                    Tab.WebSeeders -> R.string.web_seeders
                    Tab.Limits -> R.string.limits
                }
            }
        }

        enum class Tab {
            Details,
            Files,
            Trackers,
            Peers,
            WebSeeders,
            Limits
        }

        override fun getItemCount(): Int {
            return Tab.entries.size
        }

        override fun createFragment(position: Int): Fragment {
            return when (Tab.entries[position]) {
                Tab.Details -> TorrentDetailsFragment()
                Tab.Files -> TorrentFilesFragment()
                Tab.Trackers -> TrackersFragment()
                Tab.Peers -> PeersFragment()
                Tab.WebSeeders -> WebSeedersFragment()
                Tab.Limits -> TorrentLimitsFragment()
            }
        }
    }

    abstract class PagerFragment(@LayoutRes contentLayoutId: Int, val tab: PagerAdapter.Tab) :
        Fragment(contentLayoutId) {
        open fun onNavigatedFromParent() = Unit
        open fun onToolbarClicked() = Unit

        override fun onViewStateRestored(savedInstanceState: Bundle?) {
            super.onViewStateRestored(savedInstanceState)
            applyNavigationBarBottomInset()
        }
    }

    companion object {
        fun getTorrentHashString(navController: NavController): String =
            TorrentPropertiesFragmentArgs.fromBundle(checkNotNull(navController.getBackStackEntry(R.id.torrent_properties_fragment).arguments)).torrentHashString
    }
}
