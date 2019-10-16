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

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.text.Editable
import android.text.TextWatcher

import androidx.core.content.getSystemService
import androidx.core.text.trimmedLength

import com.google.android.material.snackbar.Snackbar

import org.jetbrains.anko.clearTask
import org.jetbrains.anko.contentView
import org.jetbrains.anko.intentFor
import org.jetbrains.anko.design.indefiniteSnackbar

import org.equeim.libtremotesf.Torrent
import org.equeim.tremotesf.mainactivity.MainActivity
import org.equeim.tremotesf.utils.ArraySpinnerAdapterWithHeader
import org.equeim.tremotesf.utils.Utils

import kotlinx.android.synthetic.main.add_torrent_link_activity.*
import kotlinx.android.synthetic.main.download_directory_edit.*


class AddTorrentLinkActivity : BaseActivity(R.layout.add_torrent_link_activity, false) {
    private lateinit var inputManager: InputMethodManager

    private var doneMenuItem: MenuItem? = null
    private var snackbar: Snackbar? = null

    private lateinit var directoriesAdapter: AddTorrentDirectoriesAdapter

    private var rpcStatusListener: (Int) -> Unit = {
        updateView()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        inputManager = getSystemService()!!

        priority_spinner.adapter = ArraySpinnerAdapterWithHeader(resources.getStringArray(R.array.priority_items),
                                                                 R.string.priority)

        if (savedInstanceState == null) {
            torrent_link_edit.setText(intent.dataString)
            priority_spinner.setSelection(1)
        }

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

        updateView(savedInstanceState)

        Rpc.addStatusListener(rpcStatusListener)

        Rpc.gotDownloadDirFreeSpaceListener = {
            if (Rpc.serverSettings.downloadDirectory()!!.contentEquals(download_directory_edit.text!!.trim())) {
                free_space_text_view.text = getString(R.string.free_space, Utils.formatByteSize(this, it))
                free_space_text_view.visibility = View.VISIBLE
            }
        }

        Rpc.gotFreeSpaceForPathListener = { path, success, bytes ->
            if (path.contentEquals(download_directory_edit.text!!.trim())) {
                if (success) {
                    free_space_text_view.text = getString(R.string.free_space, Utils.formatByteSize(this, bytes))
                } else {
                    free_space_text_view.text = getString(R.string.free_space_error)
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Rpc.removeStatusListener(rpcStatusListener)
        Rpc.gotDownloadDirFreeSpaceListener = null
        Rpc.gotFreeSpaceForPathListener = null
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.add_torrent_activity_menu, menu)
        doneMenuItem = menu.findItem(R.id.done)
        doneMenuItem!!.isVisible = Rpc.isConnected
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.done) {
            if (torrent_link_edit.text!!.trimmedLength() == 0) {
                torrent_link_edit.error = getString(R.string.empty_field_error)
            }

            if (download_directory_edit.text!!.trimmedLength() == 0) {
                download_directory_edit.error = getString(R.string.empty_field_error)
            }

            if (torrent_link_edit.error != null || download_directory_edit.error != null) {
                return false
            }

            Rpc.nativeInstance.addTorrentLink(torrent_link_edit.text.toString(),
                               download_directory_edit.text.toString(),
                               when (priority_spinner.selectedItemPosition) {
                                   0 -> Torrent.Priority.HighPriority
                                   1 -> Torrent.Priority.NormalPriority
                                   2 -> Torrent.Priority.LowPriority
                                   else -> Torrent.Priority.NormalPriority
                               },
                               start_downloading_check_box.isChecked)

            directoriesAdapter.save()

            finish()

            return true
        }

        return false
    }

    override fun onSupportNavigateUp(): Boolean {
        val intent = intentFor<MainActivity>()
        if (isTaskRoot) {
            intent.clearTask()
        }
        startActivity(intent)
        finish()
        return true
    }

    override fun onBackPressed() {
        if (isTaskRoot) {
            startActivity(intentFor<MainActivity>().clearTask())
        } else {
            super.onBackPressed()
        }
    }

    private fun updateView(savedInstanceState: Bundle? = null) {
        doneMenuItem?.isVisible = Rpc.isConnected

        when (Rpc.status) {
            RpcStatus.Disconnected -> {
                snackbar = contentView?.indefiniteSnackbar("", getString(R.string.connect)) {
                    snackbar = null
                    Rpc.nativeInstance.connect()
                }
                placeholder.text = Rpc.statusString

                currentFocus?.let { focus ->
                    inputManager.hideSoftInputFromWindow(focus.windowToken, 0)
                }
            }
            RpcStatus.Connecting -> {
                if (snackbar != null) {
                    snackbar!!.dismiss()
                    snackbar = null
                }
                placeholder.text = getString(R.string.connecting)
            }
            else -> {
                if (savedInstanceState == null) {
                    download_directory_edit.setText(Rpc.serverSettings.downloadDirectory())
                    start_downloading_check_box.isChecked = Rpc.serverSettings.startAddedTorrents()
                }
            }
        }

        if (Rpc.isConnected) {
            scroll_view.visibility = View.VISIBLE
            placeholder_layout.visibility = View.GONE
        } else {
            placeholder_layout.visibility = View.VISIBLE
            scroll_view.visibility = View.GONE
        }

        progress_bar.visibility = if (Rpc.status == RpcStatus.Connecting) {
            View.VISIBLE
        } else {
            View.GONE
        }
    }
}