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

package org.equeim.tremotesf.data

import android.os.Environment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.equeim.tremotesf.ui.utils.AlphanumericComparator
import java.io.File
import java.io.FileFilter
import java.util.Comparator

class FilesystemNavigator(private val currentDirectory: MutableStateFlow<File>, private val scope: CoroutineScope) {
    private val filesFilter = FileFilter { it.isDirectory || it.name.endsWith(".torrent") }
    private val comparator = object : Comparator<File?> {
        private val nameComparator = AlphanumericComparator()

        override fun compare(item1: File?, item2: File?): Int {
            if (item1 == null) {
                if (item2 == null) {
                    return 0
                }
                return -1
            }
            if (item2 == null) {
                return 1
            }
            if (item1.isDirectory == item2.isDirectory) {
                return nameComparator.compare(item1.name, item2.name)
            }
            if (item1.isDirectory) {
                return -1
            }
            return 1
        }
    }

    private val _items = MutableStateFlow(emptyList<File?>())
    val items: Flow<List<File?>> by ::_items

    fun init() {
        navigateTo(currentDirectory.value, true)
    }

    fun navigateUp(): Boolean {
        val parent = currentDirectory.value.parentFile ?: return false
        navigateTo(parent, false)
        return true
    }

    fun navigateDown(item: File) {
        if (item.isDirectory) {
            navigateTo(item, true)
        }
    }

    fun navigateToHome() {
        @Suppress("DEPRECATION")
        val homeDirectory = Environment.getExternalStorageDirectory()
        if (homeDirectory != currentDirectory.value) {
            navigateTo(homeDirectory, true)
        }
    }

    private fun navigateTo(directory: File, allowNoFiles: Boolean) = scope.launch(Dispatchers.IO) {
        val files = directory.listFiles(filesFilter)?.asList() ?: emptyList()
        if (files.isEmpty() && !allowNoFiles) {
            return@launch
        }

        val items: List<File?> = if (directory.parentFile == null) {
            files.sortedWith(comparator)
        } else {
            ArrayList<File?>(files.size + 1).apply {
                add(null)
                addAll(files)
                sortWith(comparator)
            }
        }
        ensureActive()
        currentDirectory.value = directory
        _items.value = items
    }
}