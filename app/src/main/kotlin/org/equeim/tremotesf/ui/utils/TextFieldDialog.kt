// SPDX-FileCopyrightText: 2017-2022 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package org.equeim.tremotesf.ui.utils

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


fun createTextFieldDialog(
    context: Context,
    @StringRes title: Int?,
    hint: String,
    inputType: Int,
    defaultText: String?,
    onInflatedView: ((TextFieldDialogBinding) -> Unit)?,
    onAccepted: ((TextFieldDialogBinding) -> Unit)?
): AlertDialog {
    return createTextFieldDialog(
        context,
        title,
        TextFieldDialogBinding::inflate,
        R.id.text_field,
        R.id.text_field_layout,
        hint,
        inputType,
        defaultText,
        onInflatedView,
        onAccepted
    )
}


fun <Binding : ViewBinding> createTextFieldDialog(
    context: Context,
    @StringRes title: Int?,
    viewBindingFactory: ((LayoutInflater) -> Binding),
    @IdRes textFieldId: Int,
    @IdRes textFieldLayoutId: Int,
    hint: String,
    inputType: Int,
    defaultText: String?,
    onInflatedView: ((Binding) -> Unit)?,
    onAccepted: ((Binding) -> Unit)?
): AlertDialog {
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