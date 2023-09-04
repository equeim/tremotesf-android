// SPDX-FileCopyrightText: 2017-2022 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package org.equeim.tremotesf.ui.serversettingsfragment

import android.os.Bundle
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.onEach
import org.equeim.tremotesf.R
import org.equeim.tremotesf.databinding.ServerSettingsSeedingFragmentBinding
import org.equeim.tremotesf.rpc.GlobalRpcClient
import org.equeim.tremotesf.rpc.getErrorString
import org.equeim.tremotesf.torrentfile.rpc.RpcClient
import org.equeim.tremotesf.torrentfile.rpc.RpcRequestState
import org.equeim.tremotesf.torrentfile.rpc.performRecoveringRequest
import org.equeim.tremotesf.torrentfile.rpc.requests.serversettings.SeedingServerSettings
import org.equeim.tremotesf.torrentfile.rpc.requests.serversettings.getSeedingServerSettings
import org.equeim.tremotesf.torrentfile.rpc.requests.serversettings.setServerIdleSeedingLimit
import org.equeim.tremotesf.torrentfile.rpc.requests.serversettings.setServerIdleSeedingLimited
import org.equeim.tremotesf.torrentfile.rpc.requests.serversettings.setServerRatioLimit
import org.equeim.tremotesf.torrentfile.rpc.requests.serversettings.setServerRatioLimited
import org.equeim.tremotesf.torrentfile.rpc.stateIn
import org.equeim.tremotesf.ui.NavigationFragment
import org.equeim.tremotesf.ui.utils.DecimalFormats
import org.equeim.tremotesf.ui.utils.DoubleFilter
import org.equeim.tremotesf.ui.utils.IntFilter
import org.equeim.tremotesf.ui.utils.doAfterTextChangedAndNotEmpty
import org.equeim.tremotesf.ui.utils.hideKeyboard
import org.equeim.tremotesf.ui.utils.launchAndCollectWhenStarted
import org.equeim.tremotesf.ui.utils.setDependentViews
import org.equeim.tremotesf.ui.utils.viewLifecycleObject
import timber.log.Timber
import kotlin.time.Duration.Companion.minutes

class SeedingFragment : NavigationFragment(
    R.layout.server_settings_seeding_fragment,
    R.string.server_settings_seeding
) {
    private val model by viewModels<SeedingFragmentViewModel>()
    private val binding by viewLifecycleObject(ServerSettingsSeedingFragmentBinding::bind)

    override fun onViewStateRestored(savedInstanceState: Bundle?) {
        super.onViewStateRestored(savedInstanceState)
        with(binding) {
            ratioLimitCheckBox.setDependentViews(ratioLimitLayout) { checked ->
                onValueChanged { setServerRatioLimited(checked) }
            }

            val doubleFilter = DoubleFilter(0.0..10000.0)
            ratioLimitEdit.filters = arrayOf(doubleFilter)
            ratioLimitEdit.doAfterTextChangedAndNotEmpty {
                val limit = doubleFilter.parseOrNull(it.toString())
                if (limit != null) {
                    onValueChanged { setServerRatioLimit(limit) }
                } else {
                    Timber.e("Failed to parse ratio limit $it")
                }
            }

            idleSeedingCheckBox.setDependentViews(idleSeedingLimitLayout) { checked ->
                onValueChanged { setServerIdleSeedingLimited(checked) }
            }

            idleSeedingLimitEdit.filters = arrayOf(IntFilter(0..10000))
            idleSeedingLimitEdit.doAfterTextChangedAndNotEmpty {
                val limit = it.toString().toInt().minutes
                try {
                    onValueChanged { setServerIdleSeedingLimit(limit) }
                } catch (e: NumberFormatException) {
                    Timber.e(e, "Failed to parse idle seeding limit $it")
                }
            }
        }

        model.settings.launchAndCollectWhenStarted(viewLifecycleOwner) {
            when (it) {
                is RpcRequestState.Loaded -> showSettings(it.response)
                is RpcRequestState.Loading -> showPlaceholder(getString(R.string.loading), showProgressBar = true)
                is RpcRequestState.Error -> showPlaceholder(it.error.getErrorString(requireContext()), showProgressBar = false)
            }
        }
    }

    private fun showPlaceholder(text: String, showProgressBar: Boolean) {
        hideKeyboard()
        with (binding) {
            scrollView.isVisible = false
            with (placeholderView) {
                root.isVisible = true
                progressBar.isVisible = showProgressBar
                placeholder.text = text
            }
        }
    }

    private fun showSettings(settings: SeedingServerSettings) {
        with (binding) {
            scrollView.isVisible = true
            placeholderView.root.isVisible = false
        }
        if (model.shouldSetInitialState) {
            updateViews(settings)
            model.shouldSetInitialState = false
        }
    }

    private fun updateViews(settings: SeedingServerSettings) = with(binding) {
        ratioLimitCheckBox.isChecked = settings.ratioLimited
        ratioLimitEdit.setText(DecimalFormats.ratio.format(settings.ratioLimit))
        idleSeedingCheckBox.isChecked = settings.idleSeedingLimited
        idleSeedingLimitEdit.setText(settings.idleSeedingLimit.inWholeMinutes.toString())
    }

    private fun onValueChanged(performRpcRequest: suspend RpcClient.() -> Unit) {
        if (!model.shouldSetInitialState) {
            GlobalRpcClient.performBackgroundRpcRequest(R.string.set_server_settings_error, performRpcRequest)
        }
    }
}

class SeedingFragmentViewModel : ViewModel() {
    var shouldSetInitialState = true
    val settings: StateFlow<RpcRequestState<SeedingServerSettings>> =
        GlobalRpcClient.performRecoveringRequest { getSeedingServerSettings() }
            .onEach { if (it !is RpcRequestState.Loaded) shouldSetInitialState = true }
            .stateIn(GlobalRpcClient, viewModelScope)
}
