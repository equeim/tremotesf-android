package org.equeim.tremotesf.rpc

import android.annotation.SuppressLint
import android.content.Context
import androidx.annotation.MainThread
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.dropWhile
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.plus
import org.equeim.tremotesf.TremotesfApplication
import org.equeim.tremotesf.data.rpc.Rpc
import org.equeim.tremotesf.data.rpc.Servers
import org.equeim.tremotesf.ui.AppForegroundTracker
import org.equeim.tremotesf.utils.Logger
import java.util.concurrent.atomic.AtomicReference

@SuppressLint("StaticFieldLeak")
object GlobalServers : Servers(TremotesfApplication.instance) {
    private val saveData = AtomicReference<SaveData>()

    override fun setRpc(rpc: Rpc) {
        super.setRpc(rpc)
        AppForegroundTracker.appInForeground
            .dropWhile { !it }
            .onEach { inForeground ->
                if (!inForeground && rpc.isConnected.value) save()
                wifiNetworkController.enabled.value = inForeground
            }
            .launchIn(GlobalScope + Dispatchers.Main)
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
        Worker(context, workerParameters), Logger {

        override fun doWork(): Result {
            info("doWork() called")
            val data = saveData.getAndSet(null)
            if (data != null) {
                doSave(data)
            } else {
                warn("doWork: SaveData is null")
            }
            return Result.success()
        }

        override fun onStopped() {
            info("onStopped() called")
            saveData.set(null)
        }

        companion object {
            const val UNIQUE_WORK_NAME = "ServersSaveWorker"
        }
    }
}
