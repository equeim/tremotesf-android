// SPDX-FileCopyrightText: 2017-2025 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package org.equeim.tremotesf.ui.torrentpropertiesfragment

import android.text.format.DateUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.util.PatternsCompat
import org.equeim.tremotesf.R
import org.equeim.tremotesf.rpc.normalizePath
import org.equeim.tremotesf.rpc.requests.FileSize
import org.equeim.tremotesf.rpc.requests.TorrentStatus
import org.equeim.tremotesf.rpc.requests.TransferRate
import org.equeim.tremotesf.rpc.requests.torrentproperties.TorrentDetails
import org.equeim.tremotesf.rpc.toNativeSeparators
import org.equeim.tremotesf.ui.ComponentPreview
import org.equeim.tremotesf.ui.Dimens
import org.equeim.tremotesf.ui.components.TremotesfDetailsGrid
import org.equeim.tremotesf.ui.components.TremotesfLabelsList
import org.equeim.tremotesf.ui.components.TremotesfSectionHeader
import org.equeim.tremotesf.ui.utils.formatTorrentEta
import org.equeim.tremotesf.ui.utils.rememberFileSizeFormatter
import timber.log.Timber
import java.text.DecimalFormat
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.math.abs
import kotlin.time.Duration.Companion.seconds

@Composable
fun DetailsTab(
    innerPadding: PaddingValues,
    torrentDetails: TorrentDetails,
    shouldShowLabels: State<Boolean>,
    navigateToLabelsEditDialog: () -> Unit
) {
    TremotesfDetailsGrid(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(innerPadding)
            .padding(Dimens.screenContentPadding())
    ) {
        val fileSizeFormatter = rememberFileSizeFormatter()
        val formatTime = rememberTimeFormatter()

        TremotesfSectionHeader(
            R.string.activity,
            modifier = Modifier.span { maxLineSpan }
        )

        CompositionLocalProvider(LocalTextStyle provides MaterialTheme.typography.bodyMedium) {
            Text(stringResource(R.string.completed))
            Text(fileSizeFormatter.formatFileSize(torrentDetails.completedSize))

            Text(stringResource(R.string.downloaded))
            Text(fileSizeFormatter.formatFileSize(torrentDetails.totalDownloaded))

            Text(stringResource(R.string.uploaded))
            Text(fileSizeFormatter.formatFileSize(torrentDetails.totalUploaded))

            Text(stringResource(R.string.ratio))
            val ratioFormatter = remember(LocalConfiguration.current.locales) { DecimalFormat("0.00") }
            Text(ratioFormatter.format(torrentDetails.ratio))

            Text(stringResource(R.string.download_speed))
            Text(fileSizeFormatter.formatTransferRate(torrentDetails.downloadSpeed))

            Text(stringResource(R.string.upload_speed))
            Text(fileSizeFormatter.formatTransferRate(torrentDetails.uploadSpeed))

            Text(stringResource(R.string.eta))
            Text(formatTorrentEta(torrentDetails.eta))

            Text(stringResource(R.string.seeders))
            Text(torrentDetails.totalSeedersFromTrackers.toString())

            Text(stringResource(R.string.web_seeders_sending_to_us))
            Text(torrentDetails.webSeedersSendingToUsCount.toString())

            Text(stringResource(R.string.leechers))
            Text(torrentDetails.totalLeechersFromTrackers.toString())

            Text(stringResource(R.string.peers_sending_to_us))
            Text(torrentDetails.peersSendingToUsCount.toString())

            Text(stringResource(R.string.peers_getting_from_us))
            Text(torrentDetails.peersGettingFromUsCount.toString())

            Text(stringResource(R.string.last_activity))
            Text(torrentDetails.activityDate?.let(formatTime).orEmpty())
        }

        TremotesfSectionHeader(
            R.string.information,
            modifier = Modifier
                .span { maxLineSpan }
                .padding(top = Dimens.SpacingSmall)
        )

        CompositionLocalProvider(LocalTextStyle provides MaterialTheme.typography.bodyMedium) {
            Text(stringResource(R.string.total_size))
            Text(fileSizeFormatter.formatFileSize(torrentDetails.totalSize))

            Text(stringResource(R.string.location))
            SelectionContainer {
                Text(torrentDetails.downloadDirectory.toNativeSeparators())
            }

            Text(stringResource(R.string.hash))
            SelectionContainer {
                Text(torrentDetails.hashString)
            }

            Text(stringResource(R.string.created_by))
            Text(torrentDetails.creator)

            Text(stringResource(R.string.created_on))
            Text(torrentDetails.creationDate?.let(formatTime).orEmpty())

            if (torrentDetails.comment.isNotBlank()) {
                Text(stringResource(R.string.comment))
                SelectionContainer {
                    val linkColor = MaterialTheme.colorScheme.primary
                    val annotated = remember(torrentDetails.comment, linkColor) {
                        torrentDetails.comment.annotateLinks(linkColor)
                    }
                    Text(annotated)
                }
            }

            if (shouldShowLabels.value) {
                Text(stringResource(R.string.labels))
                Column(verticalArrangement = Arrangement.spacedBy(Dimens.SpacingSmall)) {
                    TremotesfLabelsList(
                        labels = torrentDetails.labels,
                        showEditButton = true,
                        onEditButtonClicked = navigateToLabelsEditDialog
                    )
                }
            }
        }
    }
}

@Composable
private fun rememberTimeFormatter(): (Instant) -> String {
    val dateTimeFormatter = remember(LocalConfiguration.current.locales) {
        Timber.d(Throwable(), "Creating DateTimeFormatter")
        DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
    }
    val context = LocalContext.current
    return { time ->
        val absolute = dateTimeFormatter.format(time.atZone(ZoneId.systemDefault()))
        val now = Instant.now()
        val relativeMillis = Duration.between(time, now).toMillis()
        if (abs(relativeMillis) < DateUtils.WEEK_IN_MILLIS) {
            val relative = DateUtils.getRelativeTimeSpanString(
                time.toEpochMilli(),
                now.toEpochMilli(),
                DateUtils.MINUTE_IN_MILLIS,
                0
            )
            context.getString(R.string.date_time_with_relative, absolute, relative)
        } else {
            absolute
        }
    }
}

private fun String.annotateLinks(color: Color): AnnotatedString {
    val styles = TextLinkStyles(SpanStyle(color = color, textDecoration = TextDecoration.Underline))
    return AnnotatedString(this, annotations = PatternsCompat.WEB_URL.toRegex().findAll(this).map {
        AnnotatedString.Range(
            item = LinkAnnotation.Url(it.value, styles),
            start = it.range.start,
            end = it.range.endInclusive + 1
        )
    }.toList())
}

@Preview
@Composable
private fun DetailsTabPreview() = ComponentPreview {
    DetailsTab(
        innerPadding = PaddingValues(),
        torrentDetails = TORRENT_DETAILS_FOR_PREVIEW,
        shouldShowLabels = remember { mutableStateOf(true) },
        navigateToLabelsEditDialog = {}
    )
}

private val TORRENT_DETAILS_FOR_PREVIEW: TorrentDetails by lazy {
    TorrentDetails(
        id = 42,
        hashString = "",
        magnetLink = "",
        name = "Cat.",
        status = TorrentStatus.Downloading,
        downloadDirectory = "/tmp".normalizePath(null),
        eta = 666.seconds,
        ratio = 6.9,
        totalSize = FileSize.fromBytes(11111111),
        completedSize = FileSize.fromBytes(1),
        totalUploaded = FileSize.fromBytes(777),
        downloadSpeed = TransferRate.fromKiloBytesPerSecond(100000),
        uploadSpeed = TransferRate.fromKiloBytesPerSecond(10000),
        peersSendingToUsCount = 33,
        peersGettingFromUsCount = 99,
        webSeedersSendingToUsCount = 4,
        addedDate = LocalDate.of(2024, 1, 1).atStartOfDay().toInstant(ZoneOffset.UTC),
        labels = listOf("Cat.", "Cats", "Felines"),
        totalDownloaded = FileSize.fromBytes(111),
        activityDate = LocalDate.of(2025, 1, 1).atStartOfDay().toInstant(ZoneOffset.UTC),
        creationDate = LocalDate.of(2020, 1, 1).atStartOfDay().toInstant(ZoneOffset.UTC),
        comment = "Hmm https://github.com",
        creator = "Dude",
        trackerStats = listOf(
            TorrentDetails.TrackerStats(seeders = 6, leechers = 9),
            TorrentDetails.TrackerStats(seeders = 99, leechers = 999),
        )
    )
}