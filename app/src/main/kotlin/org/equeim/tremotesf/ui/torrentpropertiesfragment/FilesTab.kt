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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.Flow
import org.equeim.tremotesf.R
import org.equeim.tremotesf.rpc.RpcRequestError
import org.equeim.tremotesf.rpc.requests.FileSize
import org.equeim.tremotesf.torrentfile.TorrentFilesTree
import org.equeim.tremotesf.ui.Dimens
import org.equeim.tremotesf.ui.components.TremotesfErrorPlaceholder
import org.equeim.tremotesf.ui.components.TremotesfLoadingPlaceholder
import org.equeim.tremotesf.ui.components.TremotesfTorrentsFilesList
import org.equeim.tremotesf.ui.utils.rememberFileSizeFormatter
import java.text.DecimalFormat

@Composable
fun FilesTab(
    innerPadding: PaddingValues,
    filesTree: TorrentFilesTree,
    filesTreeState: State<TorrentPropertiesFragmentViewModel.FilesTreeState>,
    toolbarClicked: Flow<Unit>,
    navigateToDetailedErrorDialog: (RpcRequestError) -> Unit
) {
    when (val state = filesTreeState.value) {
        is TorrentPropertiesFragmentViewModel.FilesTreeState.Loading -> TremotesfLoadingPlaceholder(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(innerPadding)
                .padding(Dimens.screenContentPadding())
        )

        is TorrentPropertiesFragmentViewModel.FilesTreeState.Error -> TremotesfErrorPlaceholder(
            error = state.error,
            onShowDetailedErrorButtonClicked = navigateToDetailedErrorDialog,
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(innerPadding)
                .padding(Dimens.screenContentPadding())
        )

        is TorrentPropertiesFragmentViewModel.FilesTreeState.Loaded -> {
            if (!state.torrentHasFiles) {
                TremotesfErrorPlaceholder(
                    error = stringResource(R.string.no_files),
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(innerPadding)
                        .padding(Dimens.screenContentPadding())
                )
                return
            }

            val listState = rememberLazyListState()
            LaunchedEffect(toolbarClicked) {
                toolbarClicked.collect {
                    listState.scrollToItem(0)
                }
            }
            val fileSizeFormatter = rememberFileSizeFormatter()
            val progressFormatter = remember(LocalConfiguration.current.locales) { DecimalFormat("0.#") }
            TremotesfTorrentsFilesList(
                filesTree = filesTree,
                listState = listState,
                contentPadding = innerPadding,
                itemSupportingContent = {
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Dimens.SpacingSmall)) {
                        LinearProgressIndicator(progress = { it.progress }, gapSize = 0.dp, drawStopIndicator = {})
                        Text(
                            stringResource(
                                R.string.completed_string,
                                fileSizeFormatter.formatFileSize(FileSize.fromBytes(it.completedSize)),
                                fileSizeFormatter.formatFileSize(FileSize.fromBytes(it.size)),
                                progressFormatter.format(it.progress * 100)
                            )
                        )
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
