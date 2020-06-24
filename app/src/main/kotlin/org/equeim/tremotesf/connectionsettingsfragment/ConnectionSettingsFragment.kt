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

package org.equeim.tremotesf.connectionsettingsfragment

import java.util.Comparator

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup

import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.view.ActionMode
import androidx.core.os.bundleOf
import androidx.navigation.findNavController
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

import com.google.android.material.dialog.MaterialAlertDialogBuilder

import org.equeim.tremotesf.NavigationDialogFragment
import org.equeim.tremotesf.NavigationFragment
import org.equeim.tremotesf.R
import org.equeim.tremotesf.Selector
import org.equeim.tremotesf.Server
import org.equeim.tremotesf.Servers
import org.equeim.tremotesf.StringSelector
import org.equeim.tremotesf.databinding.ConnectionSettingsFragmentBinding
import org.equeim.tremotesf.databinding.ServerEditFragmentBinding
import org.equeim.tremotesf.databinding.ServerListItemBinding
import org.equeim.tremotesf.utils.AlphanumericComparator
import org.equeim.tremotesf.utils.safeNavigate
import org.equeim.tremotesf.utils.viewBinding


class ConnectionSettingsFragment : NavigationFragment(R.layout.connection_settings_fragment,
                                                      R.string.connection_settings) {
    private val binding by viewBinding(ConnectionSettingsFragmentBinding::bind)
    var adapter: ServersAdapter? = null

    private var savedInstanceState: Bundle? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        this.savedInstanceState = savedInstanceState
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val adapter = ServersAdapter(requireActivity() as AppCompatActivity)
        this.adapter = adapter

        with (binding) {
            serversView.adapter = adapter
            serversView.layoutManager = LinearLayoutManager(requireContext())
            serversView.addItemDecoration(DividerItemDecoration(requireContext(), DividerItemDecoration.VERTICAL))
            (serversView.itemAnimator as DefaultItemAnimator).supportsChangeAnimations = false

            fab.setOnClickListener {
                navigate(R.id.action_connectionSettingsFragment_to_serverEditFragment)
            }

            serversView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    if (dy > 0) {
                        fab.hide()
                    } else if (dy < 0) {
                        fab.show()
                    }
                }
            })
        }

        Servers.servers.observe(viewLifecycleOwner, ::update)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        adapter?.selector?.saveInstanceState(outState)
    }

    fun update(servers: List<Server>) {
        adapter?.apply {
            update(servers)
            if (savedInstanceState != null) {
                selector.restoreInstanceState(savedInstanceState)
                savedInstanceState = null
            }
        }
        binding.placeholder.visibility = if (adapter?.itemCount == 0) {
            View.VISIBLE
        } else {
            View.GONE
        }
    }

    class ServersAdapter(activity: AppCompatActivity) : RecyclerView.Adapter<ServersAdapter.ViewHolder>() {
        private val servers = mutableListOf<Server>()

        private val comparator = object : Comparator<Server> {
            private val nameComparator = AlphanumericComparator()
            override fun compare(o1: Server, o2: Server) = nameComparator.compare(o1.name, o2.name)
        }

        val selector = StringSelector(activity,
                                      ActionModeCallback(activity),
                                      this,
                                      servers,
                                      Server::name,
                                      R.plurals.servers_selected)

        override fun getItemCount(): Int {
            return servers.size
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            return ViewHolder(this,
                              selector,
                              ServerListItemBinding.inflate(LayoutInflater.from(parent.context),
                                                            parent,
                                                            false))
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val server = servers[position]
            holder.item = server
            with(holder.binding) {
                radioButton.isChecked = (server.name == Servers.currentServer.value?.name)
                textView.text = server.name
            }
            holder.updateSelectedBackground()
        }

        fun update(servers: List<Server>) {
            this.servers.clear()
            this.servers.addAll(servers.sortedWith(comparator))
            notifyDataSetChanged()
        }

        class ViewHolder(adapter: ServersAdapter,
                         selector: Selector<Server, String>,
                         val binding: ServerListItemBinding) : Selector.ViewHolder<Server>(selector, binding.root) {
            override lateinit var item: Server

            init {
                binding.radioButton.setOnClickListener {
                    if (item.name != Servers.currentServer.value?.name) {
                        Servers.currentServer.value = item
                        adapter.notifyItemRangeChanged(0, adapter.itemCount)
                    }
                }
            }

            override fun onClick(view: View) {
                if (selector.actionMode == null) {
                    itemView.findNavController().safeNavigate(R.id.action_connectionSettingsFragment_to_serverEditFragment,
                                                              bundleOf(ServerEditFragment.SERVER to item.name))
                } else {
                    super.onClick(view)
                }
            }
        }

        private class ActionModeCallback(private val activity: AppCompatActivity) : Selector.ActionModeCallback<Server>() {
            override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
                mode.menuInflater.inflate(R.menu.servers_context_menu, menu)
                return true
            }

            override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
                if (super.onActionItemClicked(mode, item)) {
                    return true
                }

                if (selector.hasSelection && item.itemId == R.id.remove) {
                    activity.findNavController(R.id.nav_host).safeNavigate(R.id.action_connectionSettingsFragment_to_removeServerDialogFragment)
                    return true
                }

                return false
            }
        }
    }

    class RemoveServerDialogFragment : NavigationDialogFragment() {
        override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
            val selector = (parentFragmentManager.primaryNavigationFragment as? ConnectionSettingsFragment)?.adapter?.selector
            val selectedCount = selector?.selectedCount ?: 0
            return MaterialAlertDialogBuilder(requireContext())
                    .setMessage(resources.getQuantityString(R.plurals.remove_servers_message,
                                                            selectedCount,
                                                            selectedCount))
                    .setNegativeButton(android.R.string.cancel, null)
                    .setPositiveButton(R.string.remove) { _, _ ->
                        selector?.apply {
                            Servers.removeServers(selectedItems)
                            actionMode?.finish()
                        }
                    }
                    .create()
        }
    }
}
