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
import android.util.Log;

import org.qtproject.qt5.android.QtNative;

public class LibTremotesf {
    private static final String TAG = "LibTremotesf";

    public static void init(ClassLoader classLoader) {
        Log.d(TAG, "init() called with: classLoader = [" + classLoader + "]");
        System.loadLibrary("c++_shared");
        QtNative.setClassLoader(classLoader);

        if (BuildConfig.QT_HAS_ABI_SUFFIX) {
            final String suffix;
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
                suffix = Build.CPU_ABI;
            } else {
                suffix = Build.SUPPORTED_ABIS[0];
            }
            System.loadLibrary("Qt5Core_" + suffix);
            System.loadLibrary("Qt5Network_" + suffix);
            System.loadLibrary("tremotesf_" + suffix);
        } else {
            System.loadLibrary("Qt5Core");
            System.loadLibrary("Qt5Network");
            System.loadLibrary("tremotesf");
        }

        Log.d(TAG, "init: loaded native libraries");
    }
}
