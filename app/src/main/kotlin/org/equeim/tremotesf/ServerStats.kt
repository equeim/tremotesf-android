/*
 * Copyright (C) 2017 Alexey Rochev <equeim@gmail.com>
 *
 * This file is part of Tremotesf.
 *
 * Tremotesf is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Tremotesf is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.equeim.tremotesf

import com.google.gson.JsonObject


class ServerStats {
    var downloadSpeed = 0L
        private set
    var uploadSpeed = 0L
        private set

    val currentSession = SessionStats()
    val total = SessionStats()

    fun update(stats: JsonObject) {
        downloadSpeed = stats["downloadSpeed"].asLong
        uploadSpeed = stats["uploadSpeed"].asLong

        currentSession.update(stats.getAsJsonObject("current-stats"))
        total.update(stats.getAsJsonObject("cumulative-stats"))
    }

    class SessionStats {
        var downloaded = 0L
            private set
        var uploaded = 0L
            private set
        var duration = 0
            private set
        var sessionCount = 0
            private set

        fun update(jsonObject: JsonObject) {
            downloaded = jsonObject["downloadedBytes"].asLong
            uploaded = jsonObject["uploadedBytes"].asLong
            duration = jsonObject["secondsActive"].asInt
            sessionCount = jsonObject["sessionCount"].asInt
        }
    }
}