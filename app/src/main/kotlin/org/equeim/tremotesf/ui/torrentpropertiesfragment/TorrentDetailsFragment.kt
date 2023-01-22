/*
 * Copyright (C) 2017-2022 Alexey Rochev <equeim@gmail.com>
 *
 * This file is part of Tremotesf.
 *
 * Tremotesf is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Tremotesf is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.equeim.tremotesf.ui.torrentpropertiesfragment

import android.os.Bundle
import android.text.format.DateUtils
import org.equeim.tremotesf.R
import org.equeim.tremotesf.databinding.TorrentDetailsFragmentBinding
import org.equeim.tremotesf.torrentfile.rpc.Torrent
import org.equeim.tremotesf.ui.navController
import org.equeim.tremotesf.ui.utils.*
import org.threeten.bp.Duration
import org.threeten.bp.Instant
import org.threeten.bp.ZoneId
import org.threeten.bp.format.DateTimeFormatter
import org.threeten.bp.format.FormatStyle
import kotlin.math.abs


class TorrentDetailsFragment :
    TorrentPropertiesFragment.PagerFragment(
        R.layout.torrent_details_fragment,
        TorrentPropertiesFragment.PagerAdapter.Tab.Details
    ) {

    private val dateTimeFormatter =
        DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
    private val binding by viewLifecycleObject(TorrentDetailsFragmentBinding::bind)

    override fun onViewStateRestored(savedInstanceState: Bundle?) {
        super.onViewStateRestored(savedInstanceState)
        val propertiesFragmentModel = TorrentPropertiesFragmentViewModel.get(navController)
        binding.hashTextView.text = propertiesFragmentModel.args.hash
        propertiesFragmentModel.torrent.launchAndCollectWhenStarted(viewLifecycleOwner, ::update)
    }

    private fun update(torrent: Torrent?) {
        if (torrent == null) return
        with(binding) {
            completedTextView.text =
                FormatUtils.formatByteSize(requireContext(), torrent.completedSize)
            downloadedTextView.text = FormatUtils.formatByteSize(
                requireContext(),
                torrent.totalDownloaded
            )
            uploadedTextView.text =
                FormatUtils.formatByteSize(requireContext(), torrent.totalUploaded)

            ratioTextView.text = DecimalFormats.ratio.format(torrent.ratio)

            downloadSpeedTextView.text =
                FormatUtils.formatByteSpeed(requireContext(), torrent.downloadSpeed)
            uploadSpeedTextView.text =
                FormatUtils.formatByteSpeed(requireContext(), torrent.uploadSpeed)
            etaTextView.text = FormatUtils.formatDuration(requireContext(), torrent.eta)
            seedersTextView.text = torrent.seeders.toString()
            webSeedersTextView.text = torrent.activeWebSeeders.toString()
            leechersTextView.text = torrent.leechers.toString()
            lastActivityTextView.text =
                torrent.data.activityDate?.toDisplayString()

            totalSizeTextView.text = FormatUtils.formatByteSize(requireContext(), torrent.totalSize)

            val dir = torrent.downloadDirectory.toNativeSeparators()
            if (!dir.contentEquals(locationTextView.text)) {
                locationTextView.text = dir
            }

            creatorTextView.text = torrent.data.creator
            creationDateTextView.text =
                torrent.data.creationDate?.toDisplayString()
            addedDateTextView.text =
                torrent.data.addedDate?.toDisplayString()

            val comment: String = torrent.data.comment
            if (!comment.contentEquals(commentTextView.text)) {
                commentTextView.text = comment
            }
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
