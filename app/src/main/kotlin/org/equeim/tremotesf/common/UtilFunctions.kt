// SPDX-FileCopyrightText: 2017-2022 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package org.equeim.tremotesf.common

inline fun <reified T : Enum<T>> enumFromInt(value: Int, default: T): T {
    val values = enumValues<T>()
    return values.getOrNull(value) ?: default
}

inline fun <T, reified R> List<T>.mapToArray(transform: (T) -> R): Array<R> = Array(size) { transform(get(it)) }
