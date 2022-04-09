/*
 * Copyright (C) 2017-2021 Alexey Rochev <equeim@gmail.com>
 *
 * This file is part of Tremotesf.
 *
 * Tremotesf is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Tremotesf is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

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
import kotlinx.coroutines.flow.*
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
