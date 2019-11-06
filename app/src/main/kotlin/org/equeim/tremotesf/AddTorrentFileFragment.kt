/*
 * Copyright (C) 2017-2019 Alexey Rochev <equeim@gmail.com>
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

package org.equeim.tremotesf

import android.Manifest
import android.content.ContentResolver
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup

import androidx.activity.OnBackPressedCallback
import androidx.activity.addCallback
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.view.ActionMode
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.core.text.trimmedLength
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2

import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayoutMediator

import org.jetbrains.anko.design.indefiniteSnackbar

import org.equeim.libtremotesf.Torrent
import org.equeim.tremotesf.utils.ArraySpinnerAdapterWithHeader
import org.equeim.tremotesf.utils.Utils
import org.equeim.tremotesf.utils.findFragment
import org.equeim.tremotesf.utils.hideKeyboard

import kotlinx.android.synthetic.main.add_torrent_file_files_fragment.*
import kotlinx.android.synthetic.main.add_torrent_file_info_fragment.*
import kotlinx.android.synthetic.main.add_torrent_file_fragment.*
import kotlinx.android.synthetic.main.download_directory_edit.*
import kotlinx.android.synthetic.main.local_torrent_file_list_item.view.*


object AddTorrentFragmentArguments {
    const val URI = "uri"
}

class AddTorrentFileFragment : NavigationFragment(R.layout.add_torrent_file_fragment,
                                                  R.string.add_torrent_file,
                                                  R.menu.add_torrent_activity_menu) {
    private var doneMenuItem: MenuItem? = null
    private var pagerAdapter: PagerAdapter? = null
    private var backPressedCallback: OnBackPressedCallback? = null
    private var snackbar: Snackbar? = null

    lateinit var torrentFileParser: TorrentFileParser
        private set

    private lateinit var uri: Uri

    private var noPermission = false

    private val rpcStatusListener: (Int) -> Unit = {
        updateView()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        retainInstance = true

        torrentFileParser = TorrentFileParser()

        uri = requireArguments().getString(AddTorrentFragmentArguments.URI)!!.toUri()
        if (uri.scheme == ContentResolver.SCHEME_FILE &&
                ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_DENIED) {
            requestPermissions(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), 0)
        } else {
            torrentFileParser.load(uri, requireContext().applicationContext)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int,
                                            permissions: Array<out String>,
                                            grantResults: IntArray) {
        if (grantResults.first() == PackageManager.PERMISSION_GRANTED) {
            torrentFileParser.load(uri, requireContext().applicationContext)
        } else {
            noPermission = true
            updateView()
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        toolbar?.setNavigationOnClickListener {
            if (requireActivity().isTaskRoot) {
                findNavController().navigateUp()
            } else {
                // For some reason it is needed to finish activity before navigateUp(),
                // otherwise we won't switch to our task in some cases
                requireActivity().finish()
                findNavController().navigateUp()
            }
        }

        val pagerAdapter = PagerAdapter(this)
        this.pagerAdapter = pagerAdapter
        pager.adapter = pagerAdapter
        TabLayoutMediator(tab_layout, pager) { tab, position ->
            tab.setText(PagerAdapter.getTitle(position))
        }.attach()

        requireActivity().onBackPressedDispatcher.addCallback(this) {
            if (pager.currentItem != PagerAdapter.Tab.Files.ordinal ||
                    findFragment<FilesFragment>()?.adapter?.navigateUp() != true) {
                isEnabled = false
                requireActivity().onBackPressedDispatcher.onBackPressed()
            }
        }

        pager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            private var previousPage = -1

            override fun onPageSelected(position: Int) {
                if (previousPage != -1) {
                    findFragment<FilesFragment>()?.adapter?.selector?.actionMode?.finish()
                    hideKeyboard()
                }
                previousPage = position
            }
        })

        updateView()

        Rpc.addStatusListener(rpcStatusListener)

        torrentFileParser.statusListener = { status ->
            updateView()
            if (status == TorrentFileParser.Status.Loaded) {
                findFragment<FilesFragment>()?.treeCreated()
            }
        }
    }

    override fun onDestroyView() {
        doneMenuItem = null
        pagerAdapter = null
        backPressedCallback = null
        snackbar = null
        Rpc.removeStatusListener(rpcStatusListener)
        torrentFileParser.statusListener = null
        super.onDestroyView()
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.add_torrent_activity_menu, menu)
        doneMenuItem = menu.findItem(R.id.done).apply {
            isVisible = (torrentFileParser.status == TorrentFileParser.Status.Loaded && Rpc.isConnected)
        }
    }

    override fun onToolbarMenuItemClicked(menuItem: MenuItem): Boolean {
        if (menuItem.itemId != R.id.done) {
            return false
        }
        val infoFragment = findFragment<InfoFragment>()
        if (infoFragment?.check() == true) {
            val filesData = torrentFileParser.getFilesData()
            Rpc.nativeInstance.addTorrentFile(torrentFileParser.fileData,
                                              infoFragment.download_directory_edit.text.toString(),
                                              filesData.wantedFiles.toIntArray(),
                                              filesData.unwantedFiles.toIntArray(),
                                              filesData.lowPriorityFiles.toIntArray(),
                                              filesData.normalPriorityFiles.toIntArray(),
                                              filesData.highPriorityFiles.toIntArray(),
                                              when (infoFragment.priority_spinner.selectedItemPosition) {
                                                  0 -> Torrent.Priority.HighPriority
                                                  1 -> Torrent.Priority.NormalPriority
                                                  2 -> Torrent.Priority.LowPriority
                                                  else -> Torrent.Priority.NormalPriority
                                              },
                                              infoFragment.start_downloading_check_box.isChecked)
            infoFragment.directoriesAdapter?.save()
            activity?.onBackPressed()
            return true
        }
        return false
    }

    private fun updateView() {
        if (view == null) {
            return
        }

        if (Rpc.isConnected && torrentFileParser.status == TorrentFileParser.Status.Loaded) {
            toolbar?.apply {
                (layoutParams as AppBarLayout.LayoutParams).scrollFlags =
                AppBarLayout.LayoutParams.SCROLL_FLAG_SCROLL or
                        AppBarLayout.LayoutParams.SCROLL_FLAG_SNAP or
                        AppBarLayout.LayoutParams.SCROLL_FLAG_ENTER_ALWAYS
                subtitle = torrentFileParser.torrentName
            }
            doneMenuItem?.isVisible = true

            tab_layout.visibility = View.VISIBLE
            pager.visibility = View.VISIBLE

            placeholder_layout.visibility = View.GONE
        } else {
            placeholder.text = if (noPermission) {
                getString(R.string.storage_permission_error)
            } else {
                when (torrentFileParser.status) {
                    TorrentFileParser.Status.Loading -> getString(R.string.loading)
                    TorrentFileParser.Status.FileIsTooLarge -> getString(R.string.file_is_too_large)
                    TorrentFileParser.Status.ReadingError -> getString(R.string.file_reading_error)
                    TorrentFileParser.Status.ParsingError -> getString(R.string.file_parsing_error)
                    TorrentFileParser.Status.Loaded -> Rpc.statusString
                    else -> null
                }
            }

            progress_bar.visibility = if (torrentFileParser.status == TorrentFileParser.Status.Loading ||
                    (Rpc.status == RpcStatus.Connecting && torrentFileParser.status == TorrentFileParser.Status.Loaded)) {
                View.VISIBLE
            } else {
                View.GONE
            }

            placeholder_layout.visibility = View.VISIBLE

            toolbar?.apply {
                (layoutParams as AppBarLayout.LayoutParams).scrollFlags = 0
                subtitle = null
            }
            doneMenuItem?.isVisible = false

            hideKeyboard()

            tab_layout.visibility = View.GONE
            pager.visibility = View.GONE
            pager.currentItem = 0
            placeholder.visibility = View.VISIBLE

            if (torrentFileParser.status == TorrentFileParser.Status.Loaded) {
                when (Rpc.status) {
                    RpcStatus.Disconnected -> {
                        snackbar = view?.indefiniteSnackbar("", getString(R.string.connect)) {
                            snackbar = null
                            Rpc.nativeInstance.connect()
                        }
                    }
                    RpcStatus.Connecting -> {
                        snackbar?.dismiss()
                        snackbar = null
                    }
                    else -> {
                    }
                }
            }
        }
    }

    class PagerAdapter(private val mainFragment: AddTorrentFileFragment) : FragmentStateAdapter(mainFragment) {
        companion object {
            private val tabs = Tab.values()

            @StringRes
            fun getTitle(position: Int): Int {
                return when (tabs[position]) {
                    Tab.Info -> R.string.information
                    Tab.Files -> R.string.files
                }
            }
        }

        enum class Tab {
            Info,
            Files
        }

        override fun getItemCount() = tabs.size

        override fun createFragment(position: Int): Fragment {
            return when (tabs[position]) {
                Tab.Info -> InfoFragment()
                Tab.Files -> FilesFragment()
            }
        }
    }

    class InfoFragment : Fragment(R.layout.add_torrent_file_info_fragment) {
        var directoriesAdapter: AddTorrentDirectoriesAdapter? = null

        override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
            priority_spinner.adapter = ArraySpinnerAdapterWithHeader(resources.getStringArray(R.array.priority_items),
                                                                     R.string.priority)

            download_directory_edit.addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(s: Editable) {
                    val path = s.toString().trim()
                    when {
                        Rpc.serverSettings.canShowFreeSpaceForPath() -> {
                            Rpc.nativeInstance.getFreeSpaceForPath(path)
                        }
                        path == Rpc.serverSettings.downloadDirectory() -> {
                            Rpc.nativeInstance.getDownloadDirFreeSpace()
                        }
                        else -> {
                            free_space_text_view.visibility = View.GONE
                            free_space_text_view.text = ""
                        }
                    }
                }

                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                }

                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                }
            })

            directoriesAdapter = AddTorrentDirectoriesAdapter.setupPopup(download_directory_dropdown, download_directory_edit)

            if (savedInstanceState == null) {
                download_directory_edit.setText(Rpc.serverSettings.downloadDirectory())
                priority_spinner.setSelection(1)
                start_downloading_check_box.isChecked = Rpc.serverSettings.startAddedTorrents()
            }

            Rpc.gotDownloadDirFreeSpaceListener = { bytes ->
                val text = download_directory_edit.text?.trim()
                if (!text.isNullOrEmpty() && Rpc.serverSettings.downloadDirectory()?.contentEquals(text) == true) {
                    free_space_text_view.text = getString(R.string.free_space, Utils.formatByteSize(requireContext(), bytes))
                    free_space_text_view.visibility = View.VISIBLE
                }
            }

            Rpc.gotFreeSpaceForPathListener = { path, success, bytes ->
                val text = download_directory_edit.text?.trim()
                if (!text.isNullOrEmpty() && path.contentEquals(text)) {
                    if (success) {
                        free_space_text_view.text = getString(R.string.free_space, Utils.formatByteSize(requireContext(), bytes))
                    } else {
                        free_space_text_view.text = getString(R.string.free_space_error)
                    }
                }
            }
        }

        override fun onDestroyView() {
            Rpc.gotDownloadDirFreeSpaceListener = null
            Rpc.gotFreeSpaceForPathListener = null
            super.onDestroyView()
        }

        fun check(): Boolean {
            if (download_directory_edit.text?.trimmedLength() ?: 0 == 0) {
                download_directory_edit.error = getString(R.string.empty_field_error)
                return false
            }
            return true
        }
    }

    class FilesFragment : Fragment(R.layout.add_torrent_file_files_fragment) {
        private val mainFragment: AddTorrentFileFragment
            get() = requireParentFragment() as AddTorrentFileFragment

        var adapter: Adapter? = null
            private set

        override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
            super.onViewCreated(view, savedInstanceState)

            val parser = mainFragment.torrentFileParser

            val adapter = Adapter(requireActivity() as AppCompatActivity, parser.rootDirectory)
            this.adapter = adapter

            files_view.adapter = adapter
            files_view.layoutManager = LinearLayoutManager(activity)
            files_view.addItemDecoration(DividerItemDecoration(activity,
                    DividerItemDecoration.VERTICAL))
            files_view.itemAnimator = null

            if (parser.status == TorrentFileParser.Status.Loaded) {
                adapter.restoreInstanceState(savedInstanceState)
            }
        }

        override fun onDestroyView() {
            adapter = null
            super.onDestroyView()
        }

        override fun onSaveInstanceState(outState: Bundle) {
            adapter?.saveInstanceState(outState)
        }

        fun treeCreated() {
            adapter?.restoreInstanceState(null)
        }

        class Adapter(private val activity: AppCompatActivity,
                      rootDirectory: Directory) : BaseTorrentFilesAdapter(rootDirectory) {
            init {
                initSelector(activity, ActionModeCallback())
            }

            override fun onCreateViewHolder(parent: ViewGroup,
                                            viewType: Int): RecyclerView.ViewHolder {
                if (viewType == TYPE_ITEM) {
                    return ItemHolder(this,
                                      selector,
                                      LayoutInflater.from(parent.context).inflate(R.layout.local_torrent_file_list_item,
                                                                                  parent,
                                                                                  false))
                }
                return super.onCreateViewHolder(parent, viewType)
            }

            override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
                super.onBindViewHolder(holder, position)
                if (holder.itemViewType == TYPE_ITEM) {
                    (holder as ItemHolder).sizeTextView.text =
                            Utils.formatByteSize(activity, holder.item.size)
                }
            }

            private class ItemHolder(adapter: BaseTorrentFilesAdapter,
                                     selector: Selector<Item, Int>,
                                     itemView: View) : BaseItemHolder(adapter, selector, itemView) {
                val sizeTextView = itemView.size_text_view!!
            }

            private inner class ActionModeCallback : BaseActionModeCallback() {
                override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
                    super.onCreateActionMode(mode, menu)
                    mode.menuInflater.inflate(R.menu.select_all_menu, menu)
                    return true
                }
            }
        }
    }
}