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

package org.equeim.tremotesf.ui.torrentslistfragment

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.Context
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.TextView

import androidx.appcompat.view.ActionMode
import androidx.core.os.bundleOf
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView

import com.google.android.material.dialog.MaterialAlertDialogBuilder

import org.equeim.libtremotesf.TorrentData
import org.equeim.tremotesf.R
import org.equeim.tremotesf.databinding.RemoveTorrentsDialogBinding
import org.equeim.tremotesf.databinding.SetLocationDialogBinding
import org.equeim.tremotesf.databinding.TorrentListItemBinding
import org.equeim.tremotesf.databinding.TorrentListItemCompactBinding
import org.equeim.tremotesf.data.rpc.Rpc
import org.equeim.tremotesf.data.rpc.Torrent
import org.equeim.tremotesf.ui.NavigationDialogFragment
import org.equeim.tremotesf.ui.NavigationFragment
import org.equeim.tremotesf.ui.SelectionTracker
import org.equeim.tremotesf.ui.Settings
import org.equeim.tremotesf.ui.TorrentFileRenameDialogFragment
import org.equeim.tremotesf.ui.addtorrent.AddTorrentDirectoriesAdapter
import org.equeim.tremotesf.ui.torrentpropertiesfragment.TorrentPropertiesFragment
import org.equeim.tremotesf.ui.utils.DecimalFormats
import org.equeim.tremotesf.ui.utils.Utils
import org.equeim.tremotesf.ui.utils.createTextFieldDialog
import org.equeim.tremotesf.ui.utils.safeNavigate


class TorrentsAdapter(private val fragment: TorrentsListFragment) : ListAdapter<Torrent, TorrentsAdapter.BaseTorrentsViewHolder>(Callback()) {
    private val selectionTracker = SelectionTracker.createForIntKeys(this,
                                                                     fragment,
                                                                     ::ActionModeCallback,
                                                                     R.plurals.torrents_selected) {
        getItem(it).id
    }

    private val compactView = Settings.torrentCompactView
    private val multilineName = Settings.torrentNameMultiline

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BaseTorrentsViewHolder {
        if (compactView) {
            return TorrentsViewHolderCompact(multilineName,
                                             TorrentListItemCompactBinding.inflate(LayoutInflater.from(parent.context),
                                                                                   parent,
                                                                                   false))
        }
        return TorrentsViewHolder(multilineName,
                                  TorrentListItemBinding.inflate(LayoutInflater.from(parent.context),
                                                                 parent,
                                                                 false))
    }

    override fun onBindViewHolder(holder: BaseTorrentsViewHolder, position: Int) {
        holder.update()
    }

    override fun submitList(list: List<Torrent>?) {
        super.submitList(if (list?.isEmpty() == true) null else list)
    }

    fun update(torrents: List<Torrent>) {
        submitList(torrents) {
            selectionTracker.restoreInstanceState()
        }
    }

    inner class TorrentsViewHolder(multilineName: Boolean,
                                   private val binding: TorrentListItemBinding) : BaseTorrentsViewHolder(multilineName, binding.root) {
        init {
            Utils.setProgressBarColor(binding.progressBar)
        }

        override fun update() {
            super.update()

            with(binding) {
                sizeTextView.text = if (torrent.isFinished) {
                    context.getString(R.string.uploaded_string,
                                      Utils.formatByteSize(context, torrent.sizeWhenDone),
                                      Utils.formatByteSize(context, torrent.totalUploaded))
                } else {
                    context.getString(R.string.completed_string,
                                      Utils.formatByteSize(context, torrent.completedSize),
                                      Utils.formatByteSize(context, torrent.sizeWhenDone),
                                      DecimalFormats.generic.format(torrent.percentDone * 100))
                }
                etaTextView.text = Utils.formatDuration(context, torrent.eta)

                progressBar.progress = (torrent.percentDone * 100).toInt()
                downloadSpeedTextView.text = context.getString(R.string.download_speed_string,
                                                               Utils.formatByteSpeed(context,
                                                                                     torrent.downloadSpeed))
                uploadSpeedTextView.text = context.getString(R.string.upload_speed_string,
                                                             Utils.formatByteSpeed(context,
                                                                                   torrent.uploadSpeed))

                statusTextView.text = torrent.statusString
            }
        }
    }

