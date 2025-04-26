// SPDX-FileCopyrightText: 2017-2025 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package org.equeim.tremotesf.ui.torrentslistfragment

import android.content.Context
import android.widget.AutoCompleteTextView
import org.equeim.tremotesf.R
import org.equeim.tremotesf.common.AlphanumericComparator
import org.equeim.tremotesf.rpc.requests.Torrent
import org.equeim.tremotesf.ui.utils.AutoCompleteTextViewDynamicAdapter

class LabelsViewAdapter(
    private val context: Context,
    textView: AutoCompleteTextView,
) : AutoCompleteTextViewDynamicAdapter(textView) {
    private data class LabelItem(val label: String?, val torrents: Int)

    private var labels = emptyList<LabelItem>()

    private val comparator = object : Comparator<LabelItem> {
        val labelComparator = AlphanumericComparator()
        override fun compare(o1: LabelItem, o2: LabelItem): Int =
            labelComparator.compare(o1.label, o2.label)
    }

    private var currentLabelIndex: Int = 0

    override fun getItem(position: Int): String {
        val item = labels.getOrNull(position) ?: return ""
        return if (item.label != null) {
            context.getString(
                R.string.directories_spinner_text,
                item.label,
                item.torrents
            )
        } else {
            context.getString(R.string.torrents_all, item.torrents)
        }
    }

    override fun getCount(): Int {
        return labels.size
    }

    override fun getCurrentItem(): CharSequence {
        return getItem(currentLabelIndex)
    }

    fun getLabel(position: Int): String? {
        return labels[position].label
    }

    fun update(torrents: List<Torrent>, labelFilter: String) {
        labels = torrents
            .asSequence()
            .flatMap { torrent -> torrent.labels.asSequence().map { torrent to it } }
            .groupingBy { (_, label) -> label }
            .eachCount()
            .mapTo(mutableListOf(LabelItem(null, torrents.size))) { (label, torrents) ->
                LabelItem(
                    label,
                    torrents
                )
            }
            .apply { sortWith(comparator) }
        currentLabelIndex = if (labelFilter.isEmpty()) {
            0
        } else {
            labels.indexOfFirst { it.label == labelFilter }.takeUnless { it == -1 } ?: 0
        }
        notifyDataSetChanged()
    }
}
