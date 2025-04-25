// SPDX-FileCopyrightText: 2017-2025 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package org.equeim.tremotesf.rpc

import android.annotation.SuppressLint
import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.await
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.equeim.tremotesf.TremotesfApplication
import org.equeim.tremotesf.ui.AppForegroundTracker
import timber.log.Timber
import java.util.concurrent.atomic.AtomicReference


@SuppressLint("StaticFieldLeak")
object GlobalServers : Servers(@OptIn(DelicateCoroutinesApi::class) GlobalScope, TremotesfApplication.instance) {
    val wifiNetworkController = WifiNetworkServersController(this, AppForegroundTracker.appInForeground, scope, context)

    private val saveData = AtomicReference<ServersState>()

    init {
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            wifiNetworkController.setCurrentServerFromWifiNetwork()
        }
    }

    override fun save(serversState: ServersState) {
        this.saveData.set(serversState)
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                Timber.d("Scheduling save worker")
                WorkManager.getInstance(context).enqueueUniqueWork(
                    SaveWorker.UNIQUE_WORK_NAME,
                    ExistingWorkPolicy.APPEND,
                    OneTimeWorkRequestBuilder<SaveWorker>().build()
                ).await()
                Timber.d("Scheduled save worker")
            } catch (e: Exception) {
                Timber.e(e, "Failed to schedule save worker")
            }
        }
    }

    class SaveWorker(context: Context, workerParameters: WorkerParameters) :
        Worker(context, workerParameters) {

        override fun doWork(): Result {
            Timber.i("doWork() called")
            val serversState = saveData.getAndSet(null)
            if (serversState != null) {
                doSave(serversState)
            } else {
                Timber.w("doWork: SaveData is null")
            }
            return Result.success()
        }

        override fun onStopped() {
            Timber.i("onStopped() called")
            saveData.set(null)
        }

        companion object {
            const val UNIQUE_WORK_NAME = "ServersSaveWorker"
        }
    }
}
