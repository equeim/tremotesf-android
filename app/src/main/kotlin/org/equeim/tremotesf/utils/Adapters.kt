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

package org.equeim.tremotesf.utils

import android.content.Context
import android.util.AttributeSet

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup

import android.widget.ArrayAdapter
import android.widget.BaseAdapter
import android.widget.LinearLayout
import android.widget.TextView

import org.equeim.tremotesf.R


class ArraySpinnerAdapter(context: Context,
                          items: Array<String>)
    : ArrayAdapter<String>(context, android.R.layout.simple_spinner_item, items) {
    init {
        setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
    }
}

class ChildrenDisablingLinearLayout(context: Context, attrs: AttributeSet) : LinearLayout(context,
                                                                                          attrs) {
    override fun setEnabled(enabled: Boolean) {
        super.setEnabled(enabled)
        setChildrenEnabled(enabled)
    }
}

abstract class BaseSpinnerAdapter(private val headerText: Int? = null) : BaseAdapter() {
    override fun getItemId(position: Int): Long {
        return position.toLong()
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view: View
        if (convertView == null) {
            val inflater = LayoutInflater.from(parent.context)
            if (headerText == null) {
                view = inflater.inflate(android.R.layout.simple_spinner_item, parent, false)
            } else {
                view = inflater.inflate(R.layout.spinner_item_with_header, parent, false)
                view.tag = ViewHolder(view)
            }
        } else {
            view = convertView
        }

        if (headerText == null) {
            (view as TextView).text = getItem(position) as String
        } else {
            val holder = view.tag as ViewHolder
            holder.headerTextView.text = parent.context.getString(headerText)
            holder.textView.text = getItem(position) as String
        }

        return view
    }

    override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = if (convertView == null) {
            LayoutInflater.from(parent.context)
                    .inflate(android.R.layout.simple_spinner_dropdown_item,
                             parent,
                             false) as TextView
        } else {
            convertView as TextView
        }
        view.text = getItem(position) as String
        return view
    }

    private class ViewHolder(view: View) {
        val headerTextView = view.findViewById(R.id.header_text_view) as TextView
        val textView = view.findViewById(android.R.id.text1) as TextView
    }
}

class ArraySpinnerAdapterWithHeader(private val items: Array<String>,
                                    headerText: Int) : BaseSpinnerAdapter(headerText) {
    override fun getCount() = items.size
    override fun getItem(position: Int) = items[position]
}