/*
 * Copyright (C) 2017-2022 Alexey Rochev <equeim@gmail.com>
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

package org.equeim.tremotesf.ui.torrentslistfragment

import android.graphics.drawable.LayerDrawable
import android.os.Bundle
import android.view.MenuItem
import androidx.activity.result.ActivityResultLauncher
import androidx.appcompat.widget.SearchView
import androidx.appcompat.widget.TooltipCompat
import androidx.core.content.withStyledAttributes
import androidx.core.view.children
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.navigation.navGraphViewModels
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.elevation.ElevationOverlayProvider
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import org.equeim.libtremotesf.RpcConnectionState
import org.equeim.libtremotesf.RpcError
import org.equeim.tremotesf.R
import org.equeim.tremotesf.databinding.TorrentsListFragmentBinding
import org.equeim.tremotesf.rpc.GlobalRpc
import org.equeim.tremotesf.rpc.GlobalServers
import org.equeim.tremotesf.rpc.statusString
import org.equeim.tremotesf.torrentfile.rpc.Server
import org.equeim.tremotesf.torrentfile.rpc.ServerStats
import org.equeim.tremotesf.ui.NavigationFragment
import org.equeim.tremotesf.ui.Settings
import org.equeim.tremotesf.ui.TorrentFileRenameDialogFragment
import org.equeim.tremotesf.ui.utils.*


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
        TorrentFileRenameDialogFragment.setFragmentResultListenerForRpc(this)
        notificationPermissionLauncher = model.notificationPermissionHelper?.registerWithFragment(this)
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
                if (GlobalRpc.isConnected.value) {
                    GlobalRpc.nativeInstance.updateData()
                } else {
                    binding.swipeRefreshLayout.isRefreshing = false
                }
            }
            GlobalRpc.torrentsUpdatedEvent.launchAndCollectWhenStarted(viewLifecycleOwner) {
                isRefreshing = false
            }
        }

        binding.torrentsView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            (itemAnimator as DefaultItemAnimator).supportsChangeAnimations = false
            fastScroller.setSwipeRefreshLayout(binding.swipeRefreshLayout)
        }

        binding.detailedErrorMessageButton.setOnClickListener {
            navigate(TorrentsListFragmentDirections.toDetailedConnectionErrorDialogFragment())
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
                compactView.await(),
                multilineName.await()
            )
            binding.torrentsView.adapter = torrentsAdapter
            model.torrents.launchAndCollectWhenStarted(viewLifecycleOwner, torrentsAdapter::update)
        }

        GlobalRpc.isConnected.launchAndCollectWhenStarted(viewLifecycleOwner, ::onRpcConnectedChanged)

        combine(model.showAddTorrentButton, model.connectionButtonState) { showAddTorrentButton, connectionButtonState ->
            showAddTorrentButton || connectionButtonState != TorrentsListFragmentViewModel.ConnectionButtonState.Hidden
        }.distinctUntilChanged().launchAndCollectWhenStarted(viewLifecycleOwner) {
            binding.endButtonSpacer.isVisible = it
        }

        model.placeholderState.launchAndCollectWhenStarted(viewLifecycleOwner, ::updatePlaceholder)

        GlobalServers.currentServer.launchAndCollectWhenStarted(viewLifecycleOwner, ::updateTitle)
        model.subtitleUpdateData.launchAndCollectWhenStarted(viewLifecycleOwner, ::updateSubtitle)

        model.showAddTorrentDuplicateError.handleAndReset {
            requireView().showSnackbar(R.string.torrent_duplicate, Snackbar.LENGTH_LONG)
        }.launchAndCollectWhenStarted(viewLifecycleOwner)

        model.showAddTorrentError.handleAndReset {
            requireView().showSnackbar(R.string.torrent_add_error, Snackbar.LENGTH_LONG)
        }.launchAndCollectWhenStarted(viewLifecycleOwner)

        notificationPermissionLauncher?.let { launcher ->
            model.showNotificationPermissionRequest.handleAndReset {
                requireView().showSnackbar(
                    message = R.string.notification_permission_rationale,
                    length = Snackbar.LENGTH_INDEFINITE,
                    actionText = R.string.request_permission,
                    action = { model.notificationPermissionHelper?.requestPermission(this, launcher) }
                )
            }.launchAndCollectWhenStarted(viewLifecycleOwner)
        }
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

                requiredActivity.onBackPressedDispatcher.addCustomCallback(viewLifecycleOwner) {
                    collapse()
                }
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

    private fun updateConnectionButton(state: TorrentsListFragmentViewModel.ConnectionButtonState) {
        binding.connectionButton.apply {
            val text = when (state) {
                TorrentsListFragmentViewModel.ConnectionButtonState.AddServer -> {
                    setOnClickListener { navigate(TorrentsListFragmentDirections.toServerEditFragment()) }
                    R.string.add_server
                }
                TorrentsListFragmentViewModel.ConnectionButtonState.Connect -> {
                    setOnClickListener { GlobalRpc.nativeInstance.connect() }
                    R.string.connect
                }
                TorrentsListFragmentViewModel.ConnectionButtonState.Disconnect -> {
                    setOnClickListener { GlobalRpc.nativeInstance.disconnect() }
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

    private fun onRpcConnectedChanged(connected: Boolean) {
        if (!connected) {
            requiredActivity.actionMode?.finish()
            when (navController.currentDestination?.id) {
                R.id.transmission_settings_dialog_fragment,
                R.id.detailed_connection_error_dialog_fragment -> Unit
                else -> navController.popDialog()
            }
        }

        with(binding) {
            swipeRefreshLayout.apply {
                isRefreshing = false
                isEnabled = connected
            }

            bottomToolbar.apply {
                hideOnScroll = connected
                if (!connected) performShow()
            }
        }
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

    private fun updateSubtitle(subtitleData: Pair<ServerStats, Boolean>) {
        val (stats, isConnected) = subtitleData
        toolbar.subtitle = if (isConnected) {
            getString(
                R.string.main_activity_subtitle,
                FormatUtils.formatByteSpeed(requireContext(), stats.downloadSpeed),
                FormatUtils.formatByteSpeed(requireContext(), stats.uploadSpeed)
            )
        } else {
            null
        }
    }

    private fun updatePlaceholder(data: TorrentsListFragmentViewModel.PlaceholderState) = with(binding) {
        val (status, hasTorrents) = data

        progressBar.isVisible = status.connectionState == RpcConnectionState.Connecting && !hasTorrents
        statusString.apply {
            text = when {
                hasTorrents -> null
                (status.connectionState == RpcConnectionState.Connected) -> getString(R.string.no_torrents)
                else -> status.statusString
            }
            isVisible = !text.isNullOrEmpty()
        }
        errorMessage.apply {
            text = if (status.error.error == RpcError.ConnectionError) {
                status.error.errorMessage
            } else {
                null
            }
            isVisible = !text.isNullOrEmpty()
        }
        detailedErrorMessageButton.apply {
            isVisible = status.error.detailedErrorMessage.isNotEmpty()
        }
        placeholderView.apply {
            isVisible = children.any { it.isVisible }
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
