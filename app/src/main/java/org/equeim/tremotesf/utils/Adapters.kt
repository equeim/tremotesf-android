/*
 * Copyright (C) 2017 Alexey Rochev <equeim@gmail.com>
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

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup

import android.widget.ArrayAdapter
import android.widget.BaseAdapter
import android.widget.TextView


class ArraySpinnerAdapter(context: Context, items: Array<String>)
    : ArrayAdapter<String>(context, android.R.layout.simple_spinner_item, items) {

    init {
        setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
    }
}

abstract class BaseSpinnerAdapter : BaseAdapter() {
    override fun getItemId(position: Int): Long {
        return position.toLong()
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        return getView(position, convertView, parent, false)
    }

    override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
        return getView(position, convertView, parent, true)
    }

    private fun getView(position: Int,
                        convertView: View?,
                        parent: ViewGroup,
                        dropdown: Boolean): View {
        val view: View
        val textView: TextView
        if (convertView == null) {
            val layoutId = if (dropdown) {
                android.R.layout.simple_spinner_dropdown_item
            } else {
                android.R.layout.simple_spinner_item
            }
            view = LayoutInflater.from(parent.context).inflate(layoutId, parent, false)
            textView = view.findViewById(android.R.id.text1) as TextView
            view.tag = textView
        } else {
            view = convertView
            textView = view.tag as TextView
        }
        textView.text = getItem(position) as String
        return view
    }
}