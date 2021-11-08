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

package org.equeim.tremotesf.ui.connectionsettingsfragment

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.view.ActionMode
import androidx.fragment.app.Fragment
import androidx.navigation.findNavController
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.equeim.tremotesf.R
import org.equeim.tremotesf.torrentfile.rpc.Server
import org.equeim.tremotesf.databinding.ConnectionSettingsFragmentBinding
import org.equeim.tremotesf.databinding.ServerListItemBinding
import org.equeim.tremotesf.rpc.GlobalServers
import org.equeim.tremotesf.ui.NavigationDialogFragment
import org.equeim.tremotesf.ui.NavigationFragment
import org.equeim.tremotesf.ui.SelectionTracker
import org.equeim.tremotesf.common.AlphanumericComparator
import org.equeim.tremotesf.ui.utils.safeNavigate
import org.equeim.tremotesf.ui.utils.viewBinding
import org.equeim.tremotesf.ui.utils.launchAndCollectWhenStarted
import java.util.Comparator


class ConnectionSettingsFragment : NavigationFragment(
    R.layout.connection_settings_fragment,
    R.string.connection_settings
) {
    private val binding by viewBinding(ConnectionSettingsFragmentBinding::bind)
    var adapter: ServersAdapter? = null
        private set

    override fun onViewStateRestored(savedInstanceState: Bundle?) {
        super.onViewStateRestored(savedInstanceState)

        val adapter = ServersAdapter(this)
        this.adapter = adapter

        with(binding) {
            serversView.adapter = adapter
            serversView.layoutManager = LinearLayoutManager(requireContext())
            serversView.addItemDecoration(DividerItemDecoration(requireContext(), DividerItemDecoration.VERTICAL))
            (serversView.itemAnimator as DefaultItemAnimator).supportsChangeAnimations = false

            addServerButton.setOnClickListener {
                navigate(ConnectionSettingsFragmentDirections.toServerEditFragment(null))
            }
        }

        GlobalServers.servers.launchAndCollectWhenStarted(viewLifecycleOwner, ::update)
    }

    fun update(servers: List<Server>) {
        adapter?.update(servers)
        binding.placeholder.visibility = if (adapter?.itemCount == 0) {
            View.VISIBLE
        } else {
            View.GONE
        }
    }

    class ServersAdapter(fragment: Fragment) : RecyclerView.Adapter<ServersAdapter.ViewHolder>() {
        private val _servers = mutableListOf<Server>()
        val servers: List<Server>
            get() = _servers

        private val comparator = object : Comparator<Server> {
            private val nameComparator = AlphanumericComparator()
            override fun compare(o1: Server, o2: Server) = nameComparator.compare(o1.name, o2.name)
        }

        val selectionTracker = SelectionTracker.createForStringKeys(
            this,
            false,
            fragment,
            ::ActionModeCallback,
            R.plurals.servers_selected
        ) { servers[it].name }

        override fun getItemCount(): Int {
            return servers.size
        }

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

        fun update(servers: List<Server>) {
            _servers.clear()
            _servers.addAll(servers.sortedWith(comparator))
            notifyDataSetChanged()
            selectionTracker.restoreInstanceState()
        }

        class ViewHolder(
            private val adapter: ServersAdapter,
            selectionTracker: SelectionTracker<String>,
            val binding: ServerListItemBinding
        ) : SelectionTracker.ViewHolder<String>(selectionTracker, binding.root) {

            init {
                binding.radioButton.setOnClickListener {
                    val server = adapter.servers[bindingAdapterPosition]
                    if (server.name != GlobalServers.currentServer.value?.name) {
                        GlobalServers.setCurrentServer(server)
                        adapter.notifyItemRangeChanged(0, adapter.itemCount)
                    }
                }
            }

            override fun update() {
                super.update()
                val server = adapter.servers[bindingAdapterPosition]
                with(binding) {
                    radioButton.isChecked = (server.name == GlobalServers.currentServer.value?.name)
                    textView.text = server.name
                }
            }

            override fun onClick(view: View) {
                itemView.findNavController()
                    .safeNavigate(ConnectionSettingsFragmentDirections.toServerEditFragment(adapter.servers[bindingAdapterPosition].name))
            }
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
                    GlobalServers.removeServers(adapter.servers.slice(getSelectedPositionsUnsorted()))
                    clearSelection()
                }
            }
            .create()
    }
}
