/*
 * Copyright (C) 2017-2018 Alexey Rochev <equeim@gmail.com>
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
import android.content.Context
import android.content.pm.PackageManager

import android.os.Build
import android.os.Bundle

import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager

import android.support.v4.app.Fragment
import android.support.v4.app.FragmentPagerAdapter
import android.support.v4.view.ViewPager

import android.support.v7.app.AppCompatActivity
import android.support.v7.view.ActionMode

import android.support.v7.widget.DividerItemDecoration
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.support.v7.widget.Toolbar

import android.support.design.widget.AppBarLayout
import android.support.design.widget.Snackbar

import org.jetbrains.anko.clearTask
import org.jetbrains.anko.contentView
import org.jetbrains.anko.intentFor
import org.jetbrains.anko.design.indefiniteSnackbar

import org.equeim.libtremotesf.BaseRpc
import org.equeim.libtremotesf.Torrent
import org.equeim.tremotesf.mainactivity.MainActivity
import org.equeim.tremotesf.utils.ArraySpinnerAdapterWithHeader
import org.equeim.tremotesf.utils.Utils

import kotlinx.android.synthetic.main.add_torrent_file_files_fragment.*
import kotlinx.android.synthetic.main.add_torrent_file_info_fragment.*
import kotlinx.android.synthetic.main.add_torrent_file_main_fragment.*
import kotlinx.android.synthetic.main.local_torrent_file_list_item.view.*


class AddTorrentFileActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setTheme(Settings.themeNoActionBar)
        setContentView(R.layout.add_torrent_file_activity)
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

    class MainFragment : Fragment() {
        private val activity: AddTorrentFileActivity
            get() {
                return getActivity() as AddTorrentFileActivity
            }

        private var doneMenuItem: MenuItem? = null
        private var pagerAdapter: PagerAdapter? = null
        private var snackbar: Snackbar? = null

        lateinit var torrentFileParser: TorrentFileParser
            private set

        private var noPermission = false

        private val rpcStatusListener = { _: Int ->
            updateView()
        }

        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            retainInstance = true
            setHasOptionsMenu(true)

            torrentFileParser = TorrentFileParser()

            if (activity.intent.scheme == ContentResolver.SCHEME_FILE &&
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                    activity.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_DENIED) {
                requestPermissions(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), 0)
            } else {
                torrentFileParser.load(activity.intent.data, activity.applicationContext)
            }
        }

        override fun onRequestPermissionsResult(requestCode: Int,
                                                permissions: Array<out String>,
                                                grantResults: IntArray) {
            if (grantResults.first() == PackageManager.PERMISSION_GRANTED) {
                torrentFileParser.load(activity.intent.data, activity.applicationContext)
            } else {
                noPermission = true
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

            Rpc.instance.addStatusListener(rpcStatusListener)

            torrentFileParser.statusListener = { status ->
                updateView()
                if (status == TorrentFileParser.Status.Loaded) {
                    pagerAdapter?.filesFragment?.treeCreated()
                }
            }
        }

        override fun onDestroyView() {
            super.onDestroyView()
            doneMenuItem = null
            pagerAdapter = null
            snackbar = null
            Rpc.instance.removeStatusListener(rpcStatusListener)
            torrentFileParser.statusListener = null
        }

        override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
            inflater.inflate(R.menu.add_torrent_activity_menu, menu)
            doneMenuItem = menu.findItem(R.id.done)
            doneMenuItem!!.isVisible = (torrentFileParser.status == TorrentFileParser.Status.Loaded && Rpc.instance.isConnected)
        }

        override fun onOptionsItemSelected(item: MenuItem): Boolean {
            if (item.itemId != R.id.done) {
                return false
            }
            val infoFragment = pagerAdapter!!.infoFragment!!
            if (infoFragment.check()) {
                val filesData = torrentFileParser.getFilesData()
                Rpc.instance.addTorrentFile(torrentFileParser.fileData,
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
                activity.finish()
                return true
            }
            return false
        }

        private fun updateView() {
            if (view == null) {
                return
            }

            if (Rpc.instance.isConnected && torrentFileParser.status == TorrentFileParser.Status.Loaded) {
                (toolbar!!.layoutParams as AppBarLayout.LayoutParams).scrollFlags =
                        AppBarLayout.LayoutParams.SCROLL_FLAG_SCROLL or
                                AppBarLayout.LayoutParams.SCROLL_FLAG_SNAP or
                                AppBarLayout.LayoutParams.SCROLL_FLAG_ENTER_ALWAYS
                (toolbar as Toolbar).subtitle = torrentFileParser.torrentName
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
                        TorrentFileParser.Status.Loaded -> Rpc.instance.statusString
                        else -> null
                    }
                }

                progress_bar.visibility = if (torrentFileParser.status == TorrentFileParser.Status.Loading ||
                        (Rpc.instance.status() == BaseRpc.Status.Connecting && torrentFileParser.status == TorrentFileParser.Status.Loaded)) {
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

                if (torrentFileParser.status == TorrentFileParser.Status.Loaded) {
                    when (Rpc.instance.status()) {
                        BaseRpc.Status.Disconnected -> {
                            snackbar = indefiniteSnackbar(activity.contentView!!, "", getString(R.string.connect)) {
                                snackbar = null
                                Rpc.instance.connect()
                            }
                        }
                        BaseRpc.Status.Connecting -> {
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

            override fun instantiateItem(container: ViewGroup, position: Int): Any {
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
                    else -> ""
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

        override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
            super.onViewCreated(view, savedInstanceState)

            priority_spinner.adapter = ArraySpinnerAdapterWithHeader(resources.getStringArray(R.array.priority_items),
                                                                     R.string.priority)

            if (savedInstanceState == null) {
                download_directory_edit.setText(Rpc.instance.serverSettings.downloadDirectory())
                priority_spinner.setSelection(1)
                start_downloading_check_box.isChecked = Rpc.instance.serverSettings.startAddedTorrents()
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
            mainFragment = fragmentManager!!.findFragmentById(R.id.add_torrent_activity_main_fragment) as MainFragment
        }

        override fun onCreateView(inflater: LayoutInflater,
                                  container: ViewGroup?,
                                  savedInstanceState: Bundle?): View {
            return inflater.inflate(R.layout.add_torrent_file_files_fragment, container, false)
        }

        override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
            super.onViewCreated(view, savedInstanceState)

            val parser = mainFragment.torrentFileParser

            adapter = Adapter(activity as AppCompatActivity,
                              parser.rootDirectory)

            files_view.adapter = adapter
            files_view.layoutManager = LinearLayoutManager(activity)
            files_view.addItemDecoration(DividerItemDecoration(activity,
                                                               DividerItemDecoration.VERTICAL))
            files_view.itemAnimator = null

            if (parser.status == TorrentFileParser.Status.Loaded) {
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