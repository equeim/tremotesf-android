// SPDX-FileCopyrightText: 2017-2024 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package org.equeim.tremotesf.ui.torrentslistfragment

import android.graphics.drawable.LayerDrawable
import android.os.Bundle
import android.view.MenuItem
import androidx.activity.addCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.appcompat.widget.SearchView
import androidx.appcompat.widget.TooltipCompat
import androidx.core.content.withStyledAttributes
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.navGraphViewModels
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.elevation.ElevationOverlayProvider
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import org.equeim.tremotesf.NavMainDirections
import org.equeim.tremotesf.R
import org.equeim.tremotesf.databinding.TorrentsListFragmentBinding
import org.equeim.tremotesf.rpc.GlobalRpcClient
import org.equeim.tremotesf.rpc.GlobalServers
import org.equeim.tremotesf.rpc.PeriodicServerStateUpdater
import org.equeim.tremotesf.rpc.RpcRequestState
import org.equeim.tremotesf.rpc.Server
import org.equeim.tremotesf.rpc.isRecoverable
import org.equeim.tremotesf.rpc.makeDetailedError
import org.equeim.tremotesf.rpc.requests.Torrent
import org.equeim.tremotesf.ui.NavigationFragment
import org.equeim.tremotesf.ui.RemoveTorrentDialogFragment
import org.equeim.tremotesf.ui.Settings
import org.equeim.tremotesf.ui.TorrentFileRenameDialogFragment
import org.equeim.tremotesf.ui.utils.FormatUtils
import org.equeim.tremotesf.ui.utils.Utils
import org.equeim.tremotesf.ui.utils.hide
import org.equeim.tremotesf.ui.utils.launchAndCollectWhenStarted
import org.equeim.tremotesf.ui.utils.showError
import org.equeim.tremotesf.ui.utils.showLoading
import org.equeim.tremotesf.ui.utils.showSnackbar
import org.equeim.tremotesf.ui.utils.viewLifecycleObject
import timber.log.Timber


