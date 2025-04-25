// SPDX-FileCopyrightText: 2017-2025 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package org.equeim.tremotesf.ui.torrentslistfragment

import android.app.Dialog
import android.graphics.Typeface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.divider.MaterialDivider
import org.equeim.tremotesf.R
import org.equeim.tremotesf.common.causes
import org.equeim.tremotesf.databinding.DetailedConnectionErrorDialogBinding
import org.equeim.tremotesf.databinding.DetailedConnectionErrorExpandedDialogBinding
import org.equeim.tremotesf.rpc.DetailedRpcRequestError
import org.equeim.tremotesf.rpc.redactHeader
import org.equeim.tremotesf.ui.NavigationDialogFragment
import org.equeim.tremotesf.ui.utils.Utils

class DetailedConnectionErrorDialogFragment : NavigationDialogFragment() {
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val builder = MaterialAlertDialogBuilder(requireContext())
        val binding = DetailedConnectionErrorDialogBinding.inflate(LayoutInflater.from(builder.context))
        val error = DetailedConnectionErrorDialogFragmentArgs.fromBundle(requireArguments()).error

        binding.addItem("Error: ${error.error}", "Error") { error.error.details() }

        error.suppressedErrors.forEach {
            binding.addItem("Suppressed: error: $it", "Suppressed error") { it.details() }
        }

        error.responseInfo?.let { response ->
            binding.addItem("HTTP response: ${response.status}", "HTTP response") { response.details() }
        }

        if (error.serverCertificates.isNotEmpty()) {
            binding.addItem("Server certificates", showDetailsMonospaceAndUnwrapped = true) {
                error.serverCertificates.joinToString("\n")
            }
        }
        if (error.clientCertificates.isNotEmpty()) {
            binding.addItem("Client certificates", showDetailsMonospaceAndUnwrapped = true) {
                error.clientCertificates.joinToString("\n")
            }
        }
        if (error.requestHeaders.isNotEmpty()) {
            binding.addItem("HTTP request headers") {
                error.requestHeaders.joinToString("\n") { header ->
                    val (name, value) = header.redactHeader()
                    "$name: $value"
                }
            }
        }

        return builder.setView(binding.root)
            .setTitle(R.string.detailed_error_message)
            .setNeutralButton(R.string.share) { _, _ ->
                Utils.shareText(
                    error.makeShareString(),
                    requireContext().getText(R.string.share),
                    requireContext()
                )
            }
            .setNegativeButton(R.string.close, null).create()
    }

    private fun DetailedConnectionErrorDialogBinding.addItem(
        summary: String,
        detailsTitle: String = summary,
        showDetailsMonospaceAndUnwrapped: Boolean = false,
        detailsText: () -> String,
    ) {
        items.addView(
            MaterialDivider(requireContext()),
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        val view = LayoutInflater.from(requireContext())
            .inflate(R.layout.detailed_connection_error_dialog_item, items, false) as TextView
        items.addView(view)
        view.text = summary
        view.setOnClickListener {
            navigate(
                DetailedConnectionErrorDialogFragmentDirections.toExpandedErrorDialogFragment(
                    detailsTitle,
                    detailsText(),
                    showDetailsMonospaceAndUnwrapped
                )
            )
        }
    }

    private companion object {
        fun Throwable.details(): String = buildString {
            append("${this@details}\n")
            for (cause in causes) {
                append("\nCaused by:\n$cause\n")
            }
        }

        fun DetailedRpcRequestError.ResponseInfo.details(): String = buildString {
            append("Status: $status\n")
            append("Protocol: $protocol\n")
            tlsHandshakeInfo?.let { handshake ->
                append("TLS version: ${handshake.tlsVersion}\n")
                append("Cipher suite: ${handshake.cipherSuite}\n")
            }
            append("Headers:\n")
            headers.forEach { header ->
                val (name, value) = header.redactHeader()
                append("  $name: $value\n")
            }
        }

        fun DetailedRpcRequestError.makeShareString(): String = buildString {
            append("Error:\n")
            append(error.details().indent())
            appendLine()
            suppressedErrors.forEach {
                append("Suppressed error:\n")
                append(it.details().indent())
                appendLine()
            }
            responseInfo?.let {
                append("HTTP response:\n")
                append(it.details().indent())
                appendLine()
            }
            if (serverCertificates.isNotEmpty()) {
                append("Server certificates:\n")
                append(serverCertificates.joinToString("\n").indent())
                appendLine()
            }
            if (clientCertificates.isNotEmpty()) {
                append("Client certificates:\n")
                append(clientCertificates.joinToString("\n").indent())
                appendLine()
            }
            if (requestHeaders.isNotEmpty()) {
                append("HTTP request headers:\n")
                append(requestHeaders.joinToString("\n") { header ->
                    val (name, value) = header.redactHeader()
                    "$name: $value"
                }.indent())
                appendLine()
            }
        }

        fun String.indent(): String =
            lineSequence()
                .map {
                    when {
                        it.isBlank() -> it
                        else -> "  $it"
                    }
                }
                .joinToString("\n")
    }
}

class DetailedConnectionErrorExpandedDialogFragment : NavigationDialogFragment() {
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val builder = MaterialAlertDialogBuilder(requireContext())
        val args = DetailedConnectionErrorExpandedDialogFragmentArgs.fromBundle(requireArguments())
        val view = DetailedConnectionErrorExpandedDialogBinding.inflate(LayoutInflater.from(builder.context))
            .run {
                text.apply {
                    text = args.text
                    if (args.monospaceAndUnwrapped) {
                        setTypeface(Typeface.MONOSPACE, Typeface.NORMAL)
                        setHorizontallyScrolling(true)
                    }
                }
                root
            }
        return builder.setTitle(args.title).setView(view).setNegativeButton(R.string.close, null).create()
    }
}
