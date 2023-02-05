// SPDX-FileCopyrightText: 2017-2022 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package org.equeim.tremotesf.gradle.utils

import org.gradle.api.Project
import org.gradle.api.invocation.Gradle
import java.io.File

const val OPENSSL_DIR = "3rdparty/openssl"
const val QT_DIR = "3rdparty/qt"

internal const val CONFIGURE_LOG_FILE = "tremotesf.configure.log"
internal const val BUILD_LOG_FILE = "tremotesf.build.log"
internal const val INSTALL_LOG_FILE = "tremotesf.install.log"

internal const val CCACHE_PROPERTY = "org.equeim.tremotesf.ccache"
internal const val PRINT_BUILD_LOG_ON_ERROR_PROPERTY = "org.equeim.tremotesf.print-3rdparty-build-log-on-error"