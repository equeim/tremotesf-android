// SPDX-FileCopyrightText: 2017-2025 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package org.equeim.tremotesf.ui.utils

import android.content.Context
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.DynamicDrawableSpan
import android.text.style.ImageSpan
import android.widget.TextView
import org.equeim.tremotesf.R

fun updateLabelsText(view: TextView, labels: List<String>) {
    @Suppress("UNCHECKED_CAST")
    val oldLabels = view.getTag(R.id.labels_list_tag) as? List<String>
    if (labels == oldLabels) {
        return
    }
    view.text = buildLabelsString(labels, view.context)
    view.setTag(R.id.labels_list_tag, labels)
}

private fun buildLabelsString(labels: List<String>, context: Context): CharSequence? = if (labels.isEmpty()) {
    null
} else {
    SpannableStringBuilder().apply {
        for (label in labels) {
            if (isNotEmpty()) append(' ')
            append('\u200b')
            setSpan(
                ImageSpan(context, R.drawable.ic_label_16dp, DynamicDrawableSpan.ALIGN_BOTTOM),
                length - 1,
                length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            append(' ')
            append(label)
        }
    }
}
