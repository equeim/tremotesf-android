/*
 * Copyright (C) 2017-2019 Alexey Rochev <equeim@gmail.com>
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

package org.equeim.tremotesf

import android.annotation.SuppressLint
import android.os.Bundle
import android.widget.Toast

import androidx.annotation.LayoutRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

import org.jetbrains.anko.intentFor

import org.equeim.tremotesf.utils.Utils


@SuppressLint("Registered")
open class BaseActivity(@LayoutRes contentLayoutId: Int,
                        private val noActionBar: Boolean,
                        private val setPreLollipopShadow: Boolean = noActionBar) : AppCompatActivity(contentLayoutId) {
    constructor(noActionBar: Boolean,
                setPreLollipopShadow: Boolean = noActionBar) : this(0, noActionBar, setPreLollipopShadow)

    companion object {
        private val createdActivities = mutableListOf<BaseActivity>()
        var activeActivity: BaseActivity? = null
            private set

        fun finishAllActivities() {
            for (activity in createdActivities) {
                activity.finish()
            }
            createdActivities.clear()
        }

        fun showToast(text: String) {
            activeActivity?.let { Toast.makeText(it, text, Toast.LENGTH_LONG).show() }
        }
    }

    protected var creating = true
    protected var active = false

    override fun onCreate(savedInstanceState: Bundle?) {
        if (noActionBar) {
            setTheme(Settings.themeNoActionBar)
        } else {
            setTheme(Settings.theme)
        }

        super.onCreate(savedInstanceState)

        if (noActionBar && setPreLollipopShadow) {
            Utils.setPreLollipopContentShadow(this)
        }

        createdActivities.add(this)
        if (Settings.showPersistentNotification) {
            ContextCompat.startForegroundService(this, intentFor<ForegroundService>())
        }
        Rpc.connectOnce()
    }

    override fun onStart() {
        super.onStart()

        if (activeActivity == null) {
            Rpc.cancelUpdateWorker()
            Rpc.nativeInstance.setUpdateDisabled(false)
        }

        activeActivity = this
        active = true
    }

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)
        creating = false
    }

    override fun onStop() {
        if (!isChangingConfigurations) {
            active = false

            if (activeActivity === this) {
                activeActivity = null

                if (!Settings.showPersistentNotification) {
                    Servers.save()
                    Rpc.nativeInstance.setUpdateDisabled(true)
                    Rpc.enqueueUpdateWorker()
                }
            }
        }
        super.onStop()
    }

    override fun onDestroy() {
        createdActivities.remove(this)
        super.onDestroy()
    }
}