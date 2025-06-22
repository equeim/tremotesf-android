// SPDX-FileCopyrightText: 2017-2025 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package org.equeim.tremotesf.ui.torrentpropertiesfragment

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import org.equeim.tremotesf.R
import org.equeim.tremotesf.rpc.RpcRequestError
import org.equeim.tremotesf.rpc.RpcRequestState
import org.equeim.tremotesf.rpc.requests.TransferRate
import org.equeim.tremotesf.rpc.requests.torrentproperties.TorrentLimits
import org.equeim.tremotesf.ui.ComponentPreview
import org.equeim.tremotesf.ui.Dimens
import org.equeim.tremotesf.ui.components.NON_NEGATIVE_DECIMALS_RANGE
import org.equeim.tremotesf.ui.components.TremotesfComboBox
import org.equeim.tremotesf.ui.components.TremotesfNumberInputField
import org.equeim.tremotesf.ui.components.TremotesfScreenContentWithPlaceholder
import org.equeim.tremotesf.ui.components.TremotesfSectionHeader
import org.equeim.tremotesf.ui.components.TremotesfSwitchWithText
import org.equeim.tremotesf.ui.components.UNSIGNED_16BIT_RANGE
import org.equeim.tremotesf.ui.components.rememberTremotesfDecimalNumberInputFieldState
import org.equeim.tremotesf.ui.components.rememberTremotesfIntegerNumberInputFieldState
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes

@Composable
fun LimitsTab(
    innerPadding: PaddingValues,
    limits: RpcRequestState<TorrentLimits>,
    operations: TorrentLimitsOperations,
    navigateToDetailedErrorDialog: (RpcRequestError) -> Unit
) {
    TremotesfScreenContentWithPlaceholder(
        requestState = limits,
        onShowDetailedErrorButtonClicked = navigateToDetailedErrorDialog,
        modifier = Modifier.fillMaxSize(),
        placeholdersModifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(innerPadding)
            .padding(Dimens.screenContentPadding())
    ) { limits ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(innerPadding)
                .padding(vertical = Dimens.screenContentPaddingVertical()),
            verticalArrangement = Arrangement.spacedBy(Dimens.SpacingSmall)
        ) {
            val horizontalPadding = Dimens.screenContentPaddingHorizontal()

            TremotesfSectionHeader(R.string.speed, modifier = Modifier.padding(horizontal = horizontalPadding))

            var honorSessionLimits: Boolean by rememberSaveable { mutableStateOf(limits.honorsSessionLimits) }
            TremotesfSwitchWithText(
                checked = honorSessionLimits,
                text = R.string.honor_global_limits,
                onCheckedChange = {
                    honorSessionLimits = it
                    operations.setHonorSessionLimits(it)
                },
                modifier = Modifier.fillMaxWidth(),
                horizontalContentPadding = horizontalPadding
            )

            var downloadSpeedLimited: Boolean by rememberSaveable { mutableStateOf(limits.downloadSpeedLimited) }
            TremotesfSwitchWithText(
                checked = downloadSpeedLimited,
                text = R.string.download_noun,
                onCheckedChange = {
                    downloadSpeedLimited = it
                    operations.setDownloadSpeedLimited(it)
                },
                modifier = Modifier.fillMaxWidth(),
                horizontalContentPadding = horizontalPadding
            )

            var downloadSpeedLimit =
                rememberTremotesfIntegerNumberInputFieldState(limits.downloadSpeedLimit.kiloBytesPerSecond) {
                    operations.setDownloadSpeedLimit(TransferRate.fromKiloBytesPerSecond(it))
                }
            TremotesfNumberInputField(
                state = downloadSpeedLimit,
                range = SPEED_LIMIT_RANGE,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = horizontalPadding),
                enabled = downloadSpeedLimited,
                suffix = R.string.text_field_suffix_kbps
            )

            var uploadSpeedLimited: Boolean by rememberSaveable { mutableStateOf(limits.uploadSpeedLimited) }
            TremotesfSwitchWithText(
                checked = uploadSpeedLimited,
                text = R.string.upload_noun,
                onCheckedChange = {
                    uploadSpeedLimited = it
                    operations.setUploadSpeedLimited(it)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Dimens.SpacingSmall),
                horizontalContentPadding = horizontalPadding
            )

            var uploadSpeedLimit =
                rememberTremotesfIntegerNumberInputFieldState(limits.uploadSpeedLimit.kiloBytesPerSecond) {
                    operations.setUploadSpeedLimit(TransferRate.fromKiloBytesPerSecond(it))
                }
            TremotesfNumberInputField(
                state = uploadSpeedLimit,
                range = SPEED_LIMIT_RANGE,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = horizontalPadding),
                enabled = uploadSpeedLimited,
                suffix = R.string.text_field_suffix_kbps
            )

            var priority: TorrentLimits.BandwidthPriority by rememberSaveable { mutableStateOf(limits.bandwidthPriority) }
            TremotesfComboBox(
                currentItem = { priority },
                updateCurrentItem = {
                    priority = it
                    operations.setBandwidthPriority(it)
                },
                items = TorrentLimits.BandwidthPriority.entries,
                itemDisplayString = {
                    stringResource(
                        when (it) {
                            TorrentLimits.BandwidthPriority.Low -> R.string.low_pririty
                            TorrentLimits.BandwidthPriority.Normal -> R.string.normal_priority
                            TorrentLimits.BandwidthPriority.High -> R.string.high_priority
                        }
                    )
                },
                label = R.string.priority,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = horizontalPadding)
                    .padding(top = Dimens.SpacingSmall)
            )

            TremotesfSectionHeader(
                R.string.seeding,
                modifier = Modifier
                    .padding(horizontal = horizontalPadding)
                    .padding(top = Dimens.SpacingSmall)
            )

            var ratioLimitMode: TorrentLimits.RatioLimitMode by rememberSaveable { mutableStateOf(limits.ratioLimitMode) }
            TremotesfComboBox(
                currentItem = { ratioLimitMode },
                updateCurrentItem = {
                    ratioLimitMode = it
                    operations.setRatioLimitMode(it)
                },
                items = TorrentLimits.RatioLimitMode.entries,
                itemDisplayString = {
                    stringResource(
                        when (it) {
                            TorrentLimits.RatioLimitMode.Global -> R.string.ratio_limit_global
                            TorrentLimits.RatioLimitMode.Single -> R.string.stop_seeding_at_ratio
                            TorrentLimits.RatioLimitMode.Unlimited -> R.string.ratio_limit_unlimited
                        }
                    )
                },
                label = R.string.ratio_limit,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = horizontalPadding)
            )
            val ratioLimit = rememberTremotesfDecimalNumberInputFieldState(limits.ratioLimit) {
                operations.setRatioLimit(it)
            }
            TremotesfNumberInputField(
                state = ratioLimit,
                range = NON_NEGATIVE_DECIMALS_RANGE,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = horizontalPadding),
                enabled = ratioLimitMode == TorrentLimits.RatioLimitMode.Single
            )

            var idleSeedingLimitMode: TorrentLimits.IdleSeedingLimitMode by rememberSaveable { mutableStateOf(limits.idleSeedingLimitMode) }
            TremotesfComboBox(
                currentItem = { idleSeedingLimitMode },
                updateCurrentItem = {
                    idleSeedingLimitMode = it
                    operations.setIdleSeedingLimitMode(it)
                },
                items = TorrentLimits.IdleSeedingLimitMode.entries,
                itemDisplayString = {
                    stringResource(
                        when (it) {
                            TorrentLimits.IdleSeedingLimitMode.Global -> R.string.idle_seeding_global
                            TorrentLimits.IdleSeedingLimitMode.Single -> R.string.stop_seeding_if_idle_for
                            TorrentLimits.IdleSeedingLimitMode.Unlimited -> R.string.idle_seeding_unlimited
                        }
                    )
                },
                label = R.string.idle_seeding,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = horizontalPadding)
            )
            val idleSeedingLimit =
                rememberTremotesfIntegerNumberInputFieldState(limits.idleSeedingLimit.inWholeMinutes) {
                    operations.setIdleSeedingLimit(it.minutes)
                }
            TremotesfNumberInputField(
                state = idleSeedingLimit,
                // Transmission processes this value as unsigned 16-bit integer
                range = UNSIGNED_16BIT_RANGE,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = horizontalPadding),
                enabled = idleSeedingLimitMode == TorrentLimits.IdleSeedingLimitMode.Single
            )

            TremotesfSectionHeader(
                R.string.peers,
                modifier = Modifier
                    .padding(horizontal = horizontalPadding)
                    .padding(top = Dimens.SpacingSmall)
            )

            val peersLimit = rememberTremotesfIntegerNumberInputFieldState(limits.peersLimit) {
                operations.setPeersLimit(it)
            }
            TremotesfNumberInputField(
                state = peersLimit,
                // Transmission processes this value as unsigned 16-bit integer
                range = UNSIGNED_16BIT_RANGE,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = horizontalPadding),
                label = R.string.maximum_peers
            )
        }
    }
}

private val SPEED_LIMIT_RANGE: LongRange = 0..(TransferRate.MAX_VALUE).kiloBytesPerSecond

@Preview
@Composable
private fun LimitsTabPreview() = ComponentPreview {
    LimitsTab(
        innerPadding = PaddingValues(),
        limits = remember {
            RpcRequestState.Loaded(
                TorrentLimits(
                    honorsSessionLimits = true,
                    bandwidthPriority = TorrentLimits.BandwidthPriority.High,
                    downloadSpeedLimited = true,
                    downloadSpeedLimit = TransferRate.fromKiloBytesPerSecond(99999),
                    uploadSpeedLimited = false,
                    uploadSpeedLimit = TransferRate.fromKiloBytesPerSecond(1),
                    ratioLimit = 42.0,
                    ratioLimitMode = TorrentLimits.RatioLimitMode.Single,
                    idleSeedingLimit = 1000.days,
                    idleSeedingLimitMode = TorrentLimits.IdleSeedingLimitMode.Single,
                    peersLimit = 777
                )
            )
        },
        operations = TORRENT_LIMITS_OPERATIONS_FOR_PREVIEW,
        navigateToDetailedErrorDialog = {}
    )
}
