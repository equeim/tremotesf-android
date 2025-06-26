// SPDX-FileCopyrightText: 2017-2025 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package org.equeim.tremotesf.ui.torrentslistfragment

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.cheonjaeung.compose.grid.GridScope
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.StateFlow
import org.equeim.tremotesf.R
import org.equeim.tremotesf.rpc.GlobalRpcClient
import org.equeim.tremotesf.rpc.RpcRequestError
import org.equeim.tremotesf.rpc.RpcRequestState
import org.equeim.tremotesf.rpc.performPeriodicRequest
import org.equeim.tremotesf.rpc.requests.FileSize
import org.equeim.tremotesf.rpc.requests.SessionStatsResponseArguments
import org.equeim.tremotesf.rpc.requests.TransferRate
import org.equeim.tremotesf.rpc.requests.getDownloadDirFreeSpace
import org.equeim.tremotesf.rpc.requests.getSessionStats
import org.equeim.tremotesf.rpc.stateIn
import org.equeim.tremotesf.ui.ComposeDialogFragment
import org.equeim.tremotesf.ui.Dimens
import org.equeim.tremotesf.ui.ScreenPreview
import org.equeim.tremotesf.ui.components.TremotesfAlertDialogContent
import org.equeim.tremotesf.ui.components.TremotesfDetailsGrid
import org.equeim.tremotesf.ui.components.TremotesfScreenContentWithPlaceholder
import org.equeim.tremotesf.ui.components.TremotesfSectionHeader
import org.equeim.tremotesf.ui.navigateToDetailedErrorDialog
import org.equeim.tremotesf.ui.utils.FileSizeFormatter
import org.equeim.tremotesf.ui.utils.formatDuration
import org.equeim.tremotesf.ui.utils.rememberFileSizeFormatter
import java.text.DecimalFormat
import kotlin.time.Duration.Companion.days

class ServerStatsDialogFragment : ComposeDialogFragment() {
    @Composable
    override fun Content(navController: NavController) {
        val model = viewModel<ServerStatsDialogFragmentViewModel>()
        ServerStatsDialogContent(
            uiState = model.uiState.collectAsStateWithLifecycle().value,
            navigateToDetailedErrorDialog = navController::navigateToDetailedErrorDialog,
            onDismissRequest = ::dismiss
        )
    }
}

@Composable
private fun ServerStatsDialogContent(
    uiState: RpcRequestState<ServerStatsDialogFragmentViewModel.UiState>,
    navigateToDetailedErrorDialog: (RpcRequestError) -> Unit,
    onDismissRequest: () -> Unit
) {
    TremotesfAlertDialogContent(
        title = { Text(stringResource(R.string.server_stats)) },
        text = {
            TremotesfScreenContentWithPlaceholder(
                requestState = uiState,
                onShowDetailedErrorButtonClicked = navigateToDetailedErrorDialog,
                placeholdersModifier = Modifier.fillMaxWidth()
            ) { uiState ->
                TremotesfDetailsGrid(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                    val fileSizeFormatter = rememberFileSizeFormatter()
                    val ratioFormatter = remember(LocalConfiguration.current.locales) { DecimalFormat("0.00") }

                    TremotesfSectionHeader(R.string.current_session, Modifier.span { maxLineSpan })

                    CompositionLocalProvider(LocalTextStyle provides MaterialTheme.typography.bodyMedium) {
                        CommonStats(uiState.sessionStats.currentSession, fileSizeFormatter, ratioFormatter)
                    }

                    TremotesfSectionHeader(
                        R.string.total,
                        Modifier
                            .span { maxLineSpan }
                            .padding(top = Dimens.SpacingSmall)
                    )

                    CompositionLocalProvider(LocalTextStyle provides MaterialTheme.typography.bodyMedium) {
                        val total = uiState.sessionStats.total
                        Text(
                            text = pluralStringResource(R.plurals.started_times, total.sessionCount, total.sessionCount),
                            modifier = Modifier.span { maxLineSpan }
                        )
                        CommonStats(total, fileSizeFormatter, ratioFormatter)
                        Text(stringResource(R.string.free_space_in_download_directory))
                        Text(fileSizeFormatter.formatFileSize(uiState.downloadDirFreeSpace))
                    }
                }
            }
        },
        buttons = { TextButton(onClick = onDismissRequest) { Text(stringResource(R.string.close)) } }
    )
}

@Composable
private fun GridScope.CommonStats(
    stats: SessionStatsResponseArguments.Stats,
    fileSizeFormatter: FileSizeFormatter,
    ratioFormatter: DecimalFormat
) {
    Text(stringResource(R.string.downloaded))
    Text(fileSizeFormatter.formatFileSize(stats.downloaded))

    Text(stringResource(R.string.uploaded))
    Text(fileSizeFormatter.formatFileSize(stats.uploaded))

    Text(stringResource(R.string.ratio))
    Text(ratioFormatter.format(stats.uploaded.bytes.toDouble() / stats.downloaded.bytes.toDouble()))

    Text(stringResource(R.string.duration))
    Text(formatDuration(stats.active))
}

@Preview
@Composable
private fun ServerStatsDialogPreview() = ScreenPreview {
    ServerStatsDialogContent(
        uiState = remember {
            RpcRequestState.Loaded(
                ServerStatsDialogFragmentViewModel.UiState(
                    sessionStats = SessionStatsResponseArguments(
                        downloadSpeed = TransferRate.fromKiloBytesPerSecond(666),
                        uploadSpeed = TransferRate.fromKiloBytesPerSecond(7777777),
                        currentSession = SessionStatsResponseArguments.Stats(
                            downloaded = FileSize.fromBytes(4535445),
                            uploaded = FileSize.fromBytes(454533),
                            sessionCount = 0,
                            active = 69.days
                        ),
                        total = SessionStatsResponseArguments.Stats(
                            downloaded = FileSize.fromBytes(896809649805),
                            uploaded = FileSize.fromBytes(4238432854),
                            sessionCount = 666,
                            active = 666.days
                        )
                    ),
                    downloadDirFreeSpace = FileSize.fromBytes(999999999999)
                )
            )
        },
        navigateToDetailedErrorDialog = {},
        onDismissRequest = {}
    )
}

class ServerStatsDialogFragmentViewModel : ViewModel() {
    data class UiState(
        val sessionStats: SessionStatsResponseArguments,
        val downloadDirFreeSpace: FileSize
    )

    val uiState: StateFlow<RpcRequestState<UiState>> =
        GlobalRpcClient.performPeriodicRequest {
            coroutineScope {
                val stats = async { getSessionStats() }
                val freeSpace = async { getDownloadDirFreeSpace() }
                UiState(stats.await(), freeSpace.await())
            }
        }.stateIn(GlobalRpcClient, viewModelScope)
}
