/*
 * Copyright (C) 2017 Alexey Rochev <equeim@gmail.com>
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

import android.content.Context
import android.content.Intent
import android.os.Bundle

import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.InputMethodManager

import android.support.design.widget.Snackbar

import org.equeim.tremotesf.mainactivity.MainActivity
import org.equeim.tremotesf.utils.ArraySpinnerAdapterWithHeader

import kotlinx.android.synthetic.main.add_torrent_link_activity.*


class AddTorrentLinkActivity : BaseActivity() {
    private lateinit var inputManager: InputMethodManager

    private var doneMenuItem: MenuItem? = null
    private var snackbar: Snackbar? = null

    private var rpcStatusListener = { _: Rpc.Status ->
        updateView()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setTheme(Settings.theme)
        setContentView(R.layout.add_torrent_link_activity)

        inputManager = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager

        priority_spinner.adapter = ArraySpinnerAdapterWithHeader(resources.getStringArray(R.array.priority_items),
                                                                 R.string.priority)

        if (savedInstanceState == null) {
            torrent_link_edit.setText(intent.dataString)
            priority_spinner.setSelection(1)
        }

        updateView(savedInstanceState)
        Rpc.addStatusListener(rpcStatusListener)
    }

    override fun onDestroy() {
        super.onDestroy()
        Rpc.removeStatusListener(rpcStatusListener)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.add_torrent_activity_menu, menu)
        doneMenuItem = menu.findItem(R.id.done)
        doneMenuItem!!.isVisible = Rpc.connected
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.done) {
            if (torrent_link_edit.text.trim().isEmpty()) {
                torrent_link_edit.error = getString(R.string.empty_field_error)
            }

            if (download_directory_edit.text.trim().isEmpty()) {
                download_directory_edit.error = getString(R.string.empty_field_error)
            }

            if (torrent_link_edit.error != null || download_directory_edit.error != null) {
                return false
            }

            Rpc.addTorrentLink(torrent_link_edit.text.toString(),
                               download_directory_edit.text.toString(),
                               when (priority_spinner.selectedItemPosition) {
                                   0 -> Torrent.Priority.HIGH
                                   1 -> Torrent.Priority.NORMAL
                                   2 -> Torrent.Priority.LOW
                                   else -> Torrent.Priority.NORMAL
                               },
                               start_downloading_check_box.isChecked)

            finish()

            return true
        }

        return false
    }

    override fun onSupportNavigateUp(): Boolean {
        val intent = Intent(this, MainActivity::class.java)
        if (isTaskRoot) {
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        startActivity(intent)
        finish()
        return true
    }

    override fun onBackPressed() {
        if (isTaskRoot) {
            val intent = Intent(this, MainActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
            startActivity(intent)
        } else {
            super.onBackPressed()
        }
    }

    private fun updateView(savedInstanceState: Bundle? = null) {
        doneMenuItem?.isVisible = Rpc.connected

        when (Rpc.status) {
            Rpc.Status.Disconnected -> {
                snackbar = Snackbar.make(findViewById(android.R.id.content),
                                         "",
                                         Snackbar.LENGTH_INDEFINITE)
                snackbar!!.setAction(R.string.connect, {
                    snackbar = null
                    Rpc.connect()
                })
                snackbar!!.show()
                placeholder.text = Rpc.statusString

                if (currentFocus != null) {
                    inputManager.hideSoftInputFromWindow(currentFocus.windowToken, 0)
                }
            }
            Rpc.Status.Connecting -> {
                if (snackbar != null) {
                    snackbar!!.dismiss()
                    snackbar = null
                }
                placeholder.text = getString(R.string.connecting)
            }
            else -> {
                if (savedInstanceState == null) {
                    download_directory_edit.setText(Rpc.serverSettings.downloadDirectory)
                    start_downloading_check_box.isChecked = Rpc.serverSettings.startAddedTorrents
                }
            }
        }

        if (Rpc.connected) {
            scroll_view.visibility = View.VISIBLE
            placeholder_layout.visibility = View.GONE
        } else {
            placeholder_layout.visibility = View.VISIBLE
            scroll_view.visibility = View.GONE
        }

        progress_bar.visibility = if (Rpc.status == Rpc.Status.Connecting) {
            View.VISIBLE
        } else {
            View.GONE
        }
    }
}