// SPDX-FileCopyrightText: 2017-2024 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package org.equeim.tremotesf.ui.utils

import androidx.core.view.isVisible
import org.equeim.tremotesf.NavMainDirections
import org.equeim.tremotesf.R
import org.equeim.tremotesf.databinding.PlaceholderLayoutBinding
import org.equeim.tremotesf.rpc.GlobalRpcClient
import org.equeim.tremotesf.rpc.getErrorString
import org.equeim.tremotesf.rpc.RpcRequestError
import org.equeim.tremotesf.rpc.makeDetailedError
import org.equeim.tremotesf.ui.NavigationActivity

fun PlaceholderLayoutBinding.showError(error: RpcRequestError) {
    root.isVisible = true
    progressBar.isVisible = false
    placeholderText.text = error.getErrorString(root.context)
    val showDetailedError = when (error) {
        is RpcRequestError.NoConnectionConfiguration, is RpcRequestError.ConnectionDisabled -> false
        else -> true
    }
    detailedErrorMessageButton.apply {
        isVisible = showDetailedError
        if (showDetailedError) {
            setOnClickListener {
                (context.activity as NavigationActivity).navigate(
                    NavMainDirections.toDetailedConnectionErrorDialogFragment(
                        error.makeDetailedError(GlobalRpcClient)
                    )
                )
            }
        } else {
            setOnClickListener(null)
        }
    }
}

fun PlaceholderLayoutBinding.showError(errorText: CharSequence) {
    root.isVisible = true
    progressBar.isVisible = false
    placeholderText.text = errorText
    detailedErrorMessageButton.apply {
        isVisible = false
        setOnClickListener(null)
    }
}

fun PlaceholderLayoutBinding.showLoading(loadingText: CharSequence = root.context.getText(R.string.loading)) {
    root.isVisible = true
    progressBar.isVisible = true
    placeholderText.text = loadingText
    detailedErrorMessageButton.apply {
        isVisible = false
        setOnClickListener(null)
    }
}

fun PlaceholderLayoutBinding.hide() {
    root.isVisible = false
    placeholderText.text = null
    detailedErrorMessageButton.setOnClickListener(null)
}