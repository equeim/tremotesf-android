/*
 * Copyright (C) 2017-2020 Alexey Rochev <equeim@gmail.com>
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

package org.equeim.tremotesf.ui.sidepanel

import android.content.Context
import android.widget.AutoCompleteTextView
import org.equeim.tremotesf.R
import org.equeim.tremotesf.data.rpc.Torrent
import org.equeim.tremotesf.rpc.GlobalRpc
import org.equeim.tremotesf.ui.Settings
import org.equeim.tremotesf.ui.torrentslistfragment.TorrentsListFragmentViewModel.Companion.statusFilterAcceptsTorrent
import org.equeim.tremotesf.ui.torrentslistfragment.TorrentsListFragmentViewModel.StatusFilterMode
import org.equeim.tremotesf.ui.utils.AutoCompleteTextViewDynamicAdapter


class StatusFilterViewAdapter(
    private val context: Context,
    textView: AutoCompleteTextView
) : AutoCompleteTextViewDynamicAdapter(textView) {
    private var activeTorrents = 0
    private var downloadingTorrents = 0
    private var seedingTorrents = 0
    private var pausedTorrents = 0
    private var checkingTorrents = 0
    private var erroredTorrents = 0

    override fun getItem(position: Int): String {
        return when (position) {
            0 -> context.getString(R.string.torrents_all, GlobalRpc.torrents.value.size)
            1 -> context.getString(R.string.torrents_active, activeTorrents)
            2 -> context.getString(R.string.torrents_downloading, downloadingTorrents)
            3 -> context.getString(R.string.torrents_seeding, seedingTorrents)
            4 -> context.getString(R.string.torrents_paused, pausedTorrents)
            5 -> context.getString(R.string.torrents_checking, checkingTorrents)
            6 -> context.getString(R.string.torrents_errored, erroredTorrents)
            else -> ""
        }
    }

    override fun getCount(): Int {
        return 7
    }

    override fun getCurrentItem(): CharSequence {
        return getItem(Settings.torrentsStatusFilter.ordinal)
    }

    fun update(torrents: List<Torrent>) {
        activeTorrents = 0
        downloadingTorrents = 0
        seedingTorrents = 0
        pausedTorrents = 0
        checkingTorrents = 0
        erroredTorrents = 0

        for (torrent in torrents) {
            if (statusFilterAcceptsTorrent(torrent, StatusFilterMode.Active)) {
                activeTorrents++
            }
            if (statusFilterAcceptsTorrent(torrent, StatusFilterMode.Downloading)) {
                downloadingTorrents++
            }
            if (statusFilterAcceptsTorrent(torrent, StatusFilterMode.Seeding)) {
                seedingTorrents++
            }
            if (statusFilterAcceptsTorrent(torrent, StatusFilterMode.Paused)) {
                pausedTorrents++
            }
            if (statusFilterAcceptsTorrent(torrent, StatusFilterMode.Checking)) {
                checkingTorrents++
            }
            if (statusFilterAcceptsTorrent(torrent, StatusFilterMode.Errored)) {
                erroredTorrents++
            }
        }

        notifyDataSetChanged()
    }
}