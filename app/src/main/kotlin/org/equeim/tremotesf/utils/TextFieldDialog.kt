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

import android.content.Context
import android.content.DialogInterface
import android.widget.EditText

import androidx.appcompat.app.AlertDialog
import androidx.core.text.trimmedLength
import androidx.core.widget.doAfterTextChanged

import com.google.android.material.dialog.MaterialAlertDialogBuilder

import org.equeim.tremotesf.R

import kotlinx.android.synthetic.main.text_field_dialog.*


fun createTextFieldDialog(context: Context,
                          title: Int?,
                          layout: Int?,
                          textFieldId: Int?,
                          hint: String,
                          inputType: Int,
                          defaultText: String?,
                          onShow: (() -> Unit)?,
                          onAccepted: (() -> Unit)?): AlertDialog {
    val builder = MaterialAlertDialogBuilder(context)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok) { _, _ -> onAccepted?.invoke() }
            .setView(layout ?: R.layout.text_field_dialog)

    if (title != null) {
        builder.setTitle(title)
    }

    val dialog = builder.create()

    dialog.setOnShowListener {
        dialog.text_field_layout?.hint = hint

        val textField = if (textFieldId == null) {
            dialog.text_field!!
        } else {
            dialog.findViewById<EditText>(textFieldId)!!
        }
        textField.inputType = textField.inputType or inputType
        textField.setText(defaultText)

        val okButton = dialog.getButton(DialogInterface.BUTTON_POSITIVE)!!
        okButton.isEnabled = defaultText?.trimmedLength() != 0

        textField.doAfterTextChanged {
            okButton.isEnabled = it?.trimmedLength() != 0
        }

        textField.showKeyboard()

        onShow?.invoke()
    }

    return dialog
}