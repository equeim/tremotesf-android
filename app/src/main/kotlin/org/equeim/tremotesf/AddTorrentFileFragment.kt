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
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup

import androidx.activity.OnBackPressedCallback
import androidx.activity.addCallback
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.core.text.trimmedLength
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.observe
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2

import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayoutMediator

import org.equeim.libtremotesf.StringMap
import org.equeim.tremotesf.utils.ArrayDropdownAdapter
import org.equeim.tremotesf.utils.BasicMediatorLiveData
import org.equeim.tremotesf.utils.Utils
import org.equeim.tremotesf.utils.findFragment
import org.equeim.tremotesf.utils.hideKeyboard
import org.equeim.tremotesf.utils.showSnackbar

import kotlinx.android.synthetic.main.add_torrent_file_files_fragment.*
import kotlinx.android.synthetic.main.add_torrent_file_info_fragment.*
import kotlinx.android.synthetic.main.add_torrent_file_fragment.*
import kotlinx.android.synthetic.main.download_directory_edit.*
import kotlinx.android.synthetic.main.local_torrent_file_list_item.view.*


class AddTorrentFileFragment : AddTorrentFragment(R.layout.add_torrent_file_fragment,
                                                  R.string.add_torrent_file,
                                                  R.menu.add_torrent_activity_menu), TorrentFileRenameDialogFragment.PrimaryFragment {
    companion object {
        fun setupDownloadDirectoryEdit(fragment: Fragment, savedInstanceState: Bundle?): AddTorrentDirectoriesAdapter {
            fragment.apply {
                download_directory_edit.doAfterTextChanged {
                    val path = it?.trim()
                    when {
                        path.isNullOrEmpty() -> {
                            download_directory_layout.helperText = null
                        }
                        Rpc.serverSettings.canShowFreeSpaceForPath() -> {
                            Rpc.nativeInstance.getFreeSpaceForPath(path.toString())
                        }
                        Rpc.serverSettings.downloadDirectory()?.contentEquals(path) == true -> {
                            Rpc.nativeInstance.getDownloadDirFreeSpace()
                        }
                        else -> {
                            download_directory_layout.helperText = null
                        }
                    }
                }

                if (savedInstanceState == null) {
                    download_directory_edit.setText(Rpc.serverSettings.downloadDirectory())
                }

                val directoriesAdapter = AddTorrentDirectoriesAdapter(download_directory_edit, savedInstanceState)
                download_directory_edit.setAdapter(directoriesAdapter)

                Rpc.gotDownloadDirFreeSpaceEvent.observe(viewLifecycleOwner) { bytes ->
                    val text = download_directory_edit.text?.trim()
                    if (!text.isNullOrEmpty() && Rpc.serverSettings.downloadDirectory()?.contentEquals(text) == true) {
                        download_directory_layout.helperText = getString(R.string.free_space, Utils.formatByteSize(requireContext(), bytes))
                    }
                }

                Rpc.gotFreeSpaceForPathEvent.observe(viewLifecycleOwner) { (path, success, bytes) ->
                    val text = download_directory_edit.text?.trim()
                    if (!text.isNullOrEmpty() && path.contentEquals(text)) {
                        download_directory_layout.helperText = if (success) {
                            getString(R.string.free_space, Utils.formatByteSize(requireContext(), bytes))
                        } else {
                            getString(R.string.free_space_error)
                        }
                    }
                }

                return directoriesAdapter
            }
        }
    }

    private var doneMenuItem: MenuItem? = null
    private var pagerAdapter: PagerAdapter? = null
    private var backPressedCallback: OnBackPressedCallback? = null
    private var snackbar: Snackbar? = null

    val model: AddTorrentFileModel by viewModels()

    private lateinit var uri: Uri

    private var noPermission = false

    private var done = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        uri = requireArguments().getString(URI)!!.toUri()
        if (uri.scheme == ContentResolver.SCHEME_FILE &&
                ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_DENIED) {
            requestPermissions(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), 0)
        } else {
            model.load(uri)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int,
                                            permissions: Array<out String>,
                                            grantResults: IntArray) {
        if (grantResults.first() == PackageManager.PERMISSION_GRANTED) {
            model.load(uri)
        } else {
            noPermission = true
            updateView(model.status.value)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val pagerAdapter = PagerAdapter(this)
        this.pagerAdapter = pagerAdapter
        pager.adapter = pagerAdapter
        TabLayoutMediator(tab_layout, pager) { tab, position ->
            tab.setText(PagerAdapter.getTitle(position))
        }.attach()

        requireActivity().onBackPressedDispatcher.addCallback(this) {
            if (done ||
                    pager.currentItem != PagerAdapter.Tab.Files.ordinal ||
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

        BasicMediatorLiveData<Nothing>(Rpc.status, model.status)
                .observe(viewLifecycleOwner) {
                    updateView(model.status.value)
                }
    }

    override fun onDestroyView() {
        doneMenuItem = null
        pagerAdapter = null
        backPressedCallback = null
        snackbar = null
        super.onDestroyView()
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.add_torrent_activity_menu, menu)
        doneMenuItem = menu.findItem(R.id.done).apply {
            isVisible = (model.status.value == AddTorrentFileModel.ParserStatus.Loaded && Rpc.isConnected)
        }
    }

    override fun onToolbarMenuItemClicked(menuItem: MenuItem): Boolean {
        if (menuItem.itemId != R.id.done) {
            return false
        }
        val infoFragment = findFragment<InfoFragment>()
        if (infoFragment?.check() == true) {
            val priorities = model.getFilePriorities()
            Rpc.nativeInstance.addTorrentFile(model.fileData,
                                              infoFragment.download_directory_edit.text.toString(),
                                              priorities.unwantedFiles.toIntArray(),
                                              priorities.highPriorityFiles.toIntArray(),
                                              priorities.lowPriorityFiles.toIntArray(),
                                              StringMap().apply { putAll(model.renamedFiles) },
                                              priorityItemEnums[priorityItems.indexOf(infoFragment.priority_view.text.toString())],
                                              infoFragment.start_downloading_check_box.isChecked)
            infoFragment.directoriesAdapter?.save()
            done = true
            activity?.onBackPressed()
            return true
        }
        return false
    }

    override fun onRenameFile(torrentId: Int, filePath: String, newName: String) {
        model.renamedFiles[filePath] = newName
        findFragment<FilesFragment>()?.adapter?.fileRenamed(filePath, newName)
    }

    private fun updateView(parserStatus: AddTorrentFileModel.ParserStatus) {
        val view = this.view ?: return

        if (Rpc.isConnected && parserStatus == AddTorrentFileModel.ParserStatus.Loaded) {
            toolbar?.apply {
                (layoutParams as AppBarLayout.LayoutParams).scrollFlags =
                AppBarLayout.LayoutParams.SCROLL_FLAG_SCROLL or
                        AppBarLayout.LayoutParams.SCROLL_FLAG_SNAP or
                        AppBarLayout.LayoutParams.SCROLL_FLAG_ENTER_ALWAYS
                subtitle = model.torrentName
            }
            doneMenuItem?.isVisible = true

            tab_layout.visibility = View.VISIBLE
            pager.visibility = View.VISIBLE

            placeholder_layout.visibility = View.GONE
        } else {
            placeholder.text = if (noPermission) {
                getString(R.string.storage_permission_error)
            } else {
                when (parserStatus) {
                    AddTorrentFileModel.ParserStatus.Loading -> getString(R.string.loading)
                    AddTorrentFileModel.ParserStatus.FileIsTooLarge -> getString(R.string.file_is_too_large)
                    AddTorrentFileModel.ParserStatus.ReadingError -> getString(R.string.file_reading_error)
                    AddTorrentFileModel.ParserStatus.ParsingError -> getString(R.string.file_parsing_error)
                    AddTorrentFileModel.ParserStatus.Loaded -> Rpc.statusString
                    else -> null
                }
            }

            progress_bar.visibility = if (parserStatus == AddTorrentFileModel.ParserStatus.Loading ||
                    (Rpc.status.value == RpcStatus.Connecting && parserStatus == AddTorrentFileModel.ParserStatus.Loaded)) {
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

            if (parserStatus == AddTorrentFileModel.ParserStatus.Loaded) {
                when (Rpc.status.value) {
                    RpcStatus.Disconnected -> {
                        snackbar = view.showSnackbar("", Snackbar.LENGTH_INDEFINITE, R.string.connect) {
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

    class PagerAdapter(fragment: Fragment) : FragmentStateAdapter(fragment) {
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
            priority_view.setText(R.string.normal_priority)
            priority_view.setAdapter(ArrayDropdownAdapter((requireParentFragment() as AddTorrentFileFragment).priorityItems))

            directoriesAdapter = setupDownloadDirectoryEdit(this, savedInstanceState)

            start_downloading_check_box.isChecked = Rpc.serverSettings.startAddedTorrents()
        }

        override fun onSaveInstanceState(outState: Bundle) {
            directoriesAdapter?.saveInstanceState(outState)
        }

        fun check(): Boolean {
            val ret: Boolean
            download_directory_layout.error = if (download_directory_edit.text.trimmedLength() == 0) {
                ret = false
                getString(R.string.empty_field_error)
            } else {
                ret = true
                null
            }
            return ret
        }
    }

    class FilesFragment : Fragment(R.layout.add_torrent_file_files_fragment) {
        private val mainFragment: AddTorrentFileFragment
            get() = requireParentFragment() as AddTorrentFileFragment

        private var savedInstanceState: Bundle? = null

        var adapter: Adapter? = null
            private set

        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            this.savedInstanceState = savedInstanceState
        }

        override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
            super.onViewCreated(view, savedInstanceState)

            val model = mainFragment.model

            val adapter = Adapter(requireActivity() as NavigationActivity, model.rootDirectory)
            this.adapter = adapter

            files_view.adapter = adapter
            files_view.layoutManager = LinearLayoutManager(activity)
            files_view.addItemDecoration(DividerItemDecoration(activity,
                    DividerItemDecoration.VERTICAL))
            files_view.itemAnimator = null

            model.status.observe(viewLifecycleOwner) {
                adapter.restoreInstanceState(this.savedInstanceState)
                this.savedInstanceState = null
            }
        }

        override fun onDestroyView() {
            adapter = null
            super.onDestroyView()
        }

        override fun onSaveInstanceState(outState: Bundle) {
            adapter?.saveInstanceState(outState)
        }

        class Adapter(private val activity: NavigationActivity,
                      rootDirectory: Directory) : BaseTorrentFilesAdapter(rootDirectory, activity) {
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

            override fun onNavigateToRenameDialog(args: Bundle) {
                activity.navController.navigate(R.id.action_addTorrentFileFragment_to_torrentRenameDialogFragment, args)
            }

            private class ItemHolder(adapter: BaseTorrentFilesAdapter,
                                     selector: Selector<Item, Int>,
                                     itemView: View) : BaseItemHolder(adapter, selector, itemView) {
                val sizeTextView = itemView.size_text_view!!
            }
        }
    }
}