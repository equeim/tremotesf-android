package org.equeim.tremotesf

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter

import org.equeim.tremotesf.utils.DecimalFormats

import org.jetbrains.anko.AnkoLogger
import org.jetbrains.anko.info

class Application : android.app.Application(), AnkoLogger {
    companion object {
        lateinit var instance: Application
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