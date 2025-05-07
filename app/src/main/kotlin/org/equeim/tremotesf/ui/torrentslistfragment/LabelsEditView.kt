// SPDX-FileCopyrightText: 2017-2025 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package org.equeim.tremotesf.ui.torrentslistfragment

import android.content.Context
import android.os.Parcelable
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.inputmethod.EditorInfo
import android.widget.LinearLayout
import androidx.annotation.AttrRes
import androidx.annotation.StyleRes
import androidx.core.view.children
import com.google.android.material.chip.Chip
import kotlinx.parcelize.Parcelize
import org.equeim.tremotesf.R
import org.equeim.tremotesf.common.AlphanumericComparator
import org.equeim.tremotesf.databinding.LabelsEditViewBinding
import org.equeim.tremotesf.ui.utils.ArrayDropdownAdapter
import timber.log.Timber

class LabelsEditView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null,
    @AttrRes defStyleAttr: Int = 0,
    @StyleRes defStyleRes: Int = 0
) : LinearLayout(context, attrs, defStyleAttr, defStyleRes) {
    private var restoredState = false
    var enabledLabels: List<String> = emptyList()
        private set
    private var allLabels: Set<String> = emptySet()

    private val inflater = LayoutInflater.from(context)
    private val binding = LabelsEditViewBinding.inflate(inflater, this)

    init {
        orientation = VERTICAL
        binding.newLabelEdit.apply {
            setOnEditorActionListener { _, id, event ->
                val label = text.trim()
                if (label.isNotEmpty()) {
                    if (addLabel(label)) {
                        text.clear()
                    }
                }
                true
            }
            setOnItemClickListener { _, _, position, _ ->
                addLabel(adapter.getItem(position) as String)
                text.clear()
            }
        }
    }

    fun setInitialEnabledLabels(labels: Iterable<String>) {
        if (restoredState) return
        enabledLabels = labels.sortedWith(AlphanumericComparator())
        binding.chipGroup.apply { children.filterIsInstance<Chip>().forEach(::removeView) }
        labels.forEach(::addChip)
    }

    fun setAllLabels(labels: Set<String>) {
        if (labels == allLabels) return
        allLabels = labels
        binding.newLabelEdit.setAdapter(ArrayDropdownAdapter(allLabels.sortedWith(AlphanumericComparator())))
    }

    private fun addChip(label: String) {
        val chip = inflater.inflate(R.layout.label_chip, binding.chipGroup, false) as Chip
        chip.text = label
        chip.isCheckable = false
        chip.setOnCloseIconClickListener {
            enabledLabels = enabledLabels - label
            binding.chipGroup.removeView(chip)
        }
        binding.chipGroup.addView(chip)
    }

    private fun addLabel(label: CharSequence): Boolean {
        if (enabledLabels.any { it.contentEquals(label) }) return false
        val labelString = label.toString()
        enabledLabels = enabledLabels + labelString
        addChip(labelString)
        return true
    }

    override fun onSaveInstanceState(): Parcelable {
        return SavedState(super.onSaveInstanceState(), enabledLabels, allLabels)
    }

    override fun onRestoreInstanceState(state: Parcelable?) {
        if (state !is SavedState) return
        super.onRestoreInstanceState(state.superState)
        setInitialEnabledLabels(state.enabledLabels)
        setAllLabels(state.allLabels)
        restoredState = true
    }

    @Parcelize
    private data class SavedState(
        val superState: Parcelable?,
        val enabledLabels: List<String>,
        val allLabels: Set<String>,
    ) : Parcelable
}
