package org.equeim.tremotesf

import android.app.NotificationManager

class Application : android.app.Application() {
    companion object {
        private var loaded = false
        fun loadLibrary() {
            if (!loaded) {
                System.loadLibrary("gnustl_shared")
                System.loadLibrary("Qt5Core")
                System.loadLibrary("Qt5Concurrent")
                System.loadLibrary("Qt5Network")
                System.loadLibrary("crypto")
                System.loadLibrary("ssl")
                System.loadLibrary("tremotesf")
                loaded = true
            }
        }
    }

    lateinit var notificationManager: NotificationManager

    override fun onCreate() {
        notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        loadLibrary()
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