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

package org.equeim.tremotesf.connectionsettingsactivity

import java.util.Comparator

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup

import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.view.ActionMode
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

import org.equeim.tremotesf.BaseActivity
import org.equeim.tremotesf.R
import org.equeim.tremotesf.Selector
import org.equeim.tremotesf.Server
import org.equeim.tremotesf.Servers
import org.equeim.tremotesf.StringSelector
import org.equeim.tremotesf.utils.AlphanumericComparator

import org.jetbrains.anko.startActivity

import kotlinx.android.synthetic.main.connection_settings_fragment.*
import kotlinx.android.synthetic.main.server_list_item.view.*


class ConnectionSettingsActivity : BaseActivity(R.layout.connection_settings_activity, true)

class ConnectionSettingsFragment : Fragment(R.layout.connection_settings_fragment) {
    var adapter: ServersAdapter? = null
    private val serversListener = { update() }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val adapter = ServersAdapter(requireActivity() as AppCompatActivity)
        this.adapter = adapter
        servers_view.adapter = adapter
        servers_view.layoutManager = LinearLayoutManager(requireContext())
        servers_view.addItemDecoration(DividerItemDecoration(requireContext(), DividerItemDecoration.VERTICAL))
        (servers_view.itemAnimator as DefaultItemAnimator).supportsChangeAnimations = false

        fab.setOnClickListener {
            requireContext().startActivity<ServerEditActivity>()
        }

        servers_view.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (dy > 0) {
                    fab.hide()
                } else if (dy < 0) {
                    fab.show()
                }
            }
        })

        update()
        adapter.selector.restoreInstanceState(savedInstanceState)

        Servers.addServersListener(serversListener)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        (requireActivity() as AppCompatActivity).apply {
            setSupportActionBar(this@ConnectionSettingsFragment.toolbar as Toolbar)
            supportActionBar?.setDisplayHomeAsUpEnabled(true)
        }
    }

    override fun onDestroyView() {
        Servers.removeServersListener(serversListener)
        super.onDestroyView()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        adapter?.selector?.saveInstanceState(outState)
    }

    fun update() {
        adapter?.update()
        placeholder.visibility = if (adapter?.itemCount == 0) {
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
                              LayoutInflater.from(parent.context).inflate(R.layout.server_list_item,
                                                                          parent,
                                                                          false))
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val server = servers[position]
            holder.item = server
            holder.radioButton.isChecked = (server === Servers.currentServer)
            holder.textView.text = server.name
            holder.updateSelectedBackground()
        }

        fun update() {
            servers.clear()
            servers.addAll(Servers.servers.sortedWith(comparator))
            notifyDataSetChanged()
        }

        class ViewHolder(adapter: ServersAdapter,
                         selector: Selector<Server, String>,
                         itemView: View) : Selector.ViewHolder<Server>(selector, itemView) {
            override lateinit var item: Server

            val radioButton = itemView.radio_button!!
            val textView = itemView.text_view!!

            init {
                radioButton.setOnClickListener {
                    if (item !== Servers.currentServer) {
                        Servers.currentServer = item
                        adapter.notifyItemRangeChanged(0, adapter.itemCount)
                    }
                }
            }

            override fun onClick(view: View) {
                if (selector.actionMode == null) {
                    itemView.context.startActivity<ServerEditActivity>(ServerEditActivity.SERVER to item.name)
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
                    RemoveDialogFragment().show(activity.supportFragmentManager, null)
                    return true
                }

                return false
            }

            class RemoveDialogFragment : DialogFragment() {
                override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
                    val selector = (requireFragmentManager().findFragmentById(R.id.connection_settings_fragment) as ConnectionSettingsFragment).adapter!!.selector
                    val selectedCount = selector.selectedCount
                    return AlertDialog.Builder(requireContext())
                            .setMessage(resources.getQuantityString(R.plurals.remove_servers_message,
                                                                    selectedCount,
                                                                    selectedCount))
                            .setNegativeButton(android.R.string.cancel, null)
                            .setPositiveButton(R.string.remove) { _, _ ->
                                Servers.removeServers(selector.selectedItems)
                                selector.actionMode?.finish()
                            }
                            .create()
                }
            }
        }
    }
}
