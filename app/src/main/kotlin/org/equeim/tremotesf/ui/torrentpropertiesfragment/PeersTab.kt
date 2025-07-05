// SPDX-FileCopyrightText: 2017-2025 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package org.equeim.tremotesf.ui.torrentpropertiesfragment

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import org.equeim.tremotesf.R
import org.equeim.tremotesf.common.AlphanumericComparator
import org.equeim.tremotesf.rpc.RpcRequestState
import org.equeim.tremotesf.rpc.requests.TransferRate
import org.equeim.tremotesf.rpc.requests.torrentproperties.Peer
import org.equeim.tremotesf.ui.ComponentPreview
import org.equeim.tremotesf.ui.Dimens
import org.equeim.tremotesf.ui.components.TremotesfErrorPlaceholder
import org.equeim.tremotesf.ui.components.TremotesfScreenContentWithPlaceholder
import org.equeim.tremotesf.ui.utils.rememberFileSizeFormatter
import org.equeim.tremotesf.ui.utils.rememberLocaleDependentValue
import org.equeim.tremotesf.ui.utils.rememberNumberFormat
import java.text.DecimalFormat

@Composable
fun PeersTab(
    innerPadding: PaddingValues,
    peers: StateFlow<RpcRequestState<List<Peer>>>,
    toolbarClicked: Flow<Unit>,
) {
    val peers = peers.collectAsStateWithLifecycle()
    TremotesfScreenContentWithPlaceholder(
        requestState = peers.value,
        modifier = Modifier.fillMaxSize(),
        placeholdersModifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(innerPadding)
            .padding(Dimens.screenContentPadding()),
        content = { peers ->
            if (peers.isEmpty()) {
                TremotesfErrorPlaceholder(
                    error = stringResource(R.string.no_peers),
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(innerPadding)
                        .padding(Dimens.screenContentPadding())
                )
                return@TremotesfScreenContentWithPlaceholder
            }

            val listState = rememberLazyListState()
            LaunchedEffect(toolbarClicked) {
                toolbarClicked.collect { listState.scrollToItem(0) }
            }

            val fileSizeFormatter = rememberFileSizeFormatter()
            val progressFormatter = rememberNumberFormat { DecimalFormat("0.#") }

            val comparator =
                rememberLocaleDependentValue { compareBy(AlphanumericComparator(), Peer::address) }
            val sortedPeers = remember { derivedStateOf { peers.sortedWith(comparator) } }

            LazyColumn(
                state = listState,
                contentPadding = innerPadding,
                modifier = Modifier.fillMaxSize()
            ) {
                items(
                    items = sortedPeers.value,
                    key = Peer::address
                ) { peer ->
                    Column {
                        ListItem(
                            headlineContent = { Text(peer.address) },
                            supportingContent = {
                                Row(Modifier.fillMaxWidth()) {
                                    Column(Modifier.weight(1.0f)) {
                                        Text(
                                            stringResource(
                                                R.string.download_speed_string,
                                                fileSizeFormatter.formatTransferRate(peer.downloadSpeed)
                                            )
                                        )
                                        Text(
                                            stringResource(
                                                R.string.upload_speed_string,
                                                fileSizeFormatter.formatTransferRate(peer.uploadSpeed)
                                            )
                                        )
                                    }
                                    Column(Modifier.weight(1.0f), horizontalAlignment = Alignment.End) {
                                        Text(
                                            stringResource(
                                                R.string.progress_string,
                                                progressFormatter.format(peer.progress)
                                            )
                                        )
                                        Text(peer.client)
                                    }
                                }
                            }
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    )
}

@Preview
@Composable
private fun PeersTabPreview() = ComponentPreview {
    PeersTab(
        innerPadding = PaddingValues(),
        peers = remember {
            MutableStateFlow(
                RpcRequestState.Loaded(
                    listOf(
                        Peer(
                            address = "127.0.0.1",
                            client = "MalwareTorrent",
                            downloadSpeed = TransferRate.fromKiloBytesPerSecond(666),
                            uploadSpeed = TransferRate.fromKiloBytesPerSecond(42000),
                            progress = 0.69,
                            flags = ""
                        )
                    )
                )
            )
        },
        toolbarClicked = remember { emptyFlow() },
    )
}
