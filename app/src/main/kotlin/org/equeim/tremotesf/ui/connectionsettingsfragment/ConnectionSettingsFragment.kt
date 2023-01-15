/*
 * Copyright (C) 2017-2022 Alexey Rochev <equeim@gmail.com>
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

package org.equeim.tremotesf.ui.connectionsettingsfragment

import android.app.Dialog
import android.os.Bundle
import android.view.*
import androidx.appcompat.view.ActionMode
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.findNavController
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import org.equeim.tremotesf.R
import org.equeim.tremotesf.common.AlphanumericComparator
import org.equeim.tremotesf.databinding.ConnectionSettingsFragmentBinding
import org.equeim.tremotesf.databinding.ServerListItemBinding
import org.equeim.tremotesf.rpc.GlobalServers
import org.equeim.tremotesf.torrentfile.rpc.Servers
import org.equeim.tremotesf.ui.NavigationDialogFragment
import org.equeim.tremotesf.ui.NavigationFragment
import org.equeim.tremotesf.ui.SelectionTracker
import org.equeim.tremotesf.ui.utils.*
import kotlin.coroutines.resume


class ConnectionSettingsFragment : NavigationFragment(
    R.layout.connection_settings_fragment,
    R.string.connection_settings
) {
    private val binding by viewLifecycleObject(ConnectionSettingsFragmentBinding::bind)
    val adapter by viewLifecycleObject { ServersAdapter(this) }

    override fun onViewStateRestored(savedInstanceState: Bundle?) {
        super.onViewStateRestored(savedInstanceState)

        with(binding) {
            serversView.adapter = adapter
            serversView.layoutManager = LinearLayoutManager(requireContext())
            serversView.addItemDecoration(
                DividerItemDecoration(
                    requireContext(),
                    DividerItemDecoration.VERTICAL
                )
            )
            (serversView.itemAnimator as DefaultItemAnimator).supportsChangeAnimations = false

            addServerButton.setOnClickListener {
                navigate(ConnectionSettingsFragmentDirections.toServerEditFragment(null))
            }
        }

        GlobalServers.serversState.launchAndCollectWhenStarted(viewLifecycleOwner, ::update)
    }

    fun update(serversState: Servers.ServersState) {
        lifecycleScope.launch {
            adapter.update(serversState)
            binding.placeholder.visibility = if (adapter.itemCount == 0) {
                View.VISIBLE
            } else {
                View.GONE
            }
        }
    }

    class ServersAdapter(fragment: Fragment) :
        StateRestoringListAdapter<ServersAdapter.Item, ServersAdapter.ViewHolder>(DiffCallback()) {
        data class Item(val serverName: String, val current: Boolean)

        private val comparator = object : Comparator<Item> {
            private val nameComparator = AlphanumericComparator()
            override fun compare(o1: Item, o2: Item) = nameComparator.compare(o1.serverName, o2.serverName)
        }

        val selectionTracker = SelectionTracker.createForStringKeys(
            this,
            false,
            fragment,
            ::ActionModeCallback,
            R.plurals.servers_selected
        ) { getItem(it).serverName }

        private var currentServerName: String? = null

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            return ViewHolder(
                this,
                selectionTracker,
                ServerListItemBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
            )
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.update()

        suspend fun update(serversState: Servers.ServersState): Unit = suspendCancellableCoroutine { continuation ->
            val items = serversState.servers
                .map { Item(it.name, it.name == serversState.currentServerName) }
                .sortedWith(comparator)
            submitList(items) {
                currentServerName = serversState.currentServerName
                continuation.resume(Unit)
            }
        }

        override fun allowStateRestoring() = true
        override fun onStateRestored() = selectionTracker.restoreInstanceState()

        class ViewHolder(
            private val adapter: ServersAdapter,
            selectionTracker: SelectionTracker<String>,
            val binding: ServerListItemBinding
        ) : SelectionTracker.ViewHolder<String>(selectionTracker, binding.root) {

            init {
                binding.radioButton.setOnClickListener {
                    bindingAdapterPositionOrNull?.let(adapter::getItem)?.let { item ->
                        GlobalServers.setCurrentServer(item.serverName)
                    }
                }
            }

            override fun update() {
                super.update()
                val item = bindingAdapterPositionOrNull?.let(adapter::getItem) ?: return
                with(binding) {
                    radioButton.isChecked = item.current
                    textView.text = item.serverName
                }
            }

            override fun onClick(view: View) {
                bindingAdapterPositionOrNull?.let(adapter::getItem)?.let { item ->
                    itemView.findNavController()
                        .safeNavigate(
                            ConnectionSettingsFragmentDirections.toServerEditFragment(
                                item.serverName
                            )
                        )
                }
            }
        }

        private class DiffCallback : DiffUtil.ItemCallback<Item>() {
            override fun areItemsTheSame(oldItem: Item, newItem: Item) =
                oldItem.serverName == newItem.serverName
            override fun areContentsTheSame(oldItem: Item, newItem: Item) = oldItem == newItem
        }

        private class ActionModeCallback(selectionTracker: SelectionTracker<String>) :
            SelectionTracker.ActionModeCallback<String>(selectionTracker) {

            override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
                mode.menuInflater.inflate(R.menu.servers_context_menu, menu)
                return true
            }

            override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
                if (super.onActionItemClicked(mode, item)) {
                    return true
                }

                if (selectionTracker?.hasSelection == true && item.itemId == R.id.remove) {
                    activity.navigate(ConnectionSettingsFragmentDirections.toRemoveServerDialog())
                    return true
                }

                return false
            }
        }
    }
}

class RemoveServerDialogFragment : NavigationDialogFragment() {
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val adapter =
            (parentFragmentManager.primaryNavigationFragment as? ConnectionSettingsFragment)?.adapter
        val selectionTracker = adapter?.selectionTracker
        val selectedCount = selectionTracker?.selectedCount ?: 0
        return MaterialAlertDialogBuilder(requireContext())
            .setMessage(
                resources.getQuantityString(
                    R.plurals.remove_servers_message,
                    selectedCount,
                    selectedCount
                )
            )
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.remove) { _, _ ->
                selectionTracker?.apply {
                    GlobalServers.removeServers(
                        adapter.currentList.slice(getSelectedPositionsUnsorted()).mapTo(
                            mutableSetOf(),
                            ConnectionSettingsFragment.ServersAdapter.Item::serverName
                        )
                    )
                    clearSelection()
                }
            }
            .create()
    }
}
