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

package org.equeim.tremotesf

import java.io.File

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.net.Uri

import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.view.LayoutInflater

import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.TextView

import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.fragment.app.viewModels
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

import org.equeim.tremotesf.databinding.FilePickerFragmentBinding
import org.equeim.tremotesf.utils.viewBinding

import kotlin.properties.Delegates


class FilePickerFragment : NavigationFragment(R.layout.file_picker_fragment,
                                              R.string.select_file,
                                              R.menu.file_picker_fragment_menu) {
    private val binding by viewBinding(FilePickerFragmentBinding::bind)
    private var adapter: FilePickerAdapter? = null

    private val model by viewModels<Model>()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val adapter = FilePickerAdapter(this, model)
        this.adapter = adapter

        binding.filesView.apply {
            this.adapter = adapter
            layoutManager = LinearLayoutManager(requireContext())
            addItemDecoration(DividerItemDecoration(requireContext(), DividerItemDecoration.VERTICAL))
            (itemAnimator as DefaultItemAnimator).supportsChangeAnimations = false
        }

        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_DENIED) {
            binding.filesView.visibility = View.GONE
            requestPermissions(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), 0)
        } else {
            adapter.init()
        }

        model.currentDirectory.observe(viewLifecycleOwner) {
            binding.currentDirectoryTextView.text = it.absolutePath
        }
    }

    override fun onToolbarMenuItemClicked(menuItem: MenuItem): Boolean {
        if (menuItem.itemId == R.id.primary_storage) {
            adapter?.navigateToHome()
            return true
        }
        return false
    }

    override fun onRequestPermissionsResult(requestCode: Int,
                                            permissions: Array<out String>,
                                            grantResults: IntArray) {
        with(binding) {
            if (grantResults.first() == PackageManager.PERMISSION_GRANTED) {
                filesView.visibility = View.VISIBLE
                adapter?.init()
            } else {
                placeholder.text = getString(R.string.storage_permission_error)
            }
        }
    }

    fun finish(fileUri: Uri) {
        navigate(R.id.action_filePickerFragment_to_addTorrentFileFragment, bundleOf(AddTorrentFragment.URI to fileUri.toString()))
    }

    fun updatePlaceholder() {
        binding.placeholder.text = if (adapter?.items?.isEmpty() == true) {
            getString(R.string.no_files)
        } else {
            null
        }
    }

    class Model(savedStateHandle: SavedStateHandle) : ViewModel() {
        companion object {
            private const val CURRENT_DIRECTORY = "currentDirectory"
        }
        @Suppress("DEPRECATION")
        val currentDirectory = savedStateHandle.getLiveData<File>(CURRENT_DIRECTORY, Environment.getExternalStorageDirectory())
    }

    private class FilePickerAdapter(private val fragment: FilePickerFragment, model: Model) : BaseFilesAdapter<File, File>() {
        private val filesFilter = { file: File -> file.isDirectory || file.name.endsWith(".torrent") }

        override var currentDirectory: File by Delegates.observable(model.currentDirectory.value!!) { _, oldValue, newValue ->
            if (newValue != oldValue) {
                model.currentDirectory.value = newValue
            }
        }

        override val hasHeaderItem: Boolean
            get() = (currentDirectory.parentFile != null)

        override fun getItemParentDirectory(item: File): File? = item.parentFile
        override fun getItemName(item: File): String = item.name
        override fun itemIsDirectory(item: File): Boolean = item.isDirectory

        override fun getDirectoryChildren(directory: File): List<File> {
            return directory.listFiles(filesFilter)?.asList() ?: emptyList()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val inflater = LayoutInflater.from(parent.context)
            if (viewType == TYPE_HEADER) {
                return HeaderHolder(inflater.inflate(R.layout.up_list_item,
                                                     parent,
                                                     false))
            }
            return ItemHolder(inflater.inflate(R.layout.file_picker_fragment_list_item,
                                               parent,
                                               false))
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            if (holder.itemViewType == TYPE_ITEM) {
                holder as ItemHolder

                val file = getItem(position)

                holder.file = file
                holder.textView.text = file.name
                holder.iconDrawable.level = if (file.isDirectory) 0 else 1
            }
        }

        fun init() {
            items = getDirectoryChildren(currentDirectory).sortedWith(comparator)
            notifyItemRangeInserted(1, items.size)
            fragment.updatePlaceholder()
        }

        fun navigateToHome() {
            @Suppress("DEPRECATION")
            val homeDirectory = Environment.getExternalStorageDirectory()
            if (currentDirectory != homeDirectory) {
                navigateTo(homeDirectory)
            }
        }

        override fun navigateTo(directory: File, directoryChildren: List<File>) {
            super.navigateTo(directory, directoryChildren)
            fragment.updatePlaceholder()
        }

        private inner class HeaderHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            init {
                itemView.setOnClickListener { navigateUp() }
            }
        }

        private inner class ItemHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            lateinit var file: File
            val textView = itemView as TextView
            val iconDrawable: Drawable = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR1) {
                textView.compoundDrawables.first()
            } else {
                textView.compoundDrawablesRelative.first()
            }

            init {
                itemView.setOnClickListener {
                    if (file.isDirectory) {
                        navigateTo(file)
                    } else {
                        fragment.finish(Uri.fromFile(file))
                    }
                }
            }
        }
    }
}