class TorrentsListFragment : NavigationFragment(
    R.layout.torrents_list_fragment,
    0,
    R.menu.torrents_list_fragment_menu
) {
    private val model by navGraphViewModels<TorrentsListFragmentViewModel>(R.id.torrents_list_fragment)
    private var notificationPermissionLauncher: ActivityResultLauncher<Array<String>>? = null
    private val binding by viewLifecycleObject(TorrentsListFragmentBinding::bind)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        notificationPermissionLauncher = model.notificationPermissionHelper?.registerWithFragment(this)
        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                PeriodicServerStateUpdater.showingTorrentsListScreen.value = true
                suspendCancellableCoroutine {
                    it.invokeOnCancellation {
                        PeriodicServerStateUpdater.showingTorrentsListScreen.value = false
                    }
                }
            }
        }
    }

    override fun onViewStateRestored(savedInstanceState: Bundle?) {
        super.onViewStateRestored(savedInstanceState)

        viewLifecycleOwner.lifecycleScope.launch {
            if (Settings.quickReturn.get()) {
                toolbar.setOnClickListener {
                    binding.torrentsView.scrollToPosition(0)
                }
            }
        }

        setupBottomBar()

        binding.swipeRefreshLayout.apply {
            requireContext().withStyledAttributes(attrs = intArrayOf(androidx.appcompat.R.attr.colorPrimary)) {
                setColorSchemeColors(getColor(0, 0))
            }
            val elevation = resources.getDimension(R.dimen.swipe_refresh_progress_bar_elevation)
            setProgressBackgroundColorSchemeColor(
                ElevationOverlayProvider(requireContext())
                    .compositeOverlayWithThemeSurfaceColorIfNeeded(elevation)
            )

            setOnRefreshListener {
                model.refresh()
            }
        }

        binding.torrentsView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            (itemAnimator as DefaultItemAnimator).supportsChangeAnimations = false
            fastScroller.setSwipeRefreshLayout(binding.swipeRefreshLayout)
        }

        binding.placeholderView.detailedErrorMessageButton.setOnClickListener {
            (model.torrentsListState.value as? RpcRequestState.Error)?.let { error ->
                navigate(NavMainDirections.toDetailedConnectionErrorDialogFragment(error.error.makeDetailedError(GlobalRpcClient)))
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            val compactView = async { Settings.torrentCompactView.get() }
            val multilineName = async { Settings.torrentNameMultiline.get() }
            if (compactView.await()) {
                binding.torrentsView.addItemDecoration(
                    DividerItemDecoration(requireContext(), DividerItemDecoration.VERTICAL)
                )
            }
            val torrentsAdapter = TorrentsAdapter(
                this@TorrentsListFragment,
                model,
                compactView.await(),
                multilineName.await()
            )
            binding.torrentsView.adapter = torrentsAdapter
            model.torrentsListState.launchAndCollectWhenStarted(viewLifecycleOwner) { updateList(it, torrentsAdapter) }
            model.torrentsLoadedEvents
                .launchAndCollectWhenStarted(viewLifecycleOwner) { binding.swipeRefreshLayout.isRefreshing = false }

            model.sortSettingsChanged
                .onEach {
                    torrentsAdapter.currentListChanged.first()
                    binding.torrentsView.scrollToPosition(0)
                }
                .launchAndCollectWhenStarted(viewLifecycleOwner)
        }

        combine(
            model.showAddTorrentButton,
            model.connectionButtonState
        ) { showAddTorrentButton, connectionButtonState ->
            showAddTorrentButton || connectionButtonState != TorrentsListFragmentViewModel.ConnectionButtonState.Hidden
        }.distinctUntilChanged().launchAndCollectWhenStarted(viewLifecycleOwner) {
            binding.endButtonSpacer.isVisible = it
        }

        GlobalServers.currentServer.launchAndCollectWhenStarted(viewLifecycleOwner, ::updateTitle)
        model.subtitleState.launchAndCollectWhenStarted(viewLifecycleOwner, ::updateSubtitle)

        notificationPermissionLauncher?.let { launcher ->
            model.showNotificationPermissionRequest
                .filter { it }
                .launchAndCollectWhenStarted(viewLifecycleOwner) {
                    val result = binding.root.showSnackbar(
                        message = R.string.notification_permission_rationale,
                        duration = Snackbar.LENGTH_INDEFINITE,
                        lifecycleOwner = viewLifecycleOwner,
                        activity = requiredActivity,
                        actionText = R.string.request_permission,
                        anchorViewId = R.id.bottom_toolbar,
                        action = { model.notificationPermissionHelper?.requestPermission(this, launcher) }
                    )
                    model.showNotificationPermissionRequest.compareAndSet(it, false)
                    if (result.event == Snackbar.Callback.DISMISS_EVENT_SWIPE) {
                        model.onNotificationPermissionRequestDismissed()
                    }
                }
        }

        RemoveTorrentDialogFragment.setFragmentResultListener(this) {
            model.removeTorrents(it.torrentHashStrings, it.deleteFiles)
        }

        TorrentFileRenameDialogFragment.setFragmentResultListener(this) {
            if (it.torrentHashString != null) {
                model.renameTorrentFile(it.torrentHashString, it.filePath, it.newName)
            }
        }
    }

    override fun onStart() {
        Timber.d("onStart() called")
        super.onStart()
        model.checkNotificationPermission()
    }

    override fun onStop() {
        Timber.d("onStop() called")
        super.onStop()
    }

    private fun setupBottomBar() {
        with(binding) {
            transmissionSettings.apply {
                TooltipCompat.setTooltipText(this, contentDescription)
                setOnClickListener { navigate(TorrentsListFragmentDirections.toTransmissionSettingsDialogFragment()) }
            }
            model.showTransmissionSettingsButton.launchAndCollectWhenStarted(viewLifecycleOwner) {
                transmissionSettings.isVisible = it
            }

            torrentsFilters.apply {
                TooltipCompat.setTooltipText(this, contentDescription)
                setOnClickListener { navigate(TorrentsListFragmentDirections.toTorrentsFiltersDialogFragment()) }

                val badgeDrawable = (drawable as LayerDrawable).getDrawable(1)
                model.sortOrFiltersEnabled.launchAndCollectWhenStarted(viewLifecycleOwner) {
                    badgeDrawable.alpha = if (it) 192 else 0
                }
            }
            model.showTorrentsFiltersButton.launchAndCollectWhenStarted(viewLifecycleOwner) {
                torrentsFilters.isVisible = it
            }

            searchView.apply {
                setOnQueryTextListener(object : SearchView.OnQueryTextListener {
                    override fun onQueryTextChange(newText: String): Boolean {
                        model.nameFilter.set(newText.trim())
                        return true
                    }

                    override fun onQueryTextSubmit(query: String): Boolean {
                        return false
                    }
                })

                setOnSearchClickListener {
                    model.searchViewIsIconified.set(false)
                }
                setOnCloseListener {
                    model.searchViewIsIconified.set(true)
                    false
                }
                val backCallback = requiredActivity.onBackPressedDispatcher.addCallback(viewLifecycleOwner) {
                    collapse()
                }
                combine(model.searchViewIsIconified.flow(), requiredActivity.actionMode) { isIconified, actionMode ->
                    !isIconified && actionMode == null
                }.launchAndCollectWhenStarted(viewLifecycleOwner, backCallback::isEnabled::set)
            }
            model.showSearchView.launchAndCollectWhenStarted(viewLifecycleOwner) {
                if (!it) searchView.collapse()
                searchView.isVisible = it
            }

            TooltipCompat.setTooltipText(addTorrentButton, addTorrentButton.contentDescription)
            addTorrentButton.setOnClickListener {
                navigate(TorrentsListFragmentDirections.toAddTorrentMenuFragment())
            }
            model.showAddTorrentButton.launchAndCollectWhenStarted(viewLifecycleOwner) {
                binding.addTorrentButton.apply {
                    isVisible = it
                }
            }

            model.connectionButtonState.launchAndCollectWhenStarted(viewLifecycleOwner, ::updateConnectionButton)
        }
    }

    private suspend fun updateList(state: RpcRequestState<List<Torrent>>, adapter: TorrentsAdapter) {
        binding.apply {
            swipeRefreshLayout.isEnabled = ((state as? RpcRequestState.Error)?.error?.isRecoverable ?: true) == true

            if (state is RpcRequestState.Loaded && state.response.isNotEmpty()) {
                adapter.update(state.response)
                placeholderView.hide()
                bottomToolbar.hideOnScroll = true
            } else {
                adapter.update(null)
                requiredActivity.actionMode.value?.finish()
                bottomToolbar.apply {
                    hideOnScroll = false
                    performShow()
                }
                when (state) {
                    is RpcRequestState.Loading -> placeholderView.showLoading(getText(R.string.connecting))
                    is RpcRequestState.Error -> placeholderView.showError(state.error)
                    is RpcRequestState.Loaded -> placeholderView.showError(getText(R.string.no_torrents))
                }
            }
        }
    }

    private fun updateConnectionButton(state: TorrentsListFragmentViewModel.ConnectionButtonState) {
        binding.connectionButton.apply {
            val text = when (state) {
                TorrentsListFragmentViewModel.ConnectionButtonState.AddServer -> {
                    setOnClickListener { navigate(TorrentsListFragmentDirections.toServerEditFragment()) }
                    R.string.add_server
                }

                TorrentsListFragmentViewModel.ConnectionButtonState.Connect -> {
                    setOnClickListener { GlobalRpcClient.shouldConnectToServer.value = true }
                    R.string.connect
                }

                TorrentsListFragmentViewModel.ConnectionButtonState.Disconnect -> {
                    setOnClickListener { GlobalRpcClient.shouldConnectToServer.value = false }
                    R.string.disconnect
                }

                TorrentsListFragmentViewModel.ConnectionButtonState.Hidden -> {
                    setOnClickListener(null)
                    null
                }
            }
            isVisible = if (text == null) {
                false
            } else {
                setText(text)
                true
            }
        }
    }

    override fun onToolbarMenuItemClicked(menuItem: MenuItem): Boolean {
        when (menuItem.itemId) {
            R.id.settings -> navigate(TorrentsListFragmentDirections.toSettingsFragment())
            R.id.about -> navigate(TorrentsListFragmentDirections.toAboutFragment())
            R.id.quit -> Utils.shutdownApp(requireContext())
            else -> return false
        }
        return true
    }

    private fun updateTitle(currentServer: Server?) {
        toolbar.title = if (currentServer != null) {
            getString(
                R.string.current_server_string,
                currentServer.name,
                currentServer.address
            )
        } else {
            getString(R.string.app_name)
        }
    }

    private fun updateSubtitle(state: TorrentsListFragmentViewModel.SubtitleState?) {
        toolbar.subtitle = if (state != null) {
            getString(
                R.string.main_activity_subtitle,
                FormatUtils.formatTransferRate(requireContext(), state.downloadSpeed),
                FormatUtils.formatTransferRate(requireContext(), state.uploadSpeed)
            )
        } else {
            null
        }
    }
}

private fun SearchView.collapse(): Boolean {
    return if (!isIconified) {
        // We need to clear query before calling setIconified(true)
        setQuery(null, false)
        isIconified = true
        true
    } else {
        false
    }
}
