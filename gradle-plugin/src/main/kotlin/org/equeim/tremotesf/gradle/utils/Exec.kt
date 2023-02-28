// SPDX-FileCopyrightText: 2017-2022 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package org.equeim.tremotesf.gradle.utils

import org.gradle.api.logging.Logger
import org.gradle.api.provider.ProviderFactory
import org.gradle.process.ExecOperations
import org.gradle.process.ExecSpec
import java.io.PipedInputStream
import java.io.PipedOutputStream

internal fun ExecOperations.execWithLogPrefix(logger: Logger, logPrefix: String, spec: ExecSpec.() -> Unit) {
    val outInputStream = PipedInputStream()
    val outOutputStream = PipedOutputStream()
    outOutputStream.connect(outInputStream)
    val prefix = "$logPrefix: "
    val outThread = Thread {
        outInputStream.reader().useLines {
            it.forEach { line -> logger.lifecycle(prefix + line) }
        }
    }.apply { start() }
    val errInputStream = PipedInputStream()
    val errOutputStream = PipedOutputStream()
    errOutputStream.connect(errInputStream)
    val errThread = Thread {
        errInputStream.reader().useLines {
            it.forEach { line -> logger.lifecycle(prefix + line) }
        }
    }.apply { start() }
    try {
        exec {
            spec()
            standardOutput = outOutputStream
            errorOutput = errOutputStream
        }
    } finally {
        outThread.interrupt()
        errThread.interrupt()
    }
}

@Suppress("UnstableApiUsage")
internal fun ProviderFactory.execAndCaptureOutput(spec: ExecSpec.() -> Unit): String {
    val output = exec {
        spec()
        isIgnoreExitValue = true
    }
    val result = output.result.get()
    runCatching {
        result.assertNormalExitValue()
    }.onFailure {
        throw RuntimeException("${it.message}:\n${output.standardError.asText.get()}")
    }.getOrThrow()
    return output.standardOutput.asText.get()
}
