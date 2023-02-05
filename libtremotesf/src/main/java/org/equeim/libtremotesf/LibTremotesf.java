// SPDX-FileCopyrightText: 2017-2022 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package org.equeim.libtremotesf;

import android.os.Build;

import org.qtproject.qt.android.QtNative;
import timber.log.Timber;

public class LibTremotesf {
    public static void init(ClassLoader classLoader) {
        Timber.d("init() called with: classLoader = [" + classLoader + "]");

        System.loadLibrary("c++_shared");

        QtNative.setClassLoader(classLoader);

        final String suffix = Build.SUPPORTED_ABIS[0];
        System.loadLibrary("Qt6Core_" + suffix);
        System.loadLibrary("Qt6Network_" + suffix);
        System.loadLibrary("tremotesf");

        Timber.d("init: loaded native libraries");
    }
}
