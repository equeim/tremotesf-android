// SPDX-FileCopyrightText: 2017-2022 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package org.equeim.tremotesf.gradle.utils

import org.gradle.api.logging.Logger

private const val CCACHE = "ccache"

internal fun zeroCcacheStatistics(logger: Logger) {
    executeCommand(listOf(CCACHE, "-z"), logger)
}

internal fun showCcacheStatistics(logger: Logger) {
    logger.lifecycle("\nCcache statistics:")
    executeCommand(listOf(CCACHE, "-s"), logger, outputMode = ExecOutputMode.PrintOutput)
}
