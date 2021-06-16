package org.equeim.tremotesf.rpc

import android.annotation.SuppressLint
import android.content.Context
import androidx.annotation.MainThread
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.dropWhile
import kotlinx.coroutines.flow.filterNot
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.plus
import org.equeim.tremotesf.TremotesfApplication
import org.equeim.tremotesf.data.rpc.Rpc
import org.equeim.tremotesf.data.rpc.Servers
import org.equeim.tremotesf.data.rpc.WifiNetworkServersController
import org.equeim.tremotesf.ui.AppForegroundTracker
import timber.log.Timber
import java.util.concurrent.atomic.AtomicReference


@SuppressLint("StaticFieldLeak")
object GlobalServers : Servers(TremotesfApplication.instance) {
    private val saveData = AtomicReference<SaveData>()

    init {
        AppForegroundTracker.appInForeground
            .dropWhile { !it }
            .filterNot { it }
            .onEach { save() }
            .launchIn(@OptIn(DelicateCoroutinesApi::class) GlobalScope + Dispatchers.Main)
    }

    @MainThread
    override fun save(saveData: SaveData) {
        this.saveData.set(saveData)
        WorkManager.getInstance(context).enqueueUniqueWork(
            SaveWorker.UNIQUE_WORK_NAME,
            ExistingWorkPolicy.APPEND,
            OneTimeWorkRequest.from(SaveWorker::class.java)
        )
    }

    class SaveWorker(context: Context, workerParameters: WorkerParameters) :
        Worker(context, workerParameters) {

        override fun doWork(): Result {
            Timber.i("doWork() called")
            val data = saveData.getAndSet(null)
            if (data != null) {
                doSave(data)
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
