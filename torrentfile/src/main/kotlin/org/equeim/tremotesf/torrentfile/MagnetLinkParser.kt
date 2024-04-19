// SPDX-FileCopyrightText: 2017-2024 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package org.equeim.tremotesf.torrentfile

import android.net.Uri
import android.net.UrlQuerySanitizer
import timber.log.Timber

data class MagnetLink(
    val uri: Uri,
    val infoHashV1: String,
    val trackers: List<Set<String>>,
)

fun parseMagnetLink(uri: Uri): MagnetLink {
    Timber.d("parseMagnetLink() called with: uri = $uri")
    if (uri.scheme != SCHEME_MAGNET) {
        throw IllegalArgumentException("Scheme must be $SCHEME_MAGNET")
    }
    try {
        /**
         * [Uri.getQueryParameters] doesn't work for magnet URIs
         */
        val sanitizer = UrlQuerySanitizer()
        sanitizer.allowUnregisteredParamaters = true
        sanitizer.parseQuery(uri.query)

        val xtValues = sanitizer.parameterList.mapNotNull {
            if (it.mParameter == "xt") it.mValue else null
        }
        val infoHashV1 = xtValues.find { it.startsWith(XT_PREFIX_V1) }?.substring(XT_PREFIX_V1.length)
            ?: throw IllegalArgumentException("Did not find info hash in the URI")

        val trackers = sanitizer.parameterList.mapNotNull {
            if (it.mParameter == "tr") setOf(it.mValue) else null
        }

        return MagnetLink(uri, infoHashV1, trackers).also {
            Timber.d("parseMagnetLink: returning $it")
        }
    } catch (e: Exception) {
        throw IllegalArgumentException("Failed to process this URI", e)
    }

}

const val SCHEME_MAGNET = "magnet"
private const val XT_PREFIX_V1 = "urn:btih:"
