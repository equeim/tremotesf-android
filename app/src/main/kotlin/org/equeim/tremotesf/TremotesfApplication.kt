// SPDX-FileCopyrightText: 2017-2025 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package org.equeim.tremotesf

import android.app.Application
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap

class TremotesfApplication : Application() {
    companion object {
        lateinit var instance: TremotesfApplication
            private set
    }

    override fun onCreate() {
        Timber.plant(TremotesfTree())
        Timber.i("onCreate() called")
        instance = this
        super.onCreate()
        Timber.i("onCreate() returned")
    }
}

private class TremotesfTree : Timber.DebugTree() {
    val tagCache = ConcurrentHashMap<String, String>()

    override fun createStackElementTag(element: StackTraceElement): String? {
        return tagCache.getOrPut(element.className) {
            var tag = super.createStackElementTag(element) ?: return null
            // Remove nested class suffix when nested class name starts with lowercase character
            // (it was most likely generated by Kotlin compiler as part of anonymous class name)
            // and then replace '$' with '.'
            var dollarSignIndex = tag.indexOf(DOLLAR_SIGN)
            if (dollarSignIndex <= 0) {
                tag
            } else {
                while (dollarSignIndex != -1) {
                    if (dollarSignIndex == (tag.length - 1) || !tag[dollarSignIndex + 1].isUpperCase()) {
                        tag = tag.substring(0, dollarSignIndex)
                        break
                    }
                    dollarSignIndex = tag.indexOf(DOLLAR_SIGN, dollarSignIndex + 1)
                }
                tag.replace(DOLLAR_SIGN, '.')
            }
        }
    }

    private companion object {
        const val DOLLAR_SIGN = '$'
    }
}
