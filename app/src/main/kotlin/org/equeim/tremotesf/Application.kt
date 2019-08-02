package org.equeim.tremotesf

import android.app.NotificationManager
import androidx.core.content.getSystemService
import org.qtproject.qt5.android.QtNative


class Application : android.app.Application() {
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

    lateinit var notificationManager: NotificationManager

    override fun onCreate() {
        notificationManager = getSystemService<NotificationManager>()!!
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