// SPDX-FileCopyrightText: 2017-2025 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package org.equeim.tremotesf.ui.addtorrent

import android.content.Context
import android.os.Parcelable
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
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
import org.equeim.tremotesf.ui.components.DownloadDirectoryItem
import org.equeim.tremotesf.ui.components.TremotesfComboBox
import org.equeim.tremotesf.ui.components.TremotesfDownloadDirectoryField
import org.equeim.tremotesf.ui.components.TremotesfLabelsEditor
import org.equeim.tremotesf.ui.components.TremotesfSectionHeader
import org.equeim.tremotesf.ui.components.TremotesfSwitchWithText
import org.equeim.tremotesf.ui.torrentslistfragment.TorrentsListFragmentViewModel
import org.equeim.tremotesf.ui.utils.rememberFileSizeFormatter

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
    data class AskForMergingTrackers(val torrentName: String) : AddTorrentState

    // Terminal states
    data object AddedTorrent : AddTorrentState
    data class MergedTrackers(val torrentName: String, val showMessage: Boolean) : AddTorrentState
    data class DidNotMergeTrackers(val torrentName: String, val showMessage: Boolean) : AddTorrentState
}

@Composable
fun HandleTerminalAddTorrentState(state: State<AddTorrentState?>, navController: NavController, activity: ComponentActivity) {
    val performBackPress = { activity.onBackPressedDispatcher.onBackPressed() }
    when (val state = state.value) {
        is AddTorrentState.AddedTorrent ->
            LaunchedEffect(null) { performBackPress() }

        is AddTorrentState.DidNotMergeTrackers -> LaunchedEffect(null) {
            if (state.showMessage) {
                showMergingTrackersMessage(
                    navController,
                    activity,
                    MergingTrackersMessage(merging = false, torrentName = state.torrentName)
                )
            }
            performBackPress()
        }

        is AddTorrentState.MergedTrackers -> LaunchedEffect(null) {
            if (state.showMessage) {
                showMergingTrackersMessage(
                    navController,
                    activity,
                    MergingTrackersMessage(merging = true, torrentName = state.torrentName)
                )
            }
            performBackPress()
        }

        else -> Unit
    }
}

data class MergingTrackersMessage(private val merging: Boolean, val torrentName: String) {
    val stringId: Int
        get() = if (merging) R.string.torrent_duplicate_merging_trackers else R.string.torrent_duplicate_not_merging_trackers
}

private fun showMergingTrackersMessage(
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
            context.getString(
                message.stringId,
                message.torrentName
            ),
            Toast.LENGTH_SHORT
        ).show()
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
    torrentName: String,
    cancellable: Boolean,
    onMergeTrackersDialogResult: (MergeTrackersDialogResult) -> Unit,
) {
    var doNotAskAgain: Boolean by rememberSaveable { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = {
            if (cancellable) {
                onMergeTrackersDialogResult(MergeTrackersDialogResult.Cancelled)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.SpacingSmall)) {
                Text(stringResource(R.string.torrent_duplicate_merging_trackers_question, torrentName))
                TremotesfSwitchWithText(
                    checked = doNotAskAgain,
                    onCheckedChange = { doNotAskAgain = it },
                    text = R.string.do_not_ask_again,
                    horizontalContentPadding = Dimens.SpacingSmall,
                    modifier = Modifier.fillMaxWidth()
                )
            }

        },
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
        torrentName = "Bzzt",
        cancellable = true,
        onMergeTrackersDialogResult = {}
    )
}