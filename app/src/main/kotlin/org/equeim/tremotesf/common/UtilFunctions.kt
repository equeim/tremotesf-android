// SPDX-FileCopyrightText: 2017-2023 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package org.equeim.tremotesf.common

inline fun <T, reified R> List<T>.mapToArray(transform: (T) -> R): Array<R> = Array(size) { transform(get(it)) }
