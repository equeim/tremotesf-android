// SPDX-FileCopyrightText: 2017-2025 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package org.equeim.tremotesf.ui.connectionsettingsfragment

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.view.ActionMode
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.navigation.findNavController
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.equeim.tremotesf.R
import org.equeim.tremotesf.common.AlphanumericComparator
import org.equeim.tremotesf.databinding.ConnectionSettingsFragmentBinding
import org.equeim.tremotesf.databinding.ServerListItemBinding
import org.equeim.tremotesf.rpc.GlobalServers
import org.equeim.tremotesf.rpc.Servers
import org.equeim.tremotesf.ui.NavigationDialogFragment
import org.equeim.tremotesf.ui.NavigationFragment
import org.equeim.tremotesf.ui.SelectionTracker
import org.equeim.tremotesf.ui.utils.bindingAdapterPositionOrNull
import org.equeim.tremotesf.ui.utils.launchAndCollectWhenStarted
import org.equeim.tremotesf.ui.utils.nullIfEmpty
import org.equeim.tremotesf.ui.utils.safeNavigate
import org.equeim.tremotesf.ui.utils.submitListAwait
import org.equeim.tremotesf.ui.utils.viewLifecycleObject


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

    suspend fun update(serversState: Servers.ServersState) {
        adapter.update(serversState)
        binding.placeholder.isVisible = adapter.itemCount == 0
    }

    class ServersAdapter(fragment: Fragment) :
        ListAdapter<ServersAdapter.Item, ServersAdapter.ViewHolder>(DiffCallback()) {
        data class Item(val serverName: String, val current: Boolean)

        private val comparator = object : Comparator<Item> {
            private val nameComparator = AlphanumericComparator()
            override fun compare(o1: Item, o2: Item) = nameComparator.compare(o1.serverName, o2.serverName)
        }

        val selectionTracker = SelectionTracker.createForStringKeys(
            this,
            true,
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

        suspend fun update(serversState: Servers.ServersState) {
            val items = serversState.servers
                .map { Item(it.name, it.name == serversState.currentServerName) }
                .sortedWith(comparator)
            currentServerName = serversState.currentServerName
            submitListAwait(items.nullIfEmpty())
            selectionTracker.commitAdapterUpdate()
            selectionTracker.restoreInstanceState()
        }

        class ViewHolder(
            private val adapter: ServersAdapter,
            selectionTracker: SelectionTracker<String>,
            val binding: ServerListItemBinding,
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
                        adapter.currentList.slice(getSelectedPositionsUnsorted().asIterable()).mapTo(
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
