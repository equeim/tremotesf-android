// SPDX-FileCopyrightText: 2017-2025 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package org.equeim.tremotesf.ui.torrentpropertiesfragment

import android.os.Bundle
import android.text.format.DateUtils
import androidx.core.view.isVisible
import org.equeim.tremotesf.R
import org.equeim.tremotesf.databinding.PlaceholderLayoutBinding
import org.equeim.tremotesf.databinding.TorrentDetailsFragmentBinding
import org.equeim.tremotesf.rpc.RpcRequestState
import org.equeim.tremotesf.rpc.requests.torrentproperties.TorrentDetails
import org.equeim.tremotesf.rpc.toNativeSeparators
import org.equeim.tremotesf.ui.navController
import org.equeim.tremotesf.ui.utils.DecimalFormats
import org.equeim.tremotesf.ui.utils.FormatUtils
import org.equeim.tremotesf.ui.utils.hide
import org.equeim.tremotesf.ui.utils.launchAndCollectWhenStarted
import org.equeim.tremotesf.ui.utils.showError
import org.equeim.tremotesf.ui.utils.showLoading
import org.equeim.tremotesf.ui.utils.updateLabelsText
import org.equeim.tremotesf.ui.utils.viewLifecycleObject
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.math.abs


class TorrentDetailsFragment :
    TorrentPropertiesFragment.PagerFragment(
        R.layout.torrent_details_fragment,
        TorrentPropertiesFragment.PagerAdapter.Tab.Details
    ) {

    private val torrentHashString: String by lazy { TorrentPropertiesFragment.getTorrentHashString(navController) }
    private val model by TorrentPropertiesFragmentViewModel.from(this)

    private val dateTimeFormatter =
        DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
    private val binding by viewLifecycleObject(TorrentDetailsFragmentBinding::bind)

    override fun onViewStateRestored(savedInstanceState: Bundle?) {
        super.onViewStateRestored(savedInstanceState)
        binding.hashTextView.text = torrentHashString
        model.torrentDetails.launchAndCollectWhenStarted(viewLifecycleOwner) {
            when (it) {
                is RpcRequestState.Loaded -> it.response?.let(::showDetails)
                    ?: showPlaceholder { showError(getText(R.string.torrent_not_found)) }

                is RpcRequestState.Loading -> showPlaceholder { showLoading() }
                is RpcRequestState.Error -> showPlaceholder { showError(it.error) }
            }
        }
    }

    private inline fun showPlaceholder(show: PlaceholderLayoutBinding.() -> Unit) {
        with(binding) {
            scrollView.isVisible = false
            placeholderView.show()
        }
    }

    private fun showDetails(details: TorrentDetails) {
        with(binding) {
            scrollView.isVisible = true
            placeholderView.hide()
        }
        update(details)
    }

    private fun update(torrentDetails: TorrentDetails) {
        with(binding) {
            completedTextView.text =
                FormatUtils.formatFileSize(requireContext(), torrentDetails.completedSize)
            downloadedTextView.text = FormatUtils.formatFileSize(
                requireContext(),
                torrentDetails.totalDownloaded
            )
            uploadedTextView.text =
                FormatUtils.formatFileSize(requireContext(), torrentDetails.totalUploaded)

            ratioTextView.text = DecimalFormats.ratio.format(torrentDetails.ratio)

            downloadSpeedTextView.text =
                FormatUtils.formatTransferRate(requireContext(), torrentDetails.downloadSpeed)
            uploadSpeedTextView.text =
                FormatUtils.formatTransferRate(requireContext(), torrentDetails.uploadSpeed)
            etaTextView.text = FormatUtils.formatDuration(requireContext(), torrentDetails.eta)
            seedersTextView.text = torrentDetails.totalSeedersFromTrackers.toString()
            leechersTextView.text = torrentDetails.totalLeechersFromTrackers.toString()
            peersSendingToUsTextView.text = torrentDetails.peersSendingToUsCount.toString()
            webSeedersSendingToUsTextView.text = torrentDetails.webSeedersSendingToUsCount.toString()
            peersGettingFromUsTextView.text = torrentDetails.peersGettingFromUsCount.toString()
            lastActivityTextView.text =
                torrentDetails.activityDate?.toDisplayString()

            totalSizeTextView.text = FormatUtils.formatFileSize(requireContext(), torrentDetails.totalSize)

            val dir = torrentDetails.downloadDirectory.toNativeSeparators()
            if (!dir.contentEquals(locationTextView.text)) {
                locationTextView.text = dir
            }

            creatorTextView.text = torrentDetails.creator
            creationDateTextView.text =
                torrentDetails.creationDate?.toDisplayString()
            addedDateTextView.text =
                torrentDetails.addedDate?.toDisplayString()

            val comment: String = torrentDetails.comment
            if (!comment.contentEquals(commentTextView.text)) {
                commentTextView.text = comment
            }
            commentLabel.isVisible = comment.isNotEmpty()
            commentTextView.isVisible = comment.isNotEmpty()

            updateLabelsText(labelsTextView, torrentDetails.labels)
            labelsLabel.isVisible = torrentDetails.labels.isNotEmpty()
            labelsTextView.isVisible = torrentDetails.labels.isNotEmpty()
        }
    }

    private fun Instant.toDisplayString(): String {
        val absolute = dateTimeFormatter.format(this.atZone(ZoneId.systemDefault()))
        val now = Instant.now()
        val relativeMillis = Duration.between(this, now).toMillis()
        return if (abs(relativeMillis) < DateUtils.WEEK_IN_MILLIS) {
            val relative = DateUtils.getRelativeTimeSpanString(
                toEpochMilli(),
                now.toEpochMilli(),
                DateUtils.MINUTE_IN_MILLIS,
                0
            )
            getString(R.string.date_time_with_relative, absolute, relative)
        } else {
            absolute
        }
    }
}
