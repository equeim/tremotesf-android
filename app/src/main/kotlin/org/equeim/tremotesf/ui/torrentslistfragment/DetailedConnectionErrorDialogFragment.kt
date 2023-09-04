// SPDX-FileCopyrightText: 2017-2022 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package org.equeim.tremotesf.ui.torrentslistfragment

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.equeim.tremotesf.R
import org.equeim.tremotesf.databinding.DetailedConnectionErrorDialogBinding
import org.equeim.tremotesf.ui.NavigationDialogFragment
import org.equeim.tremotesf.ui.utils.Utils

class DetailedConnectionErrorDialogFragment : NavigationDialogFragment() {
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val builder = MaterialAlertDialogBuilder(requireContext())
        val binding = DetailedConnectionErrorDialogBinding.inflate(LayoutInflater.from(builder.context))
        val error = DetailedConnectionErrorDialogFragmentArgs.fromBundle(requireArguments()).error
        binding.wrappedText.text = error.detailedError
        binding.unwrappedText.text = error.certificates
        return builder.setView(binding.root)
            .setTitle(R.string.detailed_error_message)
            .setNeutralButton(R.string.share) { _, _ -> Utils.shareText(error.detailedError + error.certificates, requireContext().getText(R.string.share), requireContext()) }
            .setNegativeButton(R.string.close, null).create()
    }
}
