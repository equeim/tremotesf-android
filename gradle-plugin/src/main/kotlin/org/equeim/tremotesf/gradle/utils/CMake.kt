// SPDX-FileCopyrightText: 2017-2022 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package org.equeim.tremotesf.gradle.utils

import org.apache.commons.lang3.SystemUtils
import org.gradle.api.logging.Logger
import org.gradle.api.provider.ProviderFactory
import java.lang.module.ModuleDescriptor

data class CMakeInfo(val executablePath: String, val version: ModuleDescriptor.Version)

@Suppress("UnstableApiUsage")
fun getCMakeInfoFromPathOrNull(
    providersFactory: ProviderFactory,
    logger: Logger
): CMakeInfo? {
    val executablePath = runCatching {
        providersFactory.execAndCaptureOutput {
            if (SystemUtils.IS_OS_WINDOWS) {
                commandLine("where", "cmake")
            } else {
                commandLine("which", "cmake")
            }
        }.trim()
    }.getOrElse {
        logger.error("Failed to find CMake in PATH: $it")
        return null
    }
    val version = runCatching {
        providersFactory.execAndCaptureOutput {
            commandLine(executablePath, "--version")
        }.lineSequence()
            .first()
            .trim()
            .split(Regex("\\s"))
            .last()
            .let(ModuleDescriptor.Version::parse)
    }.getOrElse {
        logger.error("Failed to determine CMake version", it)
        return null
    }
    return CMakeInfo(executablePath, version)
}
