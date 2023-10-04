// SPDX-FileCopyrightText: 2017-2023 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package org.equeim.tremotesf.ui.addtorrent

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.activity.addCallback
import androidx.annotation.StringRes
import androidx.core.text.trimmedLength
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.withStarted
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.tabs.TabLayoutMediator
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import org.equeim.tremotesf.R
import org.equeim.tremotesf.databinding.AddTorrentFileFilesFragmentBinding
import org.equeim.tremotesf.databinding.AddTorrentFileFragmentBinding
import org.equeim.tremotesf.databinding.AddTorrentFileInfoFragmentBinding
import org.equeim.tremotesf.databinding.LocalTorrentFileListItemBinding
import org.equeim.tremotesf.rpc.GlobalRpcClient
import org.equeim.tremotesf.rpc.getErrorString
import org.equeim.tremotesf.torrentfile.rpc.RpcRequestState
import org.equeim.tremotesf.torrentfile.rpc.requests.FileSize
import org.equeim.tremotesf.torrentfile.rpc.requests.addTorrentFile
import org.equeim.tremotesf.ui.BaseTorrentFilesAdapter
import org.equeim.tremotesf.ui.NavigationActivity
import org.equeim.tremotesf.ui.SelectionTracker
import org.equeim.tremotesf.ui.Settings
import org.equeim.tremotesf.ui.TorrentFileRenameDialogFragment
import org.equeim.tremotesf.ui.applyNavigationBarBottomInset
import org.equeim.tremotesf.ui.utils.ArrayDropdownAdapter
import org.equeim.tremotesf.ui.utils.FormatUtils
import org.equeim.tremotesf.ui.utils.bindingAdapterPositionOrNull
import org.equeim.tremotesf.ui.utils.currentItemFlow
import org.equeim.tremotesf.ui.utils.extendWhenImeIsHidden
import org.equeim.tremotesf.ui.utils.findFragment
import org.equeim.tremotesf.ui.utils.hideKeyboard
import org.equeim.tremotesf.ui.utils.launchAndCollectWhenStarted
import org.equeim.tremotesf.ui.utils.viewLifecycleObject
import timber.log.Timber


class AddTorrentFileFragment : AddTorrentFragment(
    R.layout.add_torrent_file_fragment,
    R.string.add_torrent_file,
    0
) {
    private val args: AddTorrentFileFragmentArgs by navArgs()
    val model: AddTorrentFileModel by viewModels<AddTorrentFileModelImpl> {
        viewModelFactory {
            initializer {
                AddTorrentFileModelImpl(
                    args,
                    checkNotNull(get(ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY)),
                    createSavedStateHandle()
                )
            }
        }
    }

    private val binding by viewLifecycleObject(AddTorrentFileFragmentBinding::bind)

    private lateinit var backCallback: OnBackPressedCallback

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Timber.i("onCreate: arguments = $arguments")

        model.storagePermissionHelper?.let { helper ->
            val launcher = helper.registerWithFragment(this@AddTorrentFileFragment)
            if (model.needStoragePermission) {
                if (!helper.checkPermission(requireContext())) {
                    lifecycleScope.launch {
                        lifecycle.withStarted {
                            helper.requestPermission(this@AddTorrentFileFragment, launcher)
                        }
                    }
                }
            }
        }
    }

    override fun onViewStateRestored(savedInstanceState: Bundle?) {
        super.onViewStateRestored(savedInstanceState)

        binding.pager.adapter = PagerAdapter(this)
        TabLayoutMediator(binding.tabLayout, binding.pager) { tab, position ->
            tab.setText(PagerAdapter.getTitle(position))
        }.attach()

        binding.addButton.setOnClickListener { addTorrentFile() }
        binding.addButton.extendWhenImeIsHidden(requiredActivity.windowInsets, viewLifecycleOwner)

        backCallback = requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner) {
            model.filesTree.navigateUp()
        }
        combine(binding.pager.currentItemFlow, model.filesTree.isAtRoot) { tab, isAtRoot ->
            tab == PagerAdapter.Tab.Files.ordinal && !isAtRoot
        }.distinctUntilChanged().launchAndCollectWhenStarted(viewLifecycleOwner, backCallback::isEnabled::set)

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

        viewLifecycleOwner.lifecycleScope.launch {
            if (Settings.quickReturn.get()) {
                toolbar.setOnClickListener {
                    Timber.d("onViewStateRestored: clicked, current tab = ${PagerAdapter.Tab.entries[binding.pager.currentItem]}")
                    if (PagerAdapter.Tab.entries[binding.pager.currentItem] == PagerAdapter.Tab.Files) {
                        childFragmentManager.fragments
                            .filterIsInstance<FilesFragment>()
                            .singleOrNull()
                            ?.onToolbarClicked()
                    }
                }
            }
        }

        model.viewUpdateData.launchAndCollectWhenStarted(viewLifecycleOwner, ::updateView)

        TorrentFileRenameDialogFragment.setFragmentResultListener(this) { (_, filePath, newName) ->
            model.renamedFiles[filePath] = newName
            model.filesTree.renameFile(filePath, newName)
        }
    }

    override fun onStart() {
        super.onStart()
        with(model) {
            storagePermissionHelper?.let { helper ->
                if (needStoragePermission && !helper.permissionGranted.value) {
                    helper.checkPermission(requireContext())
                }
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

    private fun addTorrentFile() {
        val infoFragment = findFragment<InfoFragment>()
        if (infoFragment?.check() != true) return
        val fd = model.detachFd() ?: return
        val priorities = model.getFilePriorities()
        val downloadDirectory = infoFragment.binding.downloadDirectoryLayout.downloadDirectoryEdit.text.toString()
        val bandwidthPriority =
            priorityItemEnums[priorityItems.indexOf(infoFragment.binding.priorityView.text.toString())]
        val renamedFiles = model.renamedFiles
        val startDownloading = infoFragment.binding.startDownloadingCheckBox.isChecked
        GlobalRpcClient.performBackgroundRpcRequest(R.string.add_torrent_error) {
            addTorrentFile(
                torrentFile = fd,
                downloadDirectory = downloadDirectory,
                bandwidthPriority = bandwidthPriority,
                unwantedFiles = priorities.unwantedFiles,
                highPriorityFiles = priorities.highPriorityFiles,
                lowPriorityFiles = priorities.lowPriorityFiles,
                renamedFiles = renamedFiles,
                start = startDownloading
            )
        }
        infoFragment.directoriesAdapter.save(infoFragment.binding.downloadDirectoryLayout.downloadDirectoryEdit)
        backCallback.remove()
        requiredActivity.onBackPressedDispatcher.onBackPressed()
    }

    private fun updateView(viewUpdateData: AddTorrentFileModel.ViewUpdateData) {
        val (parserStatus, downloadingSettings, hasStoragePermission) = viewUpdateData

        with(binding) {
            if (downloadingSettings is RpcRequestState.Loaded && parserStatus == AddTorrentFileModel.ParserStatus.Loaded) {
                this@AddTorrentFileFragment.toolbar.apply {
                    (layoutParams as AppBarLayout.LayoutParams).scrollFlags =
                        AppBarLayout.LayoutParams.SCROLL_FLAG_SCROLL or
                                AppBarLayout.LayoutParams.SCROLL_FLAG_SNAP or
                                AppBarLayout.LayoutParams.SCROLL_FLAG_ENTER_ALWAYS
                    subtitle = model.torrentName
                }

                tabLayout.isVisible = true
                pager.isVisible = true

                placeholderView.root.isVisible = false

                addButton.show()

                if (model.rememberedPagerItem != -1) {
                    pager.setCurrentItem(model.rememberedPagerItem, false)
                    model.rememberedPagerItem = -1
                }
            } else {
                placeholderView.placeholder.text = if (!hasStoragePermission && model.needStoragePermission) {
                    getString(R.string.storage_permission_error)
                } else {
                    when (parserStatus) {
                        AddTorrentFileModel.ParserStatus.Loading -> getString(R.string.loading)
                        AddTorrentFileModel.ParserStatus.FileIsTooLarge -> getString(R.string.file_is_too_large)
                        AddTorrentFileModel.ParserStatus.ReadingError -> getString(R.string.file_reading_error)
                        AddTorrentFileModel.ParserStatus.ParsingError -> getString(R.string.file_parsing_error)
                        else -> when (downloadingSettings) {
                            is RpcRequestState.Loading -> getString(R.string.loading)
                            is RpcRequestState.Error -> downloadingSettings.error.getErrorString(requireContext())
                            is RpcRequestState.Loaded -> null
                        }
                    }
                }

                placeholderView.progressBar.isVisible =
                    downloadingSettings is RpcRequestState.Loading || parserStatus == AddTorrentFileModel.ParserStatus.Loading

                placeholderView.placeholderLayout.isVisible = true

                addButton.hide()

                this@AddTorrentFileFragment.toolbar.apply {
                    (layoutParams as AppBarLayout.LayoutParams).scrollFlags = 0
                    subtitle = null
                }

                hideKeyboard()

                tabLayout.isVisible = false
                pager.isVisible = false
                pager.setCurrentItem(0, false)
                placeholderView.placeholder.isVisible = true
            }
        }
    }

    class PagerAdapter(fragment: Fragment) : FragmentStateAdapter(fragment) {
        companion object {
            @StringRes
            fun getTitle(position: Int): Int {
                return when (Tab.entries[position]) {
                    Tab.Info -> R.string.information
                    Tab.Files -> R.string.files
                }
            }
        }

        enum class Tab {
            Info,
            Files
        }

        override fun getItemCount() = Tab.entries.size

        override fun createFragment(position: Int): Fragment {
            return when (Tab.entries[position]) {
                Tab.Info -> InfoFragment()
                Tab.Files -> FilesFragment()
            }
        }
    }

    class InfoFragment : Fragment(R.layout.add_torrent_file_info_fragment) {
        val binding by viewLifecycleObject(AddTorrentFileInfoFragmentBinding::bind)
        var directoriesAdapter: AddTorrentDirectoriesAdapter by viewLifecycleObject()
        private var freeSpaceJob: Job? = null

        override fun onViewStateRestored(savedInstanceState: Bundle?) {
            super.onViewStateRestored(savedInstanceState)

            val model = (requireParentFragment() as AddTorrentFileFragment).model

            directoriesAdapter = AddTorrentDirectoriesAdapter(viewLifecycleOwner.lifecycleScope, savedInstanceState)

            with(binding) {
                priorityView.setAdapter(ArrayDropdownAdapter((requireParentFragment() as AddTorrentFileFragment).priorityItems))
                downloadDirectoryLayout.downloadDirectoryEdit.setAdapter(directoriesAdapter)
                downloadDirectoryLayout.downloadDirectoryEdit.doAfterTextChanged { path ->
                    freeSpaceJob?.cancel()
                    freeSpaceJob = null
                    if (!path.isNullOrBlank()) {
                        freeSpaceJob = lifecycleScope.launch {
                            binding.downloadDirectoryLayout.downloadDirectoryLayout.helperText =
                                model.getFreeSpace(path.toString())?.let {
                                    getString(
                                        R.string.free_space,
                                        FormatUtils.formatFileSize(requireContext(), it)
                                    )
                                }
                            freeSpaceJob = null
                        }
                    }
                }
            }

            model.viewUpdateData.launchAndCollectWhenStarted(viewLifecycleOwner) {
                if (it.parserStatus == AddTorrentFileModel.ParserStatus.Loaded && it.downloadingSettings is RpcRequestState.Loaded) {
                    with(binding) {
                        if (model.shouldSetInitialRpcInputs) {
                            val downloadingSettings = it.downloadingSettings.response
                            downloadDirectoryLayout.downloadDirectoryEdit.setText(
                                model.getInitialDownloadDirectory(
                                    downloadingSettings
                                )
                            )
                            startDownloadingCheckBox.isChecked = downloadingSettings.startAddedTorrents
                            model.shouldSetInitialRpcInputs = false
                        }
                        if (model.shouldSetInitialLocalInputs) {
                            priorityView.setText(R.string.normal_priority)
                            model.shouldSetInitialLocalInputs = false
                        }
                    }
                }
            }

            applyNavigationBarBottomInset()
        }

        override fun onSaveInstanceState(outState: Bundle) {
            directoriesAdapter.saveInstanceState(outState)
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

        private val binding by viewLifecycleObject(AddTorrentFileFilesFragmentBinding::bind)
        val adapter: Adapter by viewLifecycleObject {
            Adapter(mainFragment.model, this, requireActivity() as NavigationActivity)
        }

        override fun onViewStateRestored(savedInstanceState: Bundle?) {
            super.onViewStateRestored(savedInstanceState)

            binding.filesView.apply {
                adapter = this@FilesFragment.adapter
                layoutManager = LinearLayoutManager(activity)
                addItemDecoration(
                    DividerItemDecoration(
                        requireContext(),
                        DividerItemDecoration.VERTICAL
                    )
                )
                (itemAnimator as DefaultItemAnimator).supportsChangeAnimations = false
            }

            mainFragment.model.filesTree.items.launchAndCollectWhenStarted(
                viewLifecycleOwner,
                adapter::update
            )

            applyNavigationBarBottomInset()
        }

        fun onToolbarClicked() {
            binding.filesView.scrollToPosition(0)
        }

        class Adapter(
            model: AddTorrentFileModel,
            fragment: Fragment,
            private val activity: NavigationActivity,
        ) : BaseTorrentFilesAdapter(model.filesTree, fragment) {
            override fun onCreateViewHolder(
                parent: ViewGroup,
                viewType: Int,
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

            override fun navigateToRenameDialog(path: String, name: String) {
                activity.navigate(
                    AddTorrentFileFragmentDirections.toTorrentFileRenameDialog(
                        filePath = path,
                        fileName = name,
                        torrentHashString = null
                    )
                )
            }

            private class ItemHolder(
                private val adapter: Adapter,
                selectionTracker: SelectionTracker<Int>,
                val binding: LocalTorrentFileListItemBinding,
            ) : BaseItemHolder(adapter, selectionTracker, binding.root) {
                override fun update() {
                    super.update()
                    bindingAdapterPositionOrNull?.let(adapter::getItem)?.let { item ->
                        binding.sizeTextView.apply {
                            text = FormatUtils.formatFileSize(context, FileSize.fromBytes(item.size))
                        }
                    }
                }
            }
        }
    }
}

