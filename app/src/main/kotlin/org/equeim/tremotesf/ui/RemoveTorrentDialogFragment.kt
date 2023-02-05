// SPDX-FileCopyrightText: 2017-2022 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package org.equeim.tremotesf.ui

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.fragment.navArgs
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch
import org.equeim.libtremotesf.IntVector
import org.equeim.tremotesf.R
import org.equeim.tremotesf.databinding.RemoveTorrentsDialogBinding
import org.equeim.tremotesf.rpc.GlobalRpc

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
                if (args.torrentIds.size == 1) getString(R.string.remove_torrent_message)
                else resources.getQuantityString(
                    R.plurals.remove_torrents_message,
                    args.torrentIds.size,
                    args.torrentIds.size
                )
            )
            .setView(binding.root)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.remove) { _, _ ->
                GlobalRpc.nativeInstance.removeTorrents(
                    IntVector(args.torrentIds),
                    binding.deleteFilesCheckBox.isChecked
                )
                activity?.actionMode?.finish()
                if (args.popBackStack) {
                    val id =
                        (parentFragmentManager.primaryNavigationFragment as NavigationFragment).destinationId
                    val listener = object : NavController.OnDestinationChangedListener {
                        override fun onDestinationChanged(
                            controller: NavController,
                            destination: NavDestination,
                            arguments: Bundle?
                        ) {
                            if (destination.id == id) {
                                navController.popBackStack()
                                controller.removeOnDestinationChangedListener(this)
                            }
                        }
                    }
                    navController.addOnDestinationChangedListener(listener)
                }
            }
            .create()
    }
}