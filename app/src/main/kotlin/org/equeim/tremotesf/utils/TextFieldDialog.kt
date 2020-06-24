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
import android.view.LayoutInflater
import android.widget.EditText

import androidx.annotation.IdRes
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.core.text.trimmedLength
import androidx.core.widget.doAfterTextChanged
import androidx.viewbinding.ViewBinding

import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputLayout

import org.equeim.tremotesf.R
import org.equeim.tremotesf.databinding.TextFieldDialogBinding


fun createTextFieldDialog(context: Context,
                          @StringRes title: Int?,
                          hint: String,
                          inputType: Int,
                          defaultText: String?,
                          onInflatedView: ((TextFieldDialogBinding) -> Unit)?,
                          onAccepted: ((TextFieldDialogBinding) -> Unit)?): AlertDialog {
    return createTextFieldDialog(context,
                                 title,
                                 TextFieldDialogBinding::inflate,
                                 R.id.text_field,
                                 R.id.text_field_layout,
                                 hint,
                                 inputType,
                                 defaultText,
                                 onInflatedView,
                                 onAccepted)
}


inline fun <reified Binding : ViewBinding> createTextFieldDialog(context: Context,
                                                                 @StringRes title: Int?,
                                                                 crossinline viewBindingFactory: ((LayoutInflater) -> Binding),
                                                                 @IdRes textFieldId: Int,
                                                                 @IdRes textFieldLayoutId: Int,
                                                                 hint: String,
                                                                 inputType: Int,
                                                                 defaultText: String?,
                                                                 noinline onInflatedView: ((Binding) -> Unit)?,
                                                                 noinline onAccepted: ((Binding) -> Unit)?): AlertDialog {
    val builder = MaterialAlertDialogBuilder(context)

    val binding = viewBindingFactory.invoke(LayoutInflater.from(builder.context))
    val view = binding.root

    view.findViewById<TextInputLayout>(textFieldLayoutId)?.hint = hint

    val textField = view.findViewById<EditText>(textFieldId)!!
    textField.inputType = textField.inputType or inputType
    textField.setText(defaultText)

    onInflatedView?.invoke(binding)

    builder.setView(view)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok) { _, _ -> onAccepted?.invoke(binding) }

    if (title != null) {
        builder.setTitle(title)
    }

    val dialog = builder.create()

    dialog.setOnShowListener {
        val okButton = dialog.getButton(DialogInterface.BUTTON_POSITIVE)!!
        okButton.isEnabled = defaultText?.trimmedLength() != 0

        textField.doAfterTextChanged {
            okButton.isEnabled = it?.trimmedLength() != 0
        }

        textField.showKeyboard()
    }

    return dialog
}