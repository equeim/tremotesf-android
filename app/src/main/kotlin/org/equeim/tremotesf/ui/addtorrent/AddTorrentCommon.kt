// SPDX-FileCopyrightText: 2017-2025 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package org.equeim.tremotesf.ui.addtorrent

import android.content.Context
import android.content.res.Resources
import android.os.Parcelable
import android.view.View
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.get
import androidx.navigation.NavController
import kotlinx.parcelize.Parcelize
import org.equeim.tremotesf.R
import org.equeim.tremotesf.rpc.requests.torrentproperties.TorrentLimits
import org.equeim.tremotesf.ui.Dimens
import org.equeim.tremotesf.ui.ScreenPreview
import org.equeim.tremotesf.ui.addtorrent.BaseAddTorrentModel.DownloadDirectoryFreeSpace
import org.equeim.tremotesf.ui.components.DialogPadding
import org.equeim.tremotesf.ui.components.DownloadDirectoryItem
import org.equeim.tremotesf.ui.components.TremotesfAlertDialogWithoutTextPadding
import org.equeim.tremotesf.ui.components.TremotesfComboBox
import org.equeim.tremotesf.ui.components.TremotesfDownloadDirectoryField
import org.equeim.tremotesf.ui.components.TremotesfLabelsEditor
import org.equeim.tremotesf.ui.components.TremotesfSectionHeader
import org.equeim.tremotesf.ui.components.TremotesfSwitchWithText
import org.equeim.tremotesf.ui.torrentslistfragment.TorrentsListFragmentViewModel
import org.equeim.tremotesf.ui.utils.rememberFileSizeFormatter

@Suppress("UnusedReceiverParameter")
@Composable
fun ColumnScope.CommonAddTorrentParameters(
    downloadDirectory: MutableState<String>,
    showDownloadDirectoryError: MutableState<Boolean>,
    allDownloadDirectories: SnapshotStateList<DownloadDirectoryItem>,
    downloadDirectoryFreeSpace: State<DownloadDirectoryFreeSpace?>,
    priority: MutableState<TorrentLimits.BandwidthPriority>,
    startAddedTorrents: MutableState<Boolean>,
    enabledLabels: SnapshotStateList<String>,
    allLabels: State<List<String>>,
    shouldShowLabels: State<Boolean>
) {
    val horizontalPadding = Dimens.screenContentPaddingHorizontal()

    TremotesfDownloadDirectoryField(
        downloadDirectory = downloadDirectory.value,
        onDownloadDirectoryChanged = {
            downloadDirectory.value = it
            if (it.isNotBlank()) {
                showDownloadDirectoryError.value = false
            }
        },
        allDownloadDirectories = allDownloadDirectories,
        removeDownloadDirectory = allDownloadDirectories::remove,
        label = R.string.download_directory,
        imeAction = ImeAction.Next,
        isError = showDownloadDirectoryError.value,
        supportingText = if (showDownloadDirectoryError.value) {
            R.string.empty_field_error
        } else {
            0
        },
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = horizontalPadding)
    )

    downloadDirectoryFreeSpace.value?.let { freeSpace ->
        val context = LocalContext.current
        val formatter = rememberFileSizeFormatter()
        val text = when (freeSpace) {
            is DownloadDirectoryFreeSpace.FreeSpace -> remember(formatter, freeSpace) {
                context.getString(R.string.free_space, formatter.formatFileSize(freeSpace.size))
            }

            is DownloadDirectoryFreeSpace.Error -> stringResource(R.string.free_space_error)
        }
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = horizontalPadding)
        )
    }

    TremotesfComboBox(
        currentItem = priority::value,
        updateCurrentItem = priority::value::set,
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
    )

    TremotesfSwitchWithText(
        checked = startAddedTorrents.value,
        onCheckedChange = startAddedTorrents::value::set,
        text = R.string.start_downloading_after_adding,
        horizontalContentPadding = horizontalPadding,
        modifier = Modifier.fillMaxWidth()
    )

    TremotesfSectionHeader(
        R.string.labels,
        modifier = Modifier
            .padding(horizontal = horizontalPadding)
            .padding(top = Dimens.SpacingSmall)
    )

    if (shouldShowLabels.value) {
        TremotesfLabelsEditor(
            enabledLabels = enabledLabels,
            removeLabel = enabledLabels::remove,
            addLabel = enabledLabels::add,
            allLabels = allLabels::value,
            modifier = Modifier
                .padding(horizontal = horizontalPadding)
                .padding(top = Dimens.SpacingSmall)
        )
    }
}

@Parcelize
sealed interface AddTorrentState : Parcelable {
    // Intermediary states
    data object CheckingIfTorrentExists : AddTorrentState
    data class AskForMergingTrackers(val torrentNames: List<String>) : AddTorrentState

    // Terminal state
    data class Finished(val mergingTrackersMessage: MergingTrackersMessage? = null) : AddTorrentState
}

@Composable
fun HandleFinishedAddTorrentState(
    state: State<AddTorrentState?>,
    navController: NavController,
    activity: ComponentActivity
) {
    val state = state.value
    if (state is AddTorrentState.Finished) {
        LaunchedEffect(null) {
            if (state.mergingTrackersMessage != null) {
                showMergingTrackersMessageAfterAddingTorrent(navController, activity, state.mergingTrackersMessage)
            }
            activity.onBackPressedDispatcher.onBackPressed()
        }
    }
}

