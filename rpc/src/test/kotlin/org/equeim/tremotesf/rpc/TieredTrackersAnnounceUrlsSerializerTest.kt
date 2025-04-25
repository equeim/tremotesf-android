// SPDX-FileCopyrightText: 2017-2025 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package org.equeim.tremotesf.rpc

import kotlinx.serialization.json.Json
import org.equeim.tremotesf.rpc.requests.torrentproperties.TieredTrackersAnnounceUrlsSerializer
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class TieredTrackersAnnounceUrlsSerializerTest {
    @Test
    fun `TieredTrackersAnnounceUrlsSerializer deserialize empty`() {
        assertEquals(
            emptyList(), Json.decodeFromString(
                TieredTrackersAnnounceUrlsSerializer, """
            ""
        """.trimIndent()
            )
        )
    }

    @Test
    fun `TieredTrackersAnnounceUrlsSerializer deserialize empty line`() {
        assertEquals(
            emptyList(), Json.decodeFromString(
                TieredTrackersAnnounceUrlsSerializer, """
            "\n"
        """.trimIndent()
            )
        )
    }

    @Test
    fun `TieredTrackersAnnounceUrlsSerializer deserialize single tracker`() {
        assertEquals(
            listOf(setOf("http://foo.bar")),
            Json.decodeFromString(TieredTrackersAnnounceUrlsSerializer, """
                "http://foo.bar"
            """.trimIndent())
        )
    }

    @Test
    fun `TieredTrackersAnnounceUrlsSerializer deserialize two trackers in one tier`() {
        assertEquals(
            listOf(setOf("http://foo.bar", "http://bar.foo")),
            Json.decodeFromString(TieredTrackersAnnounceUrlsSerializer, """
                "http://foo.bar\nhttp://bar.foo"
            """.trimIndent())
        )
    }

    @Test
    fun `TieredTrackersAnnounceUrlsSerializer deserialize two trackers in two tiers`() {
        assertEquals(
            listOf(setOf("http://foo.bar"), setOf("http://bar.foo")),
            Json.decodeFromString(TieredTrackersAnnounceUrlsSerializer, """
                "http://foo.bar\n\nhttp://bar.foo"
            """.trimIndent())
        )
    }

    @Test
    fun `TieredTrackersAnnounceUrlsSerializer deserialize two trackers in two tiers, with trailing newline`() {
        assertEquals(
            listOf(setOf("http://foo.bar"), setOf("http://bar.foo")),
            Json.decodeFromString(TieredTrackersAnnounceUrlsSerializer, """
                "http://foo.bar\n\nhttp://bar.foo\n"
            """.trimIndent())
        )
    }

    @Test
    fun `TieredTrackersAnnounceUrlsSerializer serialize empty`() {
        assertEquals(
            """
                ""
            """.trimIndent(),
            Json.encodeToString(TieredTrackersAnnounceUrlsSerializer, emptyList())
        )
    }

    @Test
    fun `TieredTrackersAnnounceUrlsSerializer serialize single tracker`() {
        assertEquals(
            """
                "http://foo.bar"
            """.trimIndent(),
            Json.encodeToString(TieredTrackersAnnounceUrlsSerializer, listOf(setOf("http://foo.bar")))
        )
    }

    @Test
    fun `TieredTrackersAnnounceUrlsSerializer serialize two trackers in one tier`() {
        assertEquals(
            """
                "http://foo.bar\nhttp://bar.foo"
            """.trimIndent(),
            Json.encodeToString(TieredTrackersAnnounceUrlsSerializer, listOf(setOf("http://foo.bar", "http://bar.foo")))
        )
    }

    @Test
    fun `TieredTrackersAnnounceUrlsSerializer serialize two trackers in two tier`() {
        assertEquals(
            """
                "http://foo.bar\n\nhttp://bar.foo"
            """.trimIndent(),
            Json.encodeToString(TieredTrackersAnnounceUrlsSerializer, listOf(setOf("http://foo.bar"), setOf("http://bar.foo")))
        )
    }
}