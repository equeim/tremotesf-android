// SPDX-FileCopyrightText: 2017-2025 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package org.equeim.tremotesf.ui

import android.app.Activity
import androidx.activity.ComponentActivity
import androidx.annotation.MainThread
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.plus
import timber.log.Timber
import java.lang.ref.WeakReference

object AppForegroundTracker {
    private val scope = CoroutineScope(SupervisorJob())

    private val startedActivities = mutableSetOf<Activity>()
    private val changingConfigurationActivities = mutableSetOf<WeakReference<Activity>>()
    private val startedActivitiesCount = MutableStateFlow(0)

    @MainThread
    fun registerActivity(activity: ComponentActivity) {
        Timber.d("registerActivity() called with: activity = $activity")
        activity.lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                Timber.d("onStart() called with: owner = $owner")
                if (!startedActivities.add(activity)) {
                    Timber.e("onStart: activity is already in startedActivities")
                }
                changingConfigurationActivities.clear()
                updateStartedActivitiesCount()
            }

            override fun onStop(owner: LifecycleOwner) {
                Timber.d("onStop() called with: owner = $owner")
                if (!startedActivities.remove(activity)) {
                    Timber.e("onStop: activity is not in startedActivities")
                }
                if (activity.isChangingConfigurations) {
                    Timber.i("onStop: activity is changing configuration")
                    changingConfigurationActivities.add(WeakReference(activity))
                }
                updateStartedActivitiesCount()
            }
        })
    }

    private fun updateStartedActivitiesCount() {
        Timber.d("updateStartedActivitiesCount: startedActivities size = ${startedActivities.size}")
        Timber.d("updateStartedActivitiesCount: changingConfigurationActivities size = ${changingConfigurationActivities.size}")
        startedActivitiesCount.value = startedActivities.size + changingConfigurationActivities.size
    }

    val hasStartedActivity = startedActivitiesCount.map { it > 0 }

    private val foregroundServiceStarted = MutableStateFlow(false)

    @MainThread
    fun registerForegroundService(service: LifecycleService) {
        Timber.d("registerForegroundService() called with: service = $service")
        service.lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                Timber.d("onStart() called with: owner = $owner")
                if (!foregroundServiceStarted.compareAndSet(expect = false, update = true)) {
                    Timber.e("onStart: foreground service is already started")
                }
            }

            override fun onStop(owner: LifecycleOwner) {
                Timber.d("onStop() called with: owner = $owner")
                if (!foregroundServiceStarted.compareAndSet(expect = true, update = false)) {
                    Timber.e("onStart: foreground service is already stopped")
                }
            }
        })
    }

    val appInForeground = combine(hasStartedActivity, foregroundServiceStarted, Boolean::or)
        .stateIn(scope + Dispatchers.Unconfined, SharingStarted.Eagerly, false)

    init {
        startedActivitiesCount.onEach { count ->
            Timber.d("Started activities count = $count")
        }.launchIn(scope + Dispatchers.Main)

        foregroundServiceStarted.onEach { started ->
            if (started) {
                Timber.d("Foreground service is started")
            } else {
                Timber.d("Foreground service is stopped")
            }
        }.launchIn(scope + Dispatchers.Main)

        appInForeground
            .onEach { inForeground ->
                if (inForeground) {
                    Timber.i("App is in foreground")
                } else {
                    Timber.i("App is in background")
                }
            }
            .launchIn(scope + Dispatchers.Main)
    }
}
