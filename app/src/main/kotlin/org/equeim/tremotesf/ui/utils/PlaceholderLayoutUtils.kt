// SPDX-FileCopyrightText: 2017-2023 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package org.equeim.tremotesf.ui.utils

import androidx.core.view.isVisible
import org.equeim.tremotesf.NavMainDirections
import org.equeim.tremotesf.R
import org.equeim.tremotesf.databinding.PlaceholderLayoutBinding
import org.equeim.tremotesf.rpc.getErrorString
import org.equeim.tremotesf.torrentfile.rpc.RpcRequestError
import org.equeim.tremotesf.torrentfile.rpc.makeDetailedErrorString
import org.equeim.tremotesf.ui.NavigationActivity

fun PlaceholderLayoutBinding.show(error: RpcRequestError?) {
    root.isVisible = true
    progressBar.isVisible = error == null
    placeholderText.text = error?.getErrorString(root.context) ?: root.context.getString(R.string.loading)
    detailedErrorMessageButton.apply {
        when (error) {
            null, is RpcRequestError.NoConnectionConfiguration, is RpcRequestError.ConnectionDisabled -> {
                isVisible = false
                setOnClickListener(null)
            }

            else -> {
                isVisible = true
                setOnClickListener {
                    (context.activity as NavigationActivity).navigate(
                        NavMainDirections.toDetailedConnectionErrorDialogFragment(
                            error.makeDetailedErrorString()
                        )
                    )
                }
            }
        }
    }
}

fun PlaceholderLayoutBinding.show(error: CharSequence) {
    root.isVisible = true
    progressBar.isVisible = false
    placeholderText.text = error
    detailedErrorMessageButton.isVisible = false
    detailedErrorMessageButton.setOnClickListener(null)
}

fun PlaceholderLayoutBinding.show(error: Any?) {
    when (error) {
        null -> show(null as RpcRequestError?)
        is RpcRequestError -> show(error)
        is CharSequence -> show(error)
        else -> throw IllegalArgumentException()
    }
}
