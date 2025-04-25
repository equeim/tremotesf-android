// SPDX-FileCopyrightText: 2017-2025 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package org.equeim.tremotesf.ui.torrentslistfragment

import android.content.ActivityNotFoundException
import android.os.Bundle
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import org.equeim.tremotesf.R
import org.equeim.tremotesf.databinding.AddTorrentMenuFragmentBinding
import org.equeim.tremotesf.ui.NavigationBottomSheetDialogFragment
import org.equeim.tremotesf.ui.addtorrent.TORRENT_FILE_MIME_TYPE
import timber.log.Timber

class AddTorrentMenuFragment : NavigationBottomSheetDialogFragment(R.layout.add_torrent_menu_fragment) {
    private lateinit var getContentActivityLauncher: ActivityResultLauncher<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        getContentActivityLauncher =
            registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
                if (uri != null) {
                    navigate(AddTorrentMenuFragmentDirections.toAddTorrentFileFragment(uri))
                } else {
                    navController.popBackStack()
                }
            }
    }

    override fun onViewStateRestored(savedInstanceState: Bundle?) {
        super.onViewStateRestored(savedInstanceState)
        val binding = AddTorrentMenuFragmentBinding.bind(requireView())
        binding.addTorrentFile.setOnClickListener {
            startFilePickerActivity()
        }
        binding.addTorrentLink.setOnClickListener { navigate(AddTorrentMenuFragmentDirections.toAddTorrentLinkFragment()) }
    }

    private fun startFilePickerActivity() {
        try {
            getContentActivityLauncher.launch(TORRENT_FILE_MIME_TYPE)
        } catch (e: ActivityNotFoundException) {
            Timber.e(e, "Failed to start activity")
        }
    }
}