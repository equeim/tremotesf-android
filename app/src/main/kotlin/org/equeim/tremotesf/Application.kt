package org.equeim.tremotesf

class Application : android.app.Application() {
    companion object {
        private var loaded = false
        fun loadLibrary() {
            if (!loaded) {
                System.loadLibrary("Qt5Core")
                System.loadLibrary("tremotesf")
                loaded = true
            }
        }
    }

    override fun onCreate() {
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