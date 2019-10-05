package org.equeim.tremotesf

import org.jetbrains.anko.AnkoLogger
import org.jetbrains.anko.info

import org.qtproject.qt5.android.QtNative


class Application : android.app.Application(), AnkoLogger {
    companion object {
        private var loaded = false
        fun loadLibrary(classLoader: ClassLoader) {
            if (!loaded) {
                QtNative.setClassLoader(classLoader)
                System.loadLibrary("c++_shared")
                System.loadLibrary("Qt5Core")
                System.loadLibrary("Qt5Network")
                System.loadLibrary("tremotesf")
                loaded = true
            }
        }
    }

    override fun onCreate() {
        info("Application.onCreate")
        loadLibrary(classLoader)
        Settings.context = this
        Servers.context = this
        Rpc.instance.context = this
        super.onCreate()
    }

    override fun onTerminate() {
        Rpc.instance.context = null
        Servers.context = null
        Settings.context = null
        super.onTerminate()
    }
}