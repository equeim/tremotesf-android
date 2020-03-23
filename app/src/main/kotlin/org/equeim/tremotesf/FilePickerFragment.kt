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

import java.io.File
import java.util.Comparator

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.net.Uri

import android.os.Build
import android.os.Bundle
import android.os.Environment

import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.TextView

import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

import org.equeim.tremotesf.utils.AlphanumericComparator

import kotlinx.android.synthetic.main.file_picker_fragment.*


class FilePickerFragment : NavigationFragment(R.layout.file_picker_fragment,
                                              R.string.select_file,
                                              R.menu.file_picker_activity_menu) {
    private var adapter: FilePickerAdapter? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val adapter = FilePickerAdapter(this)
        this.adapter = adapter

        files_view.adapter = adapter
        files_view.layoutManager = LinearLayoutManager(requireContext())
        files_view.addItemDecoration(DividerItemDecoration(requireContext(), DividerItemDecoration.VERTICAL))
        files_view.itemAnimator = null

        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_DENIED) {
            files_view.visibility = View.GONE
            requestPermissions(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), 0)
        } else {
            adapter.init()
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
        if (grantResults.first() == PackageManager.PERMISSION_GRANTED) {
            files_view.visibility = View.VISIBLE
            adapter?.init()
        } else {
            placeholder.text = getString(R.string.storage_permission_error)
        }
    }

    fun finish(fileUri: Uri) {
        navigate(R.id.action_filePickerFragment_to_addTorrentFileFragment, bundleOf(AddTorrentFragment.URI to fileUri.toString()))
    }

    fun updatePlaceholder() {
        placeholder.text = if (adapter?.files?.isEmpty() == true) {
            getString(R.string.no_files)
        } else {
            null
        }
    }

    private class FilePickerAdapter(private val fragment: FilePickerFragment) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        companion object {
            private const val TYPE_HEADER = 0
            private const val TYPE_ITEM = 1
        }

        @Suppress("DEPRECATION")
        private var currentDirectory = Environment.getExternalStorageDirectory()
        val files = mutableListOf<File>()

        private val filesFilter = { file: File -> file.isDirectory || file.name.endsWith(".torrent") }
        private val comparator = object : Comparator<File> {
            private val nameComparator = AlphanumericComparator()

            override fun compare(file1: File,
                                 file2: File): Int {
                if (file1.isDirectory == file2.isDirectory) {
                    return nameComparator.compare(file1.name, file2.name)
                }

                if (file1.isDirectory) {
                    return -1
                }

                return 1
            }
        }

        private val hasHeaderItem: Boolean
            get() {
                return (currentDirectory.parentFile != null)
            }

        override fun getItemCount(): Int {
            if (hasHeaderItem) {
                return files.size + 1
            }
            return files.size
        }

        override fun getItemViewType(position: Int): Int {
            if (hasHeaderItem && position == 0) {
                return TYPE_HEADER
            }
            return TYPE_ITEM
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            if (viewType == TYPE_HEADER) {
                return HeaderHolder(fragment.layoutInflater.inflate(R.layout.up_list_item,
                                                                    parent,
                                                                    false))
            }
            return ItemHolder(fragment.layoutInflater.inflate(R.layout.file_picker_fragment_list_item,
                                                              parent,
                                                              false))
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            if (holder.itemViewType == TYPE_ITEM) {
                holder as ItemHolder

                val file = if (hasHeaderItem) {
                    files[position - 1]
                } else {
                    files[position]
                }

                holder.file = file
                holder.textView.text = file.name
                holder.iconDrawable.level = if (file.isDirectory) 0 else 1
            }
        }

        fun init() {
            currentDirectory.listFiles(filesFilter)?.let { it ->
                files.addAll(it.sortedWith(comparator))
            }
            notifyItemRangeInserted(1, files.size)
        }

        fun navigateToHome() {
            @Suppress("DEPRECATION")
            val homeDirectory = Environment.getExternalStorageDirectory()
            if (currentDirectory != homeDirectory) {
                navigateTo(homeDirectory)
            }
        }

        private fun navigateUp() {
            val parentDirectory = currentDirectory.parentFile
            if (parentDirectory != null) {
                navigateTo(parentDirectory)
            }
        }

        private fun navigateTo(directory: File) {
            val hadHeaderItem = hasHeaderItem
            currentDirectory = directory
            val count = files.size
            files.clear()
            if (hadHeaderItem) {
                if (hasHeaderItem) {
                    notifyItemRangeRemoved(1, count)
                } else {
                    notifyItemRangeRemoved(0, count + 1)
                }
            } else {
                notifyItemRangeRemoved(0, count)
            }

            val newFiles = currentDirectory.listFiles(filesFilter)
            if (newFiles != null) {
                files.addAll(newFiles.sortedWith(comparator))
                if (hasHeaderItem) {
                    notifyItemRangeInserted(1, files.size)
                } else {
                    notifyItemRangeInserted(0, files.size)
                }
            }

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
