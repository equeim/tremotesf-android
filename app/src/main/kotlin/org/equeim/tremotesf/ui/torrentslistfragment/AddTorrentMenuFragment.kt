package org.equeim.tremotesf.ui.torrentslistfragment

import android.content.ActivityNotFoundException
import android.os.Bundle
import android.view.View
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import org.equeim.tremotesf.R
import org.equeim.tremotesf.databinding.AddTorrentMenuFragmentBinding
import org.equeim.tremotesf.ui.NavigationBottomSheetDialogFragment
import org.equeim.tremotesf.ui.utils.viewBinding
import timber.log.Timber

class AddTorrentMenuFragment : NavigationBottomSheetDialogFragment(R.layout.add_torrent_menu_fragment) {
    private lateinit var getContentActivityLauncher: ActivityResultLauncher<String>
    private val binding by viewBinding(AddTorrentMenuFragmentBinding::bind)

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

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.addTorrentFile.setOnClickListener {
            startFilePickerActivity()
        }
        binding.addTorrentLink.setOnClickListener { navigate(AddTorrentMenuFragmentDirections.toAddTorrentLinkFragment()) }
    }

    private fun startFilePickerActivity() {
        try {
            getContentActivityLauncher.launch("application/x-bittorrent")
        } catch (e: ActivityNotFoundException) {
            Timber.e(e, "Failed to start activity")
        }
    }
}