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

import java.io.EOFException
import java.io.IOException

import android.Manifest
import android.app.Fragment

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

import android.os.AsyncTask
import android.os.Build
import android.os.Bundle

import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager

import android.widget.TextView

import android.support.v4.view.ViewPager

import android.support.v7.app.AppCompatActivity
import android.support.v7.view.ActionMode

import android.support.v7.widget.DividerItemDecoration
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.support.v7.widget.Toolbar

import android.support.v13.app.FragmentPagerAdapter

import android.support.design.widget.AppBarLayout
import android.support.design.widget.Snackbar

import com.hypirion.bencode.BencodeReader
import com.hypirion.bencode.BencodeReadException

import org.equeim.tremotesf.mainactivity.MainActivity
import org.equeim.tremotesf.utils.ArraySpinnerAdapter
import org.equeim.tremotesf.utils.Logger
import org.equeim.tremotesf.utils.Utils

import kotlinx.android.synthetic.main.add_torrent_file_files_fragment.*
import kotlinx.android.synthetic.main.add_torrent_file_info_fragment.*
import kotlinx.android.synthetic.main.add_torrent_file_main_fragment.*


class AddTorrentFileActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setTheme(Settings.themeNoActionBar)
        setContentView(R.layout.add_torrent_file_activity)
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

    class MainFragment : Fragment() {
        enum class Status {
            None,
            PermissionError,
            Loading,
            FileIsTooLarge,
            ReadingError,
            ParsingError,
            Loaded
        }

        private val activity: AddTorrentFileActivity
            get() {
                return getActivity() as AddTorrentFileActivity
            }

        private var doneMenuItem: MenuItem? = null

        var status = Status.None
            private set

        private lateinit var torrentFileData: ByteArray

        val rootDirectory = BaseTorrentFilesAdapter.Directory()
        private val files = mutableListOf<BaseTorrentFilesAdapter.File>()

        private var pagerAdapter: PagerAdapter? = null

        private var snackbar: Snackbar? = null

        private val rpcStatusListener = { status: Rpc.Status ->
            updateView()
        }

        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            retainInstance = true
            setHasOptionsMenu(true)

            if (activity.intent.scheme == ContentResolver.SCHEME_FILE &&
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                    activity.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_DENIED) {
                requestPermissions(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), 0)
            } else {
                readFile()
            }
        }

        override fun onRequestPermissionsResult(requestCode: Int,
                                                permissions: Array<out String>?,
                                                grantResults: IntArray) {
            if (grantResults.first() == PackageManager.PERMISSION_GRANTED) {
                readFile()
            } else {
                status = Status.PermissionError
                updateView()
            }
        }

        override fun onCreateView(inflater: LayoutInflater,
                                  container: ViewGroup?,
                                  savedInstanceState: Bundle?): View {
            return inflater.inflate(R.layout.add_torrent_file_main_fragment,
                                    container,
                                    false)
        }

        override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
            Utils.setPreLollipopContentShadow(view)

            activity.setSupportActionBar(toolbar as Toolbar)
            activity.supportActionBar!!.setDisplayHomeAsUpEnabled(true)

            pagerAdapter = PagerAdapter(this)
            pager.adapter = pagerAdapter
            pager.addOnPageChangeListener(object : ViewPager.SimpleOnPageChangeListener() {
                private var previousPage = -1

                override fun onPageSelected(position: Int) {
                    if (previousPage != -1) {
                        pagerAdapter!!.filesFragment?.adapter?.selector?.actionMode?.finish()
                    }
                    previousPage = position
                }
            })
            tab_layout.setupWithViewPager(pager)

            updateView()

            Rpc.addStatusListener(rpcStatusListener)
        }

        override fun onDestroyView() {
            super.onDestroyView()
            doneMenuItem = null
            pagerAdapter = null
            snackbar = null
            Rpc.removeStatusListener(rpcStatusListener)
        }

        override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
            inflater.inflate(R.menu.add_torrent_activity_menu, menu)
            doneMenuItem = menu.findItem(R.id.done)
            doneMenuItem!!.isVisible = (status == MainFragment.Status.Loaded && Rpc.connected)
        }

        override fun onOptionsItemSelected(item: MenuItem): Boolean {
            if (item.itemId != R.id.done) {
                return false
            }
            val infoFragment = pagerAdapter!!.infoFragment!!
            if (infoFragment.check()) {
                val filesData = getFilesData()
                Rpc.addTorrentFile(torrentFileData,
                                   infoFragment.download_directory_edit.text.toString(),
                                   filesData.wantedFiles,
                                   filesData.unwantedFiles,
                                   filesData.lowPriorityFiles,
                                   filesData.normalPriorityFiles,
                                   filesData.highPriorityFiles,
                                   when (infoFragment.priority_spinner.selectedItemPosition) {
                                       0 -> Torrent.Priority.HIGH
                                       1 -> Torrent.Priority.NORMAL
                                       2 -> Torrent.Priority.LOW
                                       else -> Torrent.Priority.NORMAL
                                   },
                                   infoFragment.start_downloading_check_box.isChecked)

                activity.finish()
                return true
            }
            return false
        }

        private fun updateView() {
            if (view == null) {
                return
            }

            if (Rpc.connected && status == Status.Loaded) {
                (toolbar!!.layoutParams as AppBarLayout.LayoutParams).scrollFlags =
                        AppBarLayout.LayoutParams.SCROLL_FLAG_SCROLL or
                                AppBarLayout.LayoutParams.SCROLL_FLAG_SNAP or
                                AppBarLayout.LayoutParams.SCROLL_FLAG_ENTER_ALWAYS
                (toolbar as Toolbar).subtitle = rootDirectory.children.first().name
                doneMenuItem?.isVisible = true

                tab_layout.visibility = View.VISIBLE
                pager.visibility = View.VISIBLE

                placeholder_layout.visibility = View.GONE
            } else {
                placeholder.text = if (status == Status.Loaded) {
                    if (Rpc.connected) null else Rpc.statusString
                } else {
                    when (status) {
                        Status.PermissionError -> getString(R.string.storage_permission_error)
                        Status.Loading -> getString(R.string.loading)
                        Status.FileIsTooLarge -> getString(R.string.file_is_too_large)
                        Status.ReadingError -> getString(R.string.file_reading_error)
                        Status.ParsingError -> getString(R.string.file_parsing_error)
                        else -> null
                    }
                }

                progress_bar.visibility = if (status == Status.Loading ||
                        (Rpc.status == Rpc.Status.Connecting && status == Status.Loaded)) {
                    View.VISIBLE
                } else {
                    View.GONE
                }

                placeholder_layout.visibility = View.VISIBLE

                (toolbar.layoutParams as AppBarLayout.LayoutParams).scrollFlags = 0
                (toolbar as Toolbar).subtitle = null
                doneMenuItem?.isVisible = false

                if (activity.currentFocus != null) {
                    (activity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                            .hideSoftInputFromWindow(activity.currentFocus.windowToken, 0)
                }

                tab_layout.visibility = View.GONE
                pager.visibility = View.GONE
                pager.currentItem = 0
                placeholder.visibility = View.VISIBLE

                if (status == Status.Loaded) {
                    when (Rpc.status) {
                        Rpc.Status.Disconnected -> {
                            snackbar = Snackbar.make(activity.findViewById(android.R.id.content),
                                                     String(),
                                                     Snackbar.LENGTH_INDEFINITE)
                            snackbar!!.setAction(R.string.connect, {
                                snackbar = null
                                Rpc.connect()
                            })
                            snackbar!!.show()
                        }
                        Rpc.Status.Connecting -> {
                            if (snackbar != null) {
                                snackbar!!.dismiss()
                                snackbar = null
                            }
                        }
                        else -> {
                        }
                    }
                }
            }
        }

        private fun readFile() {
            status = Status.Loading
            updateView()

            object : AsyncTask<Any, Any, Status>() {
                override fun doInBackground(vararg params: Any?): MainFragment.Status {
                    val stream = activity.contentResolver.openInputStream(activity.intent.data)
                    try {
                        val size = stream.available()

                        if (size > 10 * 1024 * 1024) {
                            Logger.e("torrent file is too large")
                            return MainFragment.Status.FileIsTooLarge
                        }

                        torrentFileData = stream.readBytes()

                        try {
                            @Suppress("UNCHECKED_CAST")
                            createTree(BencodeReader(torrentFileData.inputStream())
                                               .readDict()["info"] as Map<String, Any>)
                            return MainFragment.Status.Loaded
                        } catch (error: BencodeReadException) {
                            Logger.e("error parsing torrent file", error)
                            return MainFragment.Status.ParsingError
                        } catch (error: EOFException) {
                            Logger.e("error parsing torrent file", error)
                            return MainFragment.Status.ParsingError
                        }
                    } catch (error: IOException) {
                        Logger.e("error reading torrent file", error)
                        return MainFragment.Status.ReadingError
                    } catch (error: SecurityException) {
                        Logger.e("error reading torrent file", error)
                        return MainFragment.Status.ReadingError
                    } finally {
                        stream.close()
                    }
                }

                override fun onPostExecute(result: MainFragment.Status) {
                    this@MainFragment.status = result
                    updateView()
                    pagerAdapter?.filesFragment?.treeCreated()

                }
            }.execute()
        }

        private fun createTree(torrentInfoMap: Map<String, Any>) {
            if (torrentInfoMap.contains("files")) {
                val torrentDirectory = BaseTorrentFilesAdapter.Directory(0,
                                                                         rootDirectory,
                                                                         torrentInfoMap["name"] as String)
                rootDirectory.children.add(torrentDirectory)

                @Suppress("UNCHECKED_CAST")
                val filesMaps = torrentInfoMap["files"] as List<Map<String, Any>>
                for ((fileIndex, fileMap) in filesMaps.withIndex()) {
                    var directory = torrentDirectory

                    @Suppress("UNCHECKED_CAST")
                    val pathParts = fileMap["path"] as List<String>
                    for ((partIndex, part) in pathParts.withIndex()) {
                        if (partIndex == pathParts.lastIndex) {
                            val file = BaseTorrentFilesAdapter.File(directory.children.size,
                                                                    directory,
                                                                    part,
                                                                    fileIndex)
                            file.size = fileMap["length"] as Long
                            directory.children.add(file)
                            files.add(file)
                        } else {
                            var childDirectory = directory.children.find { item ->
                                item is BaseTorrentFilesAdapter.Directory && item.name == part
                            }
                            if (childDirectory == null) {
                                childDirectory = BaseTorrentFilesAdapter.Directory(directory.children.size,
                                                                                   directory,
                                                                                   part)
                                directory.children.add(childDirectory)
                            }
                            directory = childDirectory as BaseTorrentFilesAdapter.Directory
                        }
                    }
                }
            } else {
                val file = BaseTorrentFilesAdapter.File(0,
                                                        rootDirectory,
                                                        torrentInfoMap["name"] as String,
                                                        0)
                file.size = torrentInfoMap["length"] as Long
                rootDirectory.children.add(file)
                files.add(file)
            }

            rootDirectory.children.first().setWanted(true)
        }

        private class FilesData(val wantedFiles: List<Int>,
                                val unwantedFiles: List<Int>,
                                val lowPriorityFiles: List<Int>,
                                val normalPriorityFiles: List<Int>,
                                val highPriorityFiles: List<Int>)

        private fun getFilesData(): FilesData {
            val wantedFiles = mutableListOf<Int>()
            val unwantedFiles = mutableListOf<Int>()
            val lowPriorityFiles = mutableListOf<Int>()
            val normalPriorityFiles = mutableListOf<Int>()
            val highPriorityFiles = mutableListOf<Int>()

            for (file in files) {
                val id = file.id
                if (file.wantedState == BaseTorrentFilesAdapter.Item.WantedState.Wanted) {
                    wantedFiles.add(id)
                } else {
                    unwantedFiles.add(id)
                }
                when (file.priority) {
                    BaseTorrentFilesAdapter.Item.Priority.Low -> lowPriorityFiles
                    BaseTorrentFilesAdapter.Item.Priority.Normal -> normalPriorityFiles
                    BaseTorrentFilesAdapter.Item.Priority.High -> highPriorityFiles
                    BaseTorrentFilesAdapter.Item.Priority.Mixed -> normalPriorityFiles
                }.add(id)
            }

            return FilesData(wantedFiles,
                             unwantedFiles,
                             lowPriorityFiles,
                             normalPriorityFiles,
                             highPriorityFiles)
        }

        class PagerAdapter(private val mainFragment: Fragment) : FragmentPagerAdapter(
                mainFragment.fragmentManager) {
            var infoFragment: InfoFragment? = null
                private set
            var filesFragment: FilesFragment? = null
                private set

            override fun getCount(): Int {
                return 2
            }

            override fun getItem(position: Int): Fragment {
                if (position == 1) {
                    return FilesFragment()
                }
                return InfoFragment()
            }

            override fun instantiateItem(container: ViewGroup?, position: Int): Any {
                val fragment = super.instantiateItem(container, position)
                if (position == 0) {
                    infoFragment = fragment as InfoFragment
                } else {
                    filesFragment = fragment as FilesFragment
                }
                return fragment
            }

            override fun getPageTitle(position: Int): CharSequence {
                return when (position) {
                    0 -> mainFragment.getString(R.string.information)
                    1 -> mainFragment.getString(R.string.files)
                    else -> String()
                }
            }
        }
    }

    class InfoFragment : Fragment() {
        override fun onCreateView(inflater: LayoutInflater,
                                  container: ViewGroup?,
                                  savedInstanceState: Bundle?): View {
            return inflater.inflate(R.layout.add_torrent_file_info_fragment,
                                    container,
                                    false)
        }

        override fun onViewCreated(view: View?, savedInstanceState: Bundle?) {
            super.onViewCreated(view, savedInstanceState)

            priority_spinner.adapter = ArraySpinnerAdapter(activity,
                                                           resources.getStringArray(R.array.priority))

            if (savedInstanceState == null) {
                download_directory_edit.setText(Rpc.serverSettings.downloadDirectory)
                priority_spinner.setSelection(1)
                start_downloading_check_box.isChecked = Rpc.serverSettings.startAddedTorrents
            }
        }

        fun check(): Boolean {
            if (download_directory_edit.text.trim().isEmpty()) {
                download_directory_edit.error = getString(R.string.empty_field_error)
                return false
            }
            return true
        }
    }

    class FilesFragment : Fragment() {
        private lateinit var mainFragment: MainFragment

        private var instanceState: Bundle? = null

        var adapter: Adapter? = null
            private set

        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            retainInstance = true
            mainFragment = fragmentManager.findFragmentById(R.id.add_torrent_activity_main_fragment) as MainFragment
        }

        override fun onCreateView(inflater: LayoutInflater,
                                  container: ViewGroup,
                                  savedInstanceState: Bundle?): View {
            return inflater.inflate(R.layout.add_torrent_file_files_fragment, container, false)
        }

        override fun onViewCreated(view: View?, savedInstanceState: Bundle?) {
            super.onViewCreated(view, savedInstanceState)

            adapter = Adapter(activity as AppCompatActivity,
                              mainFragment.rootDirectory)

            files_view.adapter = adapter
            files_view.layoutManager = LinearLayoutManager(activity)
            files_view.addItemDecoration(DividerItemDecoration(activity,
                                                               DividerItemDecoration.VERTICAL))
            files_view.itemAnimator = null

            if (mainFragment.status == MainFragment.Status.Loaded) {
                adapter!!.restoreInstanceState(if (instanceState == null) savedInstanceState else instanceState)
            }
        }

        override fun onDestroyView() {
            super.onDestroyView()
            adapter = null
        }

        override fun onSaveInstanceState(outState: Bundle) {
            instanceState = outState
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
                                            viewType: Int): RecyclerView.ViewHolder? {
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
                val sizeTextView = itemView.findViewById(R.id.size_text_view) as TextView
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