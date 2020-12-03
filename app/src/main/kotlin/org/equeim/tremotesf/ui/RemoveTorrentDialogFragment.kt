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

package org.equeim.tremotesf.ui

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.equeim.tremotesf.R
import org.equeim.tremotesf.data.rpc.Rpc
import org.equeim.tremotesf.databinding.RemoveTorrentsDialogBinding

class RemoveTorrentDialogFragment : NavigationDialogFragment() {
    companion object {
        const val TORRENT_IDS = "torrentIds"
        const val POP_BACK_STACK = "popBackStack"
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val ids = requireArguments().getIntArray(TORRENT_IDS)!!

        val builder = MaterialAlertDialogBuilder(requireContext())
        val binding = RemoveTorrentsDialogBinding.inflate(LayoutInflater.from(builder.context))

        binding.deleteFilesCheckBox.isChecked = Settings.deleteFiles

        return builder
                .setMessage(if (ids.size == 1) getString(R.string.remove_torrent_message)
                            else resources.getQuantityString(R.plurals.remove_torrents_message,
                                                             ids.size,
                                                             ids.size))
                .setView(binding.root)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.remove) { _, _ ->
                    Rpc.nativeInstance.removeTorrents(ids, binding.deleteFilesCheckBox.isChecked)
                    activity?.actionMode?.finish()
                    if (requireArguments().getBoolean(POP_BACK_STACK)) {
                        val id = (parentFragmentManager.primaryNavigationFragment as NavigationFragment).destinationId
                        val listener = object : NavController.OnDestinationChangedListener {
                            override fun onDestinationChanged(controller: NavController, destination: NavDestination, arguments: Bundle?) {
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