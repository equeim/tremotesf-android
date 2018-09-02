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
import android.content.DialogInterface
import android.text.Editable
import android.text.TextWatcher
import android.view.inputmethod.InputMethodManager

import androidx.appcompat.app.AlertDialog
import androidx.core.content.getSystemService

import org.equeim.tremotesf.R

import kotlinx.android.synthetic.main.text_field_dialog.*


fun createTextFieldDialog(context: Context,
                          title: Int?,
                          layout: Int?,
                          hint: String,
                          inputType: Int,
                          defaultText: String?,
                          onAccepted: (() -> Unit)?): AlertDialog {
    val builder = AlertDialog.Builder(context)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok) { _, _ -> onAccepted?.invoke() }
            .setView(layout ?: R.layout.text_field_dialog)

    if (title != null) {
        builder.setTitle(title)
    }

    val dialog = builder.create()

    dialog.setOnShowListener {
        val textFieldLayout = dialog.text_field_layout!!
        textFieldLayout.hint = hint

        val textField = dialog.text_field!!
        textField.inputType = inputType
        textField.setText(defaultText)

        val okButton = dialog.getButton(DialogInterface.BUTTON_POSITIVE)!!
        okButton.isEnabled = textField.text!!.isNotEmpty()

        textField.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable) {
                okButton.isEnabled = s.isNotEmpty()
            }

            override fun beforeTextChanged(s: CharSequence?,
                                           start: Int,
                                           count: Int,
                                           after: Int) {
            }

            override fun onTextChanged(s: CharSequence?,
                                       start: Int,
                                       before: Int,
                                       count: Int) {
            }
        })

        context.getSystemService<InputMethodManager>()!!.showSoftInput(textField,
                                                                       InputMethodManager.SHOW_IMPLICIT)
    }

    return dialog
}