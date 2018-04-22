package org.equeim.tremotesf

class Application : android.app.Application() {
    override fun onCreate() {
        Settings.context = this
        Servers.context = this
        Rpc.context = this
        super.onCreate()
    }

    override fun onTerminate() {
        Rpc.context = null
        Servers.context = null
        Settings.context = null
        super.onTerminate()
    }
}