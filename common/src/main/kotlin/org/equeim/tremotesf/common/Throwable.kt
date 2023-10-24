// SPDX-FileCopyrightText: 2017-2023 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package org.equeim.tremotesf.common

val Throwable.causes: Sequence<Throwable> get() = generateSequence(cause, Throwable::cause)
