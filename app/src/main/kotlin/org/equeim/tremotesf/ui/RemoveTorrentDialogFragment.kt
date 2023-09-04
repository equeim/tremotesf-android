// SPDX-FileCopyrightText: 2017-2022 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package org.equeim.tremotesf.ui

import android.app.Dialog
import android.os.Bundle
import android.os.Parcelable
import android.view.LayoutInflater
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResult
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.navArgs
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch
import kotlinx.parcelize.Parcelize
import org.equeim.tremotesf.R
import org.equeim.tremotesf.databinding.RemoveTorrentsDialogBinding
import org.equeim.tremotesf.ui.utils.parcelable

class RemoveTorrentDialogFragment : NavigationDialogFragment() {
    private val args: RemoveTorrentDialogFragmentArgs by navArgs()

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val builder = MaterialAlertDialogBuilder(requireContext())
        val binding = RemoveTorrentsDialogBinding.inflate(LayoutInflater.from(builder.context))

        lifecycleScope.launch {
            binding.deleteFilesCheckBox.isChecked = Settings.deleteFiles.get()
        }

        return builder
            .setMessage(
                if (args.torrentHashStrings.size == 1) getString(R.string.remove_torrent_message)
                else resources.getQuantityString(
                    R.plurals.remove_torrents_message,
                    args.torrentHashStrings.size,
                    args.torrentHashStrings.size
                )
            )
            .setView(binding.root)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.remove) { _, _ ->
                setFragmentResult(
                    RESULT_KEY,
                    bundleOf(
                        TORRENTS_REMOVE_REQUEST_KEY to TorrentsRemoveRequest(
                            args.torrentHashStrings.asList(),
                            binding.deleteFilesCheckBox.isChecked
                        )
                    )
                )
            }
            .create()
    }

    @Parcelize
    data class TorrentsRemoveRequest(val torrentHashStrings: List<String>, val deleteFiles: Boolean) : Parcelable

    companion object {
        private val RESULT_KEY = RemoveTorrentDialogFragment::class.qualifiedName!!
        private val TORRENTS_REMOVE_REQUEST_KEY = TorrentsRemoveRequest::class.qualifiedName!!

        fun setFragmentResultListener(fragment: Fragment, listener: (TorrentsRemoveRequest) -> Unit) {
            fragment.parentFragmentManager.setFragmentResultListener(RESULT_KEY, fragment.viewLifecycleOwner) { _, bundle ->
                bundle.parcelable<TorrentsRemoveRequest>(TORRENTS_REMOVE_REQUEST_KEY)?.let(listener)
            }
        }
    }
}