    inner class TorrentsViewHolderCompact(multilineName: Boolean,
                                          private val binding: TorrentListItemCompactBinding) : BaseTorrentsViewHolder(multilineName, binding.root) {
        override fun update() {
            super.update()

            downloadSpeedTextView.text = if (torrent.downloadSpeed == 0L) {
                ""
            } else {
                context.getString(R.string.download_speed_string,
                                   Utils.formatByteSpeed(context,
                                                         torrent.downloadSpeed))
            }

            uploadSpeedTextView.text = if (torrent.uploadSpeed == 0L) {
                ""
            } else {
                context.getString(R.string.upload_speed_string,
                                   Utils.formatByteSpeed(context,
                                                         torrent.uploadSpeed))
            }

            binding.progressTextView.text = context.getString(if (torrent.downloadSpeed != 0L || torrent.uploadSpeed != 0L) R.string.progress_string_with_dot else R.string.progress_string,
                                                              DecimalFormats.generic.format(torrent.percentDone * 100))
        }
    }

    open inner class BaseTorrentsViewHolder(multilineName: Boolean,
                                            itemView: View) : SelectionTracker.ViewHolder<Int>(selectionTracker, itemView) {
        protected lateinit var torrent: Torrent
            private set

        protected val context: Context = itemView.context

        private val nameTextView = itemView.findViewById<TextView>(R.id.name_text_view)!!
        private val statusIconDrawable: Drawable = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR1) {
            nameTextView.compoundDrawables.first()
        } else {
            nameTextView.compoundDrawablesRelative.first()
        }
        protected val downloadSpeedTextView = itemView.findViewById<TextView>(R.id.download_speed_text_view)!!
        protected val uploadSpeedTextView = itemView.findViewById<TextView>(R.id.upload_speed_text_view)!!

        init {
            if (!multilineName) {
                nameTextView.ellipsize = TextUtils.TruncateAt.END
                nameTextView.maxLines = 1
                nameTextView.isSingleLine = true
            }
        }

        override fun update() {
            super.update()

            torrent = getItem(adapterPosition)

            nameTextView.text = torrent.name
            statusIconDrawable.level = when (torrent.status) {
                TorrentData.Status.Paused -> 0
                TorrentData.Status.Downloading,
                TorrentData.Status.StalledDownloading,
                TorrentData.Status.QueuedForDownloading -> 1
                TorrentData.Status.Seeding,
                TorrentData.Status.StalledSeeding,
                TorrentData.Status.QueuedForSeeding -> 2
                TorrentData.Status.Checking,
                TorrentData.Status.QueuedForChecking -> 3
                TorrentData.Status.Errored -> 4
                else -> 0
            }
        }

        override fun onClick(view: View) {
            fragment.navigate(R.id.action_torrentsListFragment_to_torrentPropertiesFragment,
                              bundleOf(TorrentPropertiesFragment.HASH to torrent.hashString,
                                       TorrentPropertiesFragment.NAME to torrent.name))
        }
    }

    private inner class ActionModeCallback(selectionTracker: SelectionTracker<Int>) : SelectionTracker.ActionModeCallback<Int>(selectionTracker) {
        private inner class MenuItems(val startItem: MenuItem,
                                      val pauseItem: MenuItem)

        private var menuItems: MenuItems? = null

        override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
            mode.menuInflater.inflate(R.menu.torrents_context_menu, menu)
            menuItems = MenuItems(menu.findItem(R.id.start),
                                  menu.findItem(R.id.pause))
            return true
        }

        override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
            super.onPrepareActionMode(mode, menu)

            if (selectionTracker.selectedCount == 1) {
                val startEnabled = when (getFirstSelectedTorrent().status) {
                    TorrentData.Status.Paused,
                    TorrentData.Status.Errored -> true
                    else -> false
                }
                menuItems?.apply {
                    startItem.isEnabled = startEnabled
                    pauseItem.isEnabled = !startEnabled
                }
            } else {
                menuItems?.apply {
                    startItem.isEnabled = true
                    pauseItem.isEnabled = true
                }
            }

            return true
        }

        override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
            if (super.onActionItemClicked(mode, item)) {
                return true
            }

            val getTorrentIds = {
                selectionTracker.selectedKeys.toIntArray()
            }

            when (item.itemId) {
                R.id.start -> Rpc.nativeInstance.startTorrents(getTorrentIds())
                R.id.pause -> Rpc.nativeInstance.pauseTorrents(getTorrentIds())
                R.id.check -> Rpc.nativeInstance.checkTorrents(getTorrentIds())
                R.id.reannounce -> Rpc.nativeInstance.reannounceTorrents(getTorrentIds())
                R.id.set_location -> {
                    fragment.navigate(R.id.action_torrentsListFragment_to_setLocationDialogFragment,
                                      bundleOf(SetLocationDialogFragment.TORRENT_IDS to getTorrentIds(),
                                               SetLocationDialogFragment.LOCATION to getFirstSelectedTorrent().downloadDirectory))
                }
                R.id.rename -> {
                    val torrent = getFirstSelectedTorrent()
                    fragment.navigate(R.id.action_torrentsListFragment_to_torrentRenameDialogFragment,
                                      bundleOf(TorrentFileRenameDialogFragment.TORRENT_ID to torrent.id,
                                               TorrentFileRenameDialogFragment.FILE_PATH to torrent.name,
                                               TorrentFileRenameDialogFragment.FILE_NAME to torrent.name))
                }
                R.id.remove -> fragment.navigate(R.id.action_torrentsListFragment_to_removeTorrentDialogFragment,
                                                 bundleOf(RemoveTorrentDialogFragment.TORRENT_IDS to getTorrentIds()))
                R.id.share -> {
                    val magnetLinks = currentList.slice(selectionTracker.getSelectedPositionsUnsorted().sorted()).map { it.data.magnetLink }
                    Utils.shareTorrents(magnetLinks, fragment.requireContext())
                }
                else -> return false
            }

            return true
        }

        override fun onDestroyActionMode(mode: ActionMode) {
            menuItems = null
            super.onDestroyActionMode(mode)
        }

        private fun getFirstSelectedTorrent(): Torrent {
            return getItem(selectionTracker.getFirstSelectedPosition())
        }
    }

    class SetLocationDialogFragment : NavigationDialogFragment() {
        companion object {
            const val TORRENT_IDS = "torrentIds"
            const val LOCATION = "location"
        }

        private var directoriesAdapter: AddTorrentDirectoriesAdapter? = null

        override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
            return createTextFieldDialog(requireContext(),
                                         null,
                                         SetLocationDialogBinding::inflate,
                                         R.id.download_directory_edit,
                                         R.id.download_directory_layout,
                                         getString(R.string.location),
                                         InputType.TYPE_TEXT_VARIATION_URI,
                                         requireArguments().getString(LOCATION),
                                         {
                                             it.downloadDirectoryLayout.downloadDirectoryEdit.let { edit ->
                                                 directoriesAdapter = AddTorrentDirectoriesAdapter(edit, savedInstanceState)
                                                 edit.setAdapter(directoriesAdapter)
                                             }
                                         },
                                         {
                                             Rpc.nativeInstance.setTorrentsLocation(requireArguments().getIntArray(TORRENT_IDS),
                                                                                    it.downloadDirectoryLayout.downloadDirectoryEdit.text.toString(),
                                                                                    it.moveFilesCheckBox.isChecked)
                                             directoriesAdapter?.save()
                                         })
        }

        override fun onSaveInstanceState(outState: Bundle) {
            directoriesAdapter?.saveInstanceState(outState)
        }
    }

    class RemoveTorrentDialogFragment : NavigationDialogFragment() {
        companion object {
            const val TORRENT_IDS = "torrentIds"
            const val POP_BACK_STACK = "popBackStack"
        }

        override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
            val ids = requireArguments().getIntArray(TORRENT_IDS)!!

            val builder = MaterialAlertDialogBuilder(requireContext())
            val binding = RemoveTorrentsDialogBinding.inflate(LayoutInflater.from(builder.context))

            binding.deleteFilesCheckBox.isChecked = Settings.deleteFiles

            return builder
                    .setMessage(if (ids.size == 1) getString(R.string.remove_torrent_message)
                                else resources.getQuantityString(R.plurals.remove_torrents_message,
                                                                 ids.size,
                                                                 ids.size))
                    .setView(binding.root)
                    .setNegativeButton(android.R.string.cancel, null)
                    .setPositiveButton(R.string.remove) { _, _ ->
                        Rpc.nativeInstance.removeTorrents(ids, binding.deleteFilesCheckBox.isChecked)
                        activity?.actionMode?.finish()
                        if (requireArguments().getBoolean(POP_BACK_STACK)) {
                            val id = (parentFragmentManager.primaryNavigationFragment as NavigationFragment).destinationId
                            val listener = object : NavController.OnDestinationChangedListener {
                                override fun onDestinationChanged(controller: NavController, destination: NavDestination, arguments: Bundle?) {
                                    if (destination.id == id) {
                                        navController.popBackStack()
                                        controller.removeOnDestinationChangedListener(this)
                                    }
                                }
                            }
                            navController.addOnDestinationChangedListener(listener)
                        }
                    }
                    .create()
        }
    }

    private class Callback : DiffUtil.ItemCallback<Torrent>() {
        override fun areItemsTheSame(oldItem: Torrent, newItem: Torrent): Boolean {
            return oldItem.id == newItem.id
        }

        @SuppressLint("DiffUtilEquals")
        override fun areContentsTheSame(oldItem: Torrent, newItem: Torrent): Boolean {
            return oldItem === newItem
        }
    }
}
