// SPDX-FileCopyrightText: 2017-2025 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package org.equeim.tremotesf.ui.utils

import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.snapshots.SnapshotStateList

@Suppress("FunctionName")
fun <T> SnapshotStateListSaver(): Saver<SnapshotStateList<T>, Any> = listSaver(
    save = { it },
    restore = { SnapshotStateList<T>().apply { addAll(it) } }
)
