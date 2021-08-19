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

package org.equeim.tremotesf.ui.addtorrent

import android.content.ContentResolver
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
import androidx.core.os.bundleOf
import androidx.core.text.trimmedLength
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.SavedStateViewModelFactory
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayoutMediator
import org.equeim.libtremotesf.RpcConnectionState
import org.equeim.libtremotesf.StringMap
import org.equeim.tremotesf.R
import org.equeim.tremotesf.databinding.AddTorrentFileFilesFragmentBinding
import org.equeim.tremotesf.databinding.AddTorrentFileFragmentBinding
import org.equeim.tremotesf.databinding.AddTorrentFileInfoFragmentBinding
import org.equeim.tremotesf.databinding.DownloadDirectoryEditBinding
import org.equeim.tremotesf.databinding.LocalTorrentFileListItemBinding
import org.equeim.tremotesf.rpc.GlobalRpc
import org.equeim.tremotesf.rpc.statusString
import org.equeim.tremotesf.ui.BaseTorrentFilesAdapter
import org.equeim.tremotesf.ui.NavigationActivity
import org.equeim.tremotesf.ui.SelectionTracker
import org.equeim.tremotesf.ui.TorrentFileRenameDialogFragment
import org.equeim.tremotesf.ui.utils.ArrayDropdownAdapter
import org.equeim.tremotesf.ui.utils.FormatUtils
import org.equeim.tremotesf.ui.utils.application
import org.equeim.tremotesf.ui.utils.findFragment
import org.equeim.tremotesf.ui.utils.hideKeyboard
import org.equeim.tremotesf.ui.utils.showSnackbar
import org.equeim.tremotesf.ui.utils.viewBinding
import org.equeim.tremotesf.ui.utils.collectWhenStarted
import timber.log.Timber


