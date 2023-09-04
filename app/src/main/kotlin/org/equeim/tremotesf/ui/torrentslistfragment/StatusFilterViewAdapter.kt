// SPDX-FileCopyrightText: 2017-2023 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package org.equeim.tremotesf.ui.torrentslistfragment

import android.content.Context
import android.widget.AutoCompleteTextView
import org.equeim.tremotesf.R
import org.equeim.tremotesf.torrentfile.rpc.requests.Torrent
import org.equeim.tremotesf.ui.torrentslistfragment.TorrentsListFragmentViewModel.Companion.statusFilterAcceptsTorrent
import org.equeim.tremotesf.ui.torrentslistfragment.TorrentsListFragmentViewModel.StatusFilterMode
import org.equeim.tremotesf.ui.utils.AutoCompleteTextViewDynamicAdapter


class StatusFilterViewAdapter(
    private val context: Context,
    textView: AutoCompleteTextView
) : AutoCompleteTextViewDynamicAdapter(textView) {
    private var statusFilterMode = StatusFilterMode.DEFAULT

    private var allTorrents = 0
    private var activeTorrents = 0
    private var downloadingTorrents = 0
    private var seedingTorrents = 0
    private var pausedTorrents = 0
    private var checkingTorrents = 0
    private var erroredTorrents = 0

    override fun getItem(position: Int): String {
        return when (position) {
            0 -> context.getString(R.string.torrents_all, allTorrents)
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
        return getItem(statusFilterMode.ordinal)
    }

    fun update(torrents: List<Torrent>, allTorrentsCount: Int, statusFilterMode: StatusFilterMode) {
        this.statusFilterMode = statusFilterMode

        allTorrents = allTorrentsCount
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