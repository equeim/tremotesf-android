// SPDX-FileCopyrightText: 2017-2023 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package org.equeim.tremotesf.ui.torrentpropertiesfragment

import android.os.Bundle
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.onEach
import org.equeim.tremotesf.R
import org.equeim.tremotesf.databinding.PlaceholderLayoutBinding
import org.equeim.tremotesf.databinding.TorrentLimitsFragmentBinding
import org.equeim.tremotesf.rpc.GlobalRpcClient
import org.equeim.tremotesf.rpc.RpcClient
import org.equeim.tremotesf.rpc.RpcRequestState
import org.equeim.tremotesf.rpc.performRecoveringRequest
import org.equeim.tremotesf.rpc.requests.torrentproperties.TorrentLimits
import org.equeim.tremotesf.rpc.requests.torrentproperties.getTorrentLimits
import org.equeim.tremotesf.rpc.requests.torrentproperties.setTorrentBandwidthPriority
import org.equeim.tremotesf.rpc.requests.torrentproperties.setTorrentDownloadSpeedLimit
import org.equeim.tremotesf.rpc.requests.torrentproperties.setTorrentDownloadSpeedLimited
import org.equeim.tremotesf.rpc.requests.torrentproperties.setTorrentHonorSessionLimits
import org.equeim.tremotesf.rpc.requests.torrentproperties.setTorrentIdleSeedingLimit
import org.equeim.tremotesf.rpc.requests.torrentproperties.setTorrentIdleSeedingLimitMode
import org.equeim.tremotesf.rpc.requests.torrentproperties.setTorrentPeersLimit
import org.equeim.tremotesf.rpc.requests.torrentproperties.setTorrentRatioLimit
import org.equeim.tremotesf.rpc.requests.torrentproperties.setTorrentRatioLimitMode
import org.equeim.tremotesf.rpc.requests.torrentproperties.setTorrentUploadSpeedLimit
import org.equeim.tremotesf.rpc.requests.torrentproperties.setTorrentUploadSpeedLimited
import org.equeim.tremotesf.rpc.stateIn
import org.equeim.tremotesf.ui.navController
import org.equeim.tremotesf.ui.utils.ArrayDropdownAdapter
import org.equeim.tremotesf.ui.utils.DecimalFormats
import org.equeim.tremotesf.ui.utils.DoubleFilter
import org.equeim.tremotesf.ui.utils.IntFilter
import org.equeim.tremotesf.ui.utils.doAfterTextChangedAndNotEmpty
import org.equeim.tremotesf.ui.utils.hide
import org.equeim.tremotesf.ui.utils.hideKeyboard
import org.equeim.tremotesf.ui.utils.launchAndCollectWhenStarted
import org.equeim.tremotesf.ui.utils.setDependentViews
import org.equeim.tremotesf.ui.utils.showError
import org.equeim.tremotesf.ui.utils.showLoading
import org.equeim.tremotesf.ui.utils.viewLifecycleObject
import timber.log.Timber
import kotlin.time.Duration.Companion.minutes


class TorrentLimitsFragment :
    TorrentPropertiesFragment.PagerFragment(
        R.layout.torrent_limits_fragment,
        TorrentPropertiesFragment.PagerAdapter.Tab.Limits
    ) {
    private companion object {
        const val MAX_SPEED_LIMIT = 4 * 1024 * 1024 // kilobytes per second
        const val MAX_RATIO_LIMIT = 10000.0
        const val MAX_IDLE_SEEDING_LIMIT = 10000 // minutes
        const val MAX_PEERS = 10000

        // Should match R.array.priority_items
        val priorityItems = arrayOf(
            TorrentLimits.BandwidthPriority.High,
            TorrentLimits.BandwidthPriority.Normal,
            TorrentLimits.BandwidthPriority.Low
        )

        // Should match R.array.ratio_limit_mode
        val ratioLimitModeItems = arrayOf(
            TorrentLimits.RatioLimitMode.Global,
            TorrentLimits.RatioLimitMode.Unlimited,
            TorrentLimits.RatioLimitMode.Single
        )

        // Should match R.array.idle_seeding_mode
        val idleSeedingModeItems = arrayOf(
            TorrentLimits.IdleSeedingLimitMode.Global,
            TorrentLimits.IdleSeedingLimitMode.Unlimited,
            TorrentLimits.IdleSeedingLimitMode.Single
        )
    }

    private val torrentHashString: String by lazy { TorrentPropertiesFragment.getTorrentHashString(navController) }
    private val model by viewModels<TorrentLimitsFragmentViewModel> {
        viewModelFactory {
            initializer {
                TorrentLimitsFragmentViewModel(torrentHashString)
            }
        }
    }

    private lateinit var priorityItemValues: Array<String>
    private lateinit var ratioLimitModeItemValues: Array<String>
    private lateinit var idleSeedingModeItemValues: Array<String>

    private lateinit var doubleFilter: DoubleFilter

    private val binding by viewLifecycleObject(TorrentLimitsFragmentBinding::bind)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        priorityItemValues = resources.getStringArray(R.array.priority_items)
        ratioLimitModeItemValues = resources.getStringArray(R.array.ratio_limit_mode)
        idleSeedingModeItemValues = resources.getStringArray(R.array.idle_seeding_mode)
        doubleFilter = DoubleFilter(0.0..MAX_RATIO_LIMIT)
    }

    override fun onViewStateRestored(savedInstanceState: Bundle?) {
        super.onViewStateRestored(savedInstanceState)

        with(binding) {
            scrollView.isEnabled = false

            downloadSpeedLimitEdit.filters = arrayOf(IntFilter(0 until MAX_SPEED_LIMIT))

            uploadSpeedLimitEdit.filters = arrayOf(IntFilter(0 until MAX_SPEED_LIMIT))

            priorityView.setAdapter(ArrayDropdownAdapter(priorityItemValues))

            ratioLimitModeView.setAdapter(ArrayDropdownAdapter(ratioLimitModeItemValues))
            ratioLimitEdit.filters = arrayOf(doubleFilter)

            idleSeedingModeView.setAdapter(ArrayDropdownAdapter(idleSeedingModeItemValues))
            idleSeedingLimitEdit.filters = arrayOf(IntFilter(0..MAX_IDLE_SEEDING_LIMIT))

            maximumPeersEdit.filters = arrayOf(IntFilter(0..MAX_PEERS))

            globalLimitsCheckBox.setOnCheckedChangeListener { _, checked ->
                onValueChanged { setTorrentHonorSessionLimits(it, checked) }
            }

            downloadSpeedLimitCheckBox.setDependentViews(downloadSpeedLimitLayout) { checked ->
                onValueChanged { setTorrentDownloadSpeedLimited(it, checked) }
            }
            downloadSpeedLimitEdit.doAfterTextChangedAndNotEmpty { text ->
                onValueChanged {
                    try {
                        val transferRate = org.equeim.tremotesf.rpc.requests.TransferRate.fromKiloBytesPerSecond(text.toString().toLong())
                        setTorrentDownloadSpeedLimit(it, transferRate)
                    } catch (e: NumberFormatException) {
                        Timber.e(e, "Failed to parse download speed limit $it")
                    }
                }
            }

            uploadSpeedLimitCheckBox.setDependentViews(uploadSpeedLimitLayout) { checked ->
                onValueChanged { setTorrentUploadSpeedLimited(it, checked) }
            }
            uploadSpeedLimitEdit.doAfterTextChangedAndNotEmpty { text ->
                onValueChanged {
                    try {
                        val transferRate = org.equeim.tremotesf.rpc.requests.TransferRate.fromKiloBytesPerSecond(text.toString().toLong())
                        setTorrentUploadSpeedLimit(it, transferRate)
                    } catch (e: NumberFormatException) {
                        Timber.e(e, "Failed to parse upload speed limit $it")
                    }
                }
            }

            priorityView.setOnItemClickListener { _, _, position, _ ->
                onValueChanged {
                    setTorrentBandwidthPriority(it, priorityItems[position])
                }
            }

            ratioLimitModeView.setOnItemClickListener { _, _, position, _ ->
                val mode = ratioLimitModeItems[position]
                ratioLimitLayout.isEnabled = mode == TorrentLimits.RatioLimitMode.Single
                onValueChanged {
                    setTorrentRatioLimitMode(it, mode)
                }
            }
            ratioLimitEdit.doAfterTextChangedAndNotEmpty { text ->
                onValueChanged {
                    val limit = doubleFilter.parseOrNull(text.toString())
                    if (limit != null) {
                        setTorrentRatioLimit(it, limit)
                    } else {
                        Timber.e("Failed to parse ratio limit $it")
                    }
                }
            }

            idleSeedingModeView.setOnItemClickListener { _, _, position, _ ->
                val mode = idleSeedingModeItems[position]
                idleSeedingLimitLayout.isEnabled = mode == TorrentLimits.IdleSeedingLimitMode.Single
                onValueChanged { setTorrentIdleSeedingLimitMode(it, mode) }
            }
            idleSeedingLimitEdit.doAfterTextChangedAndNotEmpty { text ->
                onValueChanged {
                    try {
                        setTorrentIdleSeedingLimit(it, text.toString().toInt().minutes)
                    } catch (e: NumberFormatException) {
                        Timber.e(e, "Failed to parse idle seeding limit $it")
                    }
                }
            }

            maximumPeersEdit.doAfterTextChangedAndNotEmpty { text ->
                onValueChanged {
                    try {
                        setTorrentPeersLimit(it, text.toString().toInt())
                    } catch (e: NumberFormatException) {
                        Timber.e(e, "Failed to parse peers limit $it")
                    }
                }
            }
        }

        model.limits.launchAndCollectWhenStarted(viewLifecycleOwner) {
            when (it) {
                is RpcRequestState.Loaded -> it.response?.let(::showLimits)
                    ?: showPlaceholder { showError(getText(R.string.torrent_not_found)) }

                is RpcRequestState.Loading -> showPlaceholder { showLoading() }
                is RpcRequestState.Error -> showPlaceholder { showError(it.error) }
            }
        }
    }

    private inline fun showPlaceholder(show: PlaceholderLayoutBinding.() -> Unit) {
        hideKeyboard()
        with(binding) {
            scrollView.isVisible = false
            placeholderView.show()
        }
    }

    private fun showLimits(limit: TorrentLimits) {
        with(binding) {
            scrollView.isVisible = true
            placeholderView.hide()
        }
        if (model.shouldSetInitialState) {
            setInitialState(limit)
            model.shouldSetInitialState = false
        }
    }

    private fun setInitialState(limits: TorrentLimits) = with(binding) {
        globalLimitsCheckBox.isChecked = limits.honorsSessionLimits
        downloadSpeedLimitCheckBox.isChecked = limits.downloadSpeedLimited
        downloadSpeedLimitEdit.setText(limits.downloadSpeedLimit.kiloBytesPerSecond.toString())
        uploadSpeedLimitCheckBox.isChecked = limits.uploadSpeedLimited
        uploadSpeedLimitEdit.setText(limits.uploadSpeedLimit.kiloBytesPerSecond.toString())
        priorityView.setText(priorityItemValues[priorityItems.indexOf(limits.bandwidthPriority)])
        ratioLimitModeView.setText(ratioLimitModeItemValues[ratioLimitModeItems.indexOf(limits.ratioLimitMode)])
        ratioLimitEdit.setText(DecimalFormats.ratio.format(limits.ratioLimit))
        ratioLimitLayout.isEnabled = limits.ratioLimitMode == TorrentLimits.RatioLimitMode.Single
        idleSeedingModeView.setText(
            idleSeedingModeItemValues[idleSeedingModeItems.indexOf(
                limits.idleSeedingLimitMode
            )]
        )
        idleSeedingLimitEdit.setText(limits.idleSeedingLimit.inWholeMinutes.toString())
        idleSeedingLimitLayout.isEnabled = limits.idleSeedingLimitMode == TorrentLimits.IdleSeedingLimitMode.Single

        maximumPeersEdit.setText(limits.peersLimit.toString())
    }

    private fun onValueChanged(performRpcRequest: suspend RpcClient.(String) -> Unit) {
        if (!model.shouldSetInitialState) {
            val hashString = torrentHashString
            GlobalRpcClient.performBackgroundRpcRequest(R.string.set_torrent_limits_error) {
                performRpcRequest(
                    hashString
                )
            }
        }
    }
}

class TorrentLimitsFragmentViewModel(torrentHashString: String) : ViewModel() {
    var shouldSetInitialState = true
    val limits: StateFlow<RpcRequestState<TorrentLimits?>> =
        GlobalRpcClient.performRecoveringRequest { getTorrentLimits(torrentHashString) }
            .onEach { if (it !is RpcRequestState.Loaded) shouldSetInitialState = true }
            .stateIn(GlobalRpcClient, viewModelScope)
}