private fun showMergingTrackersMessageAfterAddingTorrent(
    navController: NavController,
    context: Context,
    message: MergingTrackersMessage
) {
    val torrentsListScreenViewModel = try {
        ViewModelProvider.create(navController.getBackStackEntry(R.id.torrents_list_fragment))
            .get<TorrentsListFragmentViewModel>()
    } catch (_: IllegalArgumentException) {
        null
    }
    if (torrentsListScreenViewModel != null) {
        torrentsListScreenViewModel.showMergingTrackersMessage.value = message
    } else {
        Toast.makeText(
            context,
            message.getDisplayText(context.resources),
            Toast.LENGTH_SHORT
        ).show()
    }
}

@Parcelize
data class MergingTrackersMessage(private val merging: Boolean, val torrentNames: List<String>) : Parcelable {
    fun getDisplayText(resources: Resources): String {
        return if (torrentNames.size == 1) {
            resources.getString(
                if (merging) R.string.torrent_duplicate_merging_trackers else R.string.torrent_duplicate_not_merging_trackers,
                torrentNames.first()
            )
        } else {
            resources.getString(
                if (merging) R.string.torrents_duplicates_merging_trackers else R.string.torrents_duplicates_not_merging_trackers,
                buildMultipleTorrentsListString(
                    torrentNames = torrentNames,
                    isRtl = resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL
                )
            )
        }
    }
}

@Composable
fun ShowMergingTrackersMessage(
    mergingTrackersMessage: MutableState<MergingTrackersMessage?>,
    snackbarHostState: SnackbarHostState
) {
    val messageString = mergingTrackersMessage.value?.let {
        val resources = LocalResources.current
        remember(it, resources) { it.getDisplayText(resources) }
    }
    if (messageString != null) {
        LaunchedEffect(messageString) {
            try {
                snackbarHostState.showSnackbar(
                    message = messageString,
                    withDismissAction = true,
                    duration = SnackbarDuration.Short
                )
            } finally {
                mergingTrackersMessage.value = null
            }
        }
    }
}

sealed interface MergeTrackersDialogResult {
    data class ButtonClicked(
        val merge: Boolean,
        val doNotAskAgain: Boolean,
    ) : MergeTrackersDialogResult

    data object Cancelled : MergeTrackersDialogResult
}

@Composable
fun MergingTrackersDialog(
    torrentNames: List<String>,
    cancellable: Boolean,
    onMergeTrackersDialogResult: (MergeTrackersDialogResult) -> Unit,
) {
    var doNotAskAgain: Boolean by rememberSaveable { mutableStateOf(false) }
    TremotesfAlertDialogWithoutTextPadding(
        onDismissRequest = {
            if (cancellable) {
                onMergeTrackersDialogResult(MergeTrackersDialogResult.Cancelled)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.SpacingBig)) {
                Text(
                    text = if (torrentNames.size == 1) {
                        stringResource(R.string.torrent_duplicate_merging_trackers_question, torrentNames.first())
                    } else {
                        val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
                        val torrents = remember(torrentNames, isRtl) {
                            buildMultipleTorrentsListString(
                                torrentNames = torrentNames,
                                isRtl = isRtl
                            )
                        }
                        stringResource(R.string.torrents_duplicates_merging_trackers_question, torrents)
                    },
                    modifier = Modifier.padding(horizontal = DialogPadding)
                )
                CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurfaceVariant) {
                    TremotesfSwitchWithText(
                        checked = doNotAskAgain,
                        onCheckedChange = { doNotAskAgain = it },
                        text = R.string.do_not_ask_again,
                        horizontalContentPadding = DialogPadding,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        textContentColor = MaterialTheme.colorScheme.onSurface,
        confirmButton = {
            TextButton(onClick = {
                onMergeTrackersDialogResult(
                    MergeTrackersDialogResult.ButtonClicked(merge = true, doNotAskAgain = doNotAskAgain)
                )
            }) {
                Text(stringResource(R.string.merge_yes))
            }
        },
        dismissButton = {
            TextButton(onClick = {
                onMergeTrackersDialogResult(
                    MergeTrackersDialogResult.ButtonClicked(merge = false, doNotAskAgain = doNotAskAgain)
                )
            }) {
                Text(stringResource(R.string.merge_no))
            }
        },
        properties = DialogProperties(
            dismissOnBackPress = cancellable,
            dismissOnClickOutside = cancellable
        )
    )
}

@Preview
@Composable
fun MergingTrackersDialogPreview() = ScreenPreview {
    MergingTrackersDialog(
        torrentNames = listOf("Bzzt"),
        cancellable = true,
        onMergeTrackersDialogResult = {}
    )
}

@Preview
@Composable
fun MergingTrackersDialogPreviewSeveralTorrents() = ScreenPreview {
    MergingTrackersDialog(
        torrentNames = listOf("Bzzt", "Hmm", "Foo"),
        cancellable = true,
        onMergeTrackersDialogResult = {}
    )
}

private fun buildMultipleTorrentsListString(torrentNames: List<String>, isRtl: Boolean): String = buildString {
    for ((index, torrentName) in torrentNames.withIndex()) {
        if (isRtl) {
            append(torrentName)
            append('\u00a0') // NBSP
            append('\u2022') // Dot
        } else {
            append('\u2022') // Dot
            append('\u00a0') // NBSP
            append(torrentName)
        }
        if (index != torrentNames.lastIndex) {
            append('\n')
        }
    }
}
