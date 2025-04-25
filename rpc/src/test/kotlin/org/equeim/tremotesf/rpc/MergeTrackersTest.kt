// SPDX-FileCopyrightText: 2017-2025 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package org.equeim.tremotesf.rpc

import org.equeim.tremotesf.rpc.requests.torrentproperties.mergeWith
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class MergeTrackersTest {
    @Test
    fun `Completely new trackers`() {
        val existing = listOf(setOf("foo"), setOf("bar"))
        val new = listOf(setOf("lol"), setOf("nope"))
        assertEquals(listOf(setOf("foo"), setOf("bar"), setOf("lol"), setOf("nope")), existing.mergeWith(new))
    }

    @Test
    fun `Adding to existing tier`() {
        val existing = listOf(setOf("foo"), setOf("bar"))
        val new = listOf(setOf("foo", "foo.alt", "foo.alt2"), setOf("nope"))
        assertEquals(listOf(setOf("foo", "foo.alt", "foo.alt2"), setOf("bar"), setOf("nope")), existing.mergeWith(new))
    }
}