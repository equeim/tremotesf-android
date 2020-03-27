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

package org.equeim.tremotesf.utils

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup

import android.widget.AutoCompleteTextView
import android.widget.BaseAdapter
import android.widget.Filter
import android.widget.Filterable
import android.widget.TextView

import androidx.annotation.IdRes
import androidx.annotation.LayoutRes

import org.equeim.tremotesf.R


abstract class BaseDropdownAdapter(@LayoutRes private val resource: Int = R.layout.dropdown_menu_popup_item,
                                   @IdRes private val textViewResourceId: Int = 0) : BaseAdapter(), Filterable {
    protected open fun createViewHolder(view: View): BaseViewHolder = BaseViewHolder(view)

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view: View
        val holder: BaseViewHolder
        if (convertView == null) {
            view = LayoutInflater.from(parent.context).inflate(resource,
                                                               parent,
                                                               false)
            holder = createViewHolder(view)
            view.tag = holder
        } else {
            view = convertView
            holder = view.tag as BaseViewHolder
        }
        holder.textView.text = getItem(position) as CharSequence
        return view
    }

    override fun getItemId(position: Int) = position.toLong()

    override fun getFilter(): Filter {
        return object : Filter() {
            override fun performFiltering(constraint: CharSequence?) = FilterResults()
            override fun publishResults(constraint: CharSequence?, results: FilterResults?) {}
        }
    }

    protected open inner class BaseViewHolder(view: View) {
        val textView = if (textViewResourceId == 0) {
            view
        } else {
            view.findViewById(textViewResourceId)
        } as TextView
    }
}

class ArrayDropdownAdapter(private val objects: List<String>) : BaseDropdownAdapter() {
    constructor(objects: Array<String>) : this(objects.asList())

    override fun getCount() = objects.size
    override fun getItem(position: Int) = objects[position]
}

abstract class AutoCompleteTextViewDynamicAdapter(private val textView: AutoCompleteTextView) : BaseDropdownAdapter() {
    protected abstract fun getCurrentItem(): CharSequence

    override fun notifyDataSetChanged() {
        if (textView.isPopupShowing) {
            super.notifyDataSetChanged()
        }
        textView.setText(getCurrentItem())
    }
}
