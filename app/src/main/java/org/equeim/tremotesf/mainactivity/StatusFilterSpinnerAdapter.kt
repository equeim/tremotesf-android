/*
 * Copyright (C) 2017 Alexey Rochev <equeim@gmail.com>
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

package org.equeim.tremotesf.mainactivity

import android.content.Context

import org.equeim.tremotesf.R
import org.equeim.tremotesf.Rpc
import org.equeim.tremotesf.utils.BaseSpinnerAdapter


class StatusFilterSpinnerAdapter(private val context: Context) : BaseSpinnerAdapter(R.string.status) {
    companion object {
        const val ALL = 0
        const val ACTIVE = 1
        const val DOWNLOADING = 2
        const val SEEDING = 3
        const val PAUSED = 4
        const val CHECKING = 5
        const val ERRORED = 6
    }

    private var activeTorrents = 0
    private var downloadingTorrents = 0
    private var seedingTorrents = 0
    private var pausedTorrents = 0
    private var checkingTorrents = 0
    private var erroredTorrents = 0

    override fun getItem(position: Int): String {
        return when (position) {
            ALL -> context.getString(R.string.torrents_all, Rpc.torrents.size)
            ACTIVE -> context.getString(R.string.torrents_active, activeTorrents)
            DOWNLOADING -> context.getString(R.string.torrents_downloading, downloadingTorrents)
            SEEDING -> context.getString(R.string.torrents_seeding, seedingTorrents)
            PAUSED -> context.getString(R.string.torrents_paused, pausedTorrents)
            CHECKING -> context.getString(R.string.torrents_checking, checkingTorrents)
            ERRORED -> context.getString(R.string.torrents_errored, erroredTorrents)
            else -> String()
        }
    }

    override fun getCount(): Int {
        return 7
    }

    fun update() {
        activeTorrents = 0
        downloadingTorrents = 0
        seedingTorrents = 0
        pausedTorrents = 0
        checkingTorrents = 0
        erroredTorrents = 0

        for (torrent in Rpc.torrents) {
            if (TorrentsAdapter.statusFilterAcceptsTorrent(torrent, TorrentsAdapter.StatusFilterMode.Active)) {
                activeTorrents++
            }
            if (TorrentsAdapter.statusFilterAcceptsTorrent(torrent, TorrentsAdapter.StatusFilterMode.Downloading)) {
                downloadingTorrents++
            }
            if (TorrentsAdapter.statusFilterAcceptsTorrent(torrent, TorrentsAdapter.StatusFilterMode.Seeding)) {
                seedingTorrents++
            }
            if (TorrentsAdapter.statusFilterAcceptsTorrent(torrent, TorrentsAdapter.StatusFilterMode.Paused)) {
                pausedTorrents++
            }
            if (TorrentsAdapter.statusFilterAcceptsTorrent(torrent, TorrentsAdapter.StatusFilterMode.Checking)) {
                checkingTorrents++
            }
            if (TorrentsAdapter.statusFilterAcceptsTorrent(torrent, TorrentsAdapter.StatusFilterMode.Errored)) {
                erroredTorrents++
            }
        }

        notifyDataSetChanged()
    }
}