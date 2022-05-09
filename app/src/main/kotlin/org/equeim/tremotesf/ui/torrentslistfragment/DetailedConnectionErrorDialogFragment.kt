package org.equeim.tremotesf.ui.torrentslistfragment

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.equeim.tremotesf.R
import org.equeim.tremotesf.databinding.DetailedConnectionErrorDialogBinding
import org.equeim.tremotesf.rpc.GlobalRpc
import org.equeim.tremotesf.ui.NavigationDialogFragment
import org.equeim.tremotesf.ui.utils.Utils

class DetailedConnectionErrorDialogFragment : NavigationDialogFragment() {
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val builder = MaterialAlertDialogBuilder(requireContext())
        val binding = DetailedConnectionErrorDialogBinding.inflate(LayoutInflater.from(builder.context))
        binding.text.text = GlobalRpc.error.value.detailedErrorMessage
        return builder.setView(binding.root)
            .setTitle(R.string.detailed_error_message)
            .setNeutralButton(R.string.share) { _, _ -> Utils.shareText(binding.text.text, requireContext().getText(R.string.share), requireContext()) }
            .setNegativeButton(R.string.close, null).create()
    }
}
