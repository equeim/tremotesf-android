package org.equeim.tremotesf

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter

import org.equeim.tremotesf.ui.utils.DecimalFormats
import org.equeim.tremotesf.utils.Logger

class TremotesfApplication : Application(), Logger {
    companion object {
        lateinit var instance: TremotesfApplication
    }

    override fun onCreate() {
        info("Application.onCreate")
        instance = this
        super.onCreate()
        registerReceiver(object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                DecimalFormats.reset()
            }
        }, IntentFilter(Intent.ACTION_LOCALE_CHANGED))
    }
}