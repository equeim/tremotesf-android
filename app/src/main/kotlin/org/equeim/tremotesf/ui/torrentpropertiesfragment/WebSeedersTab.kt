// SPDX-FileCopyrightText: 2017-2025 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package org.equeim.tremotesf.ui.torrentpropertiesfragment

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import org.equeim.tremotesf.R
import org.equeim.tremotesf.rpc.RpcRequestError
import org.equeim.tremotesf.rpc.RpcRequestState
import org.equeim.tremotesf.ui.ComponentPreview
import org.equeim.tremotesf.ui.Dimens
import org.equeim.tremotesf.ui.components.TremotesfErrorPlaceholder
import org.equeim.tremotesf.ui.components.TremotesfScreenContentWithPlaceholder
import org.equeim.tremotesf.ui.utils.rememberAlphanumericComparator

@Composable
fun WebSeedersTab(
    innerPadding: PaddingValues,
    webSeeders: StateFlow<RpcRequestState<List<String>>>,
    toolbarClicked: Flow<Unit>,
    navigateToDetailedErrorDialog: (RpcRequestError) -> Unit
) {
    val webSeeders = webSeeders.collectAsStateWithLifecycle()
    TremotesfScreenContentWithPlaceholder(
        requestState = webSeeders.value,
        onShowDetailedErrorButtonClicked = navigateToDetailedErrorDialog,
        modifier = Modifier.fillMaxSize(),
        placeholdersModifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(innerPadding)
            .padding(Dimens.screenContentPadding())
    ) { webSeeders ->
        if (webSeeders.isEmpty()) {
            TremotesfErrorPlaceholder(
                error = stringResource(R.string.no_web_seeders),
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

        val comparator = rememberAlphanumericComparator()
        val sortedWebSeeders = remember { derivedStateOf { webSeeders.sortedWith(comparator) } }

        LazyColumn(
            state = listState,
            contentPadding = innerPadding,
            modifier = Modifier.fillMaxSize()
        ) {
            items(
                items = sortedWebSeeders.value,
                key = { it }
            ) { webSeeder ->
                Column {
                    ListItem(headlineContent = { Text(webSeeder) })
                }
            }
        }
    }
}

@Preview
@Composable
private fun WebSeedersTabPreview() = ComponentPreview {
    WebSeedersTab(
        innerPadding = PaddingValues(),
        webSeeders = remember { MutableStateFlow(RpcRequestState.Loaded(listOf("http://example.com"))) },
        toolbarClicked = remember { emptyFlow() },
        navigateToDetailedErrorDialog = {}
    )
}