class AddTorrentFileFragment : AddTorrentFragment(
    R.layout.add_torrent_file_fragment,
    R.string.add_torrent_file,
    R.menu.add_torrent_fragment_menu
) {
    companion object {
        val SCHEMES = arrayOf(ContentResolver.SCHEME_FILE, ContentResolver.SCHEME_CONTENT)

        fun setupDownloadDirectoryEdit(
            binding: DownloadDirectoryEditBinding,
            fragment: Fragment,
            savedInstanceState: Bundle?
        ): AddTorrentDirectoriesAdapter {
            val downloadDirectoryEdit = binding.downloadDirectoryEdit
            val downloadDirectoryLayout = binding.downloadDirectoryLayout
            downloadDirectoryEdit.doAfterTextChanged {
                val path = it?.trim()
                when {
                    path.isNullOrEmpty() -> {
                        downloadDirectoryLayout.helperText = null
                    }
                    GlobalRpc.serverSettings.canShowFreeSpaceForPath() -> {
                        GlobalRpc.nativeInstance.getFreeSpaceForPath(path.toString())
                    }
                    GlobalRpc.serverSettings.downloadDirectory?.contentEquals(path) == true -> {
                        GlobalRpc.nativeInstance.getDownloadDirFreeSpace()
                    }
                    else -> {
                        downloadDirectoryLayout.helperText = null
                    }
                }
            }

            if (savedInstanceState == null) {
                downloadDirectoryEdit.setText(GlobalRpc.serverSettings.downloadDirectory)
            }

            val directoriesAdapter =
                AddTorrentDirectoriesAdapter(downloadDirectoryEdit, savedInstanceState)
            downloadDirectoryEdit.setAdapter(directoriesAdapter)

            GlobalRpc.gotDownloadDirFreeSpaceEvents.collectWhenStarted(fragment.viewLifecycleOwner) { bytes ->
                val text = downloadDirectoryEdit.text?.trim()
                if (!text.isNullOrEmpty() && GlobalRpc.serverSettings.downloadDirectory?.contentEquals(
                        text
                    ) == true
                ) {
                    downloadDirectoryLayout.helperText = fragment.getString(
                        R.string.free_space,
                        FormatUtils.formatByteSize(fragment.requireContext(), bytes)
                    )
                }
            }

            GlobalRpc.gotFreeSpaceForPathEvents.collectWhenStarted(fragment.viewLifecycleOwner) { (path, success, bytes) ->
                val text = downloadDirectoryEdit.text?.trim()
                if (!text.isNullOrEmpty() && path.contentEquals(text)) {
                    downloadDirectoryLayout.helperText = if (success) {
                        fragment.getString(
                            R.string.free_space,
                            FormatUtils.formatByteSize(fragment.requireContext(), bytes)
                        )
                    } else {
                        fragment.getString(R.string.free_space_error)
                    }
                }
            }

            return directoriesAdapter
        }
    }

    private val model: AddTorrentFileModel by viewModels<AddTorrentFileModelImpl> {
        val args = AddTorrentFileFragmentArgs.fromBundle(requireArguments())
        SavedStateViewModelFactory(
            requireContext().application,
            this,
            bundleOf(AddTorrentFileModel::uri.name to args.uri)
        )
    }

    private val binding by viewBinding(AddTorrentFileFragmentBinding::bind)

    private var doneMenuItem: MenuItem? = null
    private var pagerAdapter: PagerAdapter? = null
    private var backPressedCallback: OnBackPressedCallback? = null
    private var snackbar: Snackbar? = null

    private var done = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Timber.i("onCreate: arguments = $arguments")

        with(model.storagePermissionHelper) {
            val launcher = registerWithFragment(this@AddTorrentFileFragment)
            if (model.needStoragePermission) {
                if (!checkPermission(requireContext())) {
                    lifecycleScope.launchWhenStarted {
                        requestPermission(this@AddTorrentFileFragment, launcher)
                    }
                }
            }
        }

        TorrentFileRenameDialogFragment.setFragmentResultListener(this) { (_, filePath, newName) ->
            model.renamedFiles[filePath] = newName
            model.filesTree.renameFile(filePath, newName)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val pagerAdapter = PagerAdapter(this)
        this.pagerAdapter = pagerAdapter
        binding.pager.adapter = pagerAdapter
        TabLayoutMediator(binding.tabLayout, binding.pager) { tab, position ->
            tab.setText(PagerAdapter.getTitle(position))
        }.attach()

        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner) {
            if (done ||
                binding.pager.currentItem != PagerAdapter.Tab.Files.ordinal || !model.filesTree.navigateUp()
            ) {
                isEnabled = false
                requireActivity().onBackPressedDispatcher.onBackPressed()
            }
        }

        binding.pager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            private var previousPage = -1

            override fun onPageSelected(position: Int) {
                if (previousPage != -1) {
                    findFragment<FilesFragment>()?.adapter?.selectionTracker?.clearSelection()
                    hideKeyboard()
                }
                previousPage = position
            }
        })

        model.viewUpdateData.collectWhenStarted(viewLifecycleOwner, ::updateView)
    }

    override fun onStart() {
        super.onStart()
        with(model) {
            if (needStoragePermission && !storagePermissionHelper.permissionGranted.value) {
                storagePermissionHelper.checkPermission(requireContext())
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        with(binding.pager) {
            if (isVisible) {
                model.rememberedPagerItem = currentItem
            }
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
        inflater.inflate(R.menu.add_torrent_fragment_menu, menu)
    }

    override fun onToolbarMenuItemClicked(menuItem: MenuItem): Boolean {
        if (menuItem.itemId != R.id.done) {
            return false
        }
        val infoFragment = findFragment<InfoFragment>()
        if (infoFragment?.check() == true) {
            val priorities = model.getFilePriorities()
            GlobalRpc.nativeInstance.addTorrentFile(
                model.detachFd(),
                infoFragment.binding.downloadDirectoryLayout.downloadDirectoryEdit.text.toString(),
                priorities.unwantedFiles.toIntArray(),
                priorities.highPriorityFiles.toIntArray(),
                priorities.lowPriorityFiles.toIntArray(),
                StringMap().apply { putAll(model.renamedFiles) },
                priorityItemEnums[priorityItems.indexOf(infoFragment.binding.priorityView.text.toString())].swigValue(),
                infoFragment.binding.startDownloadingCheckBox.isChecked
            )
            infoFragment.directoriesAdapter?.save()
            done = true
            activity?.onBackPressed()
            return true
        }
        return false
    }

    private fun updateView(viewUpdateData: AddTorrentFileModel.ViewUpdateData) {
        val (parserStatus, rpcStatus, hasStoragePermission) = viewUpdateData

        with(binding) {
            if (rpcStatus.isConnected && parserStatus == AddTorrentFileModel.ParserStatus.Loaded) {
                this@AddTorrentFileFragment.toolbar?.apply {
                    (layoutParams as AppBarLayout.LayoutParams).scrollFlags =
                        AppBarLayout.LayoutParams.SCROLL_FLAG_SCROLL or
                                AppBarLayout.LayoutParams.SCROLL_FLAG_SNAP or
                                AppBarLayout.LayoutParams.SCROLL_FLAG_ENTER_ALWAYS
                    subtitle = model.torrentName
                }
                doneMenuItem?.isVisible = true

                tabLayout.visibility = View.VISIBLE
                pager.visibility = View.VISIBLE

                placeholderLayout.visibility = View.GONE

                if (model.rememberedPagerItem != -1) {
                    pager.setCurrentItem(model.rememberedPagerItem, false)
                    model.rememberedPagerItem = -1
                }
            } else {
                placeholder.text = if (!hasStoragePermission && model.needStoragePermission) {
                    getString(R.string.storage_permission_error)
                } else {
                    when (parserStatus) {
                        AddTorrentFileModel.ParserStatus.Loading -> getString(R.string.loading)
                        AddTorrentFileModel.ParserStatus.FileIsTooLarge -> getString(R.string.file_is_too_large)
                        AddTorrentFileModel.ParserStatus.ReadingError -> getString(R.string.file_reading_error)
                        AddTorrentFileModel.ParserStatus.ParsingError -> getString(R.string.file_parsing_error)
                        AddTorrentFileModel.ParserStatus.Loaded -> rpcStatus.statusString
                        else -> null
                    }
                }

                progressBar.visibility =
                    if (parserStatus == AddTorrentFileModel.ParserStatus.Loading ||
                        (rpcStatus.connectionState == RpcConnectionState.Connecting && parserStatus == AddTorrentFileModel.ParserStatus.Loaded)
                    ) {
                        View.VISIBLE
                    } else {
                        View.GONE
                    }

                placeholderLayout.visibility = View.VISIBLE

                this@AddTorrentFileFragment.toolbar?.apply {
                    (layoutParams as AppBarLayout.LayoutParams).scrollFlags = 0
                    subtitle = null
                }
                doneMenuItem?.isVisible = false

                hideKeyboard()

                tabLayout.visibility = View.GONE
                pager.visibility = View.GONE
                pager.setCurrentItem(0, false)
                placeholder.visibility = View.VISIBLE

                if (parserStatus == AddTorrentFileModel.ParserStatus.Loaded) {
                    if (rpcStatus.connectionState == RpcConnectionState.Disconnected) {
                        snackbar = coordinatorLayout.showSnackbar(
                            "",
                            Snackbar.LENGTH_INDEFINITE,
                            R.string.connect,
                            GlobalRpc.nativeInstance::connect,
                        ) {
                            snackbar = null
                        }
                    } else {
                        snackbar?.dismiss()
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
        val binding by viewBinding(AddTorrentFileInfoFragmentBinding::bind)
        var directoriesAdapter: AddTorrentDirectoriesAdapter? = null

        override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
            with(binding) {
                priorityView.setText(R.string.normal_priority)
                priorityView.setAdapter(ArrayDropdownAdapter((requireParentFragment() as AddTorrentFileFragment).priorityItems))

                directoriesAdapter = setupDownloadDirectoryEdit(
                    downloadDirectoryLayout,
                    this@InfoFragment,
                    savedInstanceState
                )

                startDownloadingCheckBox.isChecked = GlobalRpc.serverSettings.startAddedTorrents
            }
        }

        override fun onSaveInstanceState(outState: Bundle) {
            directoriesAdapter?.saveInstanceState(outState)
        }

        fun check(): Boolean {
            val ret: Boolean
            with(binding.downloadDirectoryLayout) {
                downloadDirectoryLayout.error =
                    if (downloadDirectoryEdit.text.trimmedLength() == 0) {
                        ret = false
                        getString(R.string.empty_field_error)
                    } else {
                        ret = true
                        null
                    }
            }
            return ret
        }
    }

    class FilesFragment : Fragment(R.layout.add_torrent_file_files_fragment) {
        private val mainFragment: AddTorrentFileFragment
            get() = requireParentFragment() as AddTorrentFileFragment

        var adapter: Adapter? = null
            private set

        override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
            super.onViewCreated(view, savedInstanceState)

            val model = mainFragment.model

            val adapter = Adapter(model, this, requireActivity() as NavigationActivity)
            this.adapter = adapter

            AddTorrentFileFilesFragmentBinding.bind(view).filesView.apply {
                this.adapter = adapter
                layoutManager = LinearLayoutManager(activity)
                addItemDecoration(DividerItemDecoration(requireContext(), DividerItemDecoration.VERTICAL))
                (itemAnimator as DefaultItemAnimator).supportsChangeAnimations = false
            }

            model.filesTree.items.collectWhenStarted(viewLifecycleOwner, adapter::update)
        }

        override fun onDestroyView() {
            adapter = null
            super.onDestroyView()
        }

        class Adapter(
            private val model: AddTorrentFileModel,
            fragment: Fragment,
            private val activity: NavigationActivity
        ) : BaseTorrentFilesAdapter(model.filesTree, fragment) {
            override fun onCreateViewHolder(
                parent: ViewGroup,
                viewType: Int
            ): RecyclerView.ViewHolder {
                if (viewType == TYPE_ITEM) {
                    return ItemHolder(
                        this,
                        selectionTracker,
                        LocalTorrentFileListItemBinding.inflate(
                            LayoutInflater.from(parent.context),
                            parent,
                            false
                        )
                    )
                }
                return super.onCreateViewHolder(parent, viewType)
            }

            override fun allowStateRestoring(): Boolean {
                return model.parserStatus.value == AddTorrentFileModel.ParserStatus.Loaded
            }

            override fun navigateToRenameDialog(path: String, name: String) {
                activity.navigate(
                    AddTorrentFileFragmentDirections.toTorrentFileRenameDialog(
                        path,
                        name
                    )
                )
            }

            private class ItemHolder(
                private val adapter: Adapter,
                selectionTracker: SelectionTracker<Int>,
                val binding: LocalTorrentFileListItemBinding
            ) : BaseItemHolder(adapter, selectionTracker, binding.root) {
                override fun update() {
                    super.update()
                    val context = binding.sizeTextView.context
                    binding.sizeTextView.text =
                        FormatUtils.formatByteSize(
                            context,
                            adapter.getItem(bindingAdapterPosition)!!.size
                        )
                }
            }
        }
    }
}

