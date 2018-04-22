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

package org.equeim.tremotesf.serversactivity

import java.text.Collator
import java.util.Comparator

import android.app.Dialog

import android.content.Intent
import android.os.Bundle
import android.support.v4.app.DialogFragment

import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup

import android.widget.RadioButton
import android.widget.TextView

import android.support.v7.app.AlertDialog
import android.support.v7.app.AppCompatActivity
import android.support.v7.view.ActionMode

import android.support.v7.widget.DefaultItemAnimator
import android.support.v7.widget.DividerItemDecoration
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.support.v7.widget.Toolbar

import com.amjjd.alphanum.AlphanumericComparator

import org.equeim.tremotesf.BaseActivity
import org.equeim.tremotesf.R
import org.equeim.tremotesf.Selector
import org.equeim.tremotesf.Server
import org.equeim.tremotesf.Servers
import org.equeim.tremotesf.Settings

import kotlinx.android.synthetic.main.servers_activity.*


class ServersActivity : BaseActivity() {
    lateinit var adapter: ServersAdapter
    private val serversListener = { update() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setTheme(Settings.themeNoActionBar)
        setContentView(R.layout.servers_activity)
        setPreLollipopShadow()

        setSupportActionBar(toolbar as Toolbar)
        supportActionBar!!.setDisplayHomeAsUpEnabled(true)

        adapter = ServersAdapter(this)
        servers_view.adapter = adapter
        servers_view.layoutManager = LinearLayoutManager(this)
        servers_view.addItemDecoration(DividerItemDecoration(this, DividerItemDecoration.VERTICAL))
        (servers_view.itemAnimator as DefaultItemAnimator).supportsChangeAnimations = false

        fab.setOnClickListener {
            startActivity(Intent(this, ServerEditActivity::class.java))
        }

        servers_view.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView?, dx: Int, dy: Int) {
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

    override fun onDestroy() {
        super.onDestroy()
        Servers.removeServersListener(serversListener)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        adapter.selector.saveInstanceState(outState)
    }

    fun update() {
        adapter.update()
        placeholder.visibility = if (adapter.itemCount == 0) {
            View.VISIBLE
        } else {
            View.GONE
        }
    }
}

class ServersAdapter(activity: ServersActivity) : RecyclerView.Adapter<ServersAdapter.ViewHolder>() {
    private val servers = mutableListOf<Server>()

    private val comparator = object : Comparator<Server> {
        private val nameComparator = AlphanumericComparator(Collator.getInstance())
        override fun compare(o1: Server, o2: Server) = nameComparator.compare(o1.name, o2.name)
    }

    val selector = Selector(activity,
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

        val radioButton = itemView.findViewById(R.id.radio_button) as RadioButton
        val textView = itemView.findViewById(R.id.text_view) as TextView

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
                val intent = Intent(itemView.context, ServerEditActivity::class.java)
                intent.putExtra(ServerEditActivity.SERVER, item.name)
                itemView.context.startActivity(intent)
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
                val selectedCount = (activity as ServersActivity).adapter.selector.selectedCount
                return AlertDialog.Builder(context!!)
                        .setMessage(resources.getQuantityString(R.plurals.remove_servers_message,
                                                                selectedCount,
                                                                selectedCount))
                        .setNegativeButton(android.R.string.cancel, null)
                        .setPositiveButton(R.string.remove, { _, _ ->
                            val adapter = (activity as ServersActivity).adapter
                            Servers.removeServers(adapter.selector.selectedItems)
                            adapter.selector.actionMode?.finish()
                        }).create()
            }
        }
    }
}