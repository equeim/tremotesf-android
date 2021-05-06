package org.equeim.tremotesf

import android.app.Application
import org.equeim.tremotesf.utils.Logger

class TremotesfApplication : Application(), Logger {
    companion object {
        lateinit var instance: TremotesfApplication
    }

    override fun onCreate() {
        info("onCreate() called")
        instance = this
        super.onCreate()
    }
}
