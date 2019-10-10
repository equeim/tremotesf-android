package org.equeim.tremotesf

import org.jetbrains.anko.AnkoLogger
import org.jetbrains.anko.info

class Application : android.app.Application(), AnkoLogger {
    companion object {
        lateinit var instance: Application
    }

    override fun onCreate() {
        super.onCreate()
        info("Application.onCreate")
        instance = this
    }
}