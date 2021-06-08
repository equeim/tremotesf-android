/*
 * Copyright (C) 2017-2021 Alexey Rochev <equeim@gmail.com>
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

package org.equeim.libtremotesf;

import android.os.Build;

import org.qtproject.qt5.android.QtNative;
import timber.log.Timber;

public class LibTremotesfLoader {
    public static void init(ClassLoader classLoader) {
        Timber.d("init() called with: classLoader = [" + classLoader + "]");
        System.loadLibrary("c++_shared");
        QtNative.setClassLoader(classLoader);

        final String suffix;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            suffix = Build.CPU_ABI;
        } else {
            suffix = Build.SUPPORTED_ABIS[0];
        }
        System.loadLibrary("Qt5Core_" + suffix);
        System.loadLibrary("Qt5Network_" + suffix);
        System.loadLibrary("tremotesf_" + suffix);

        System.setProperty("org.bytedeco.javacpp.loadlibraries", "false");

        Timber.d("init: loaded native libraries");
    }
}
