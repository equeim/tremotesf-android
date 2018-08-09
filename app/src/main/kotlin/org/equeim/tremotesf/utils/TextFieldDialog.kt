package org.equeim.tremotesf.utils

import android.content.Context
import android.content.DialogInterface

import android.text.Editable
import android.text.TextWatcher
import android.view.inputmethod.InputMethodManager

import android.support.v7.app.AlertDialog

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

        (context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showSoftInput(textField,
                                                                  InputMethodManager.SHOW_IMPLICIT)
    }

    return dialog
}