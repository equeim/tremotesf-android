/*
 * Copyright (C) 2017-2018 Alexey Rochev <equeim@gmail.com>
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
import android.os.Build
import android.os.Bundle

import androidx.appcompat.app.AppCompatActivity

import org.equeim.tremotesf.utils.Utils


private val createdActivities = mutableListOf<BaseActivity>()

@SuppressLint("Registered")
open class BaseActivity : AppCompatActivity() {
    protected var creating = true
    protected var active = false

    companion object {
        var activeActivity: BaseActivity? = null
            private set

        fun finishAllActivities() {
            for (activity in createdActivities) {
                activity.finish()
            }
            createdActivities.clear()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        createdActivities.add(this)
        Utils.initApp(applicationContext)
    }

    override fun onStart() {
        super.onStart()
        activeActivity = this
        active = true
        if (Settings.backgroundServiceEnabled) {
            Rpc.instance.setBackgroundUpdate(false)
        } else {
            Rpc.instance.isUpdateDisabled = false
        }
    }

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)
        creating = false
    }

    override fun onStop() {
        super.onStop()

        if (!isChangingConfigurations) {
            active = false

            if (activeActivity === this) {
                activeActivity = null

                if (Settings.backgroundServiceEnabled) {
                    Rpc.instance.setBackgroundUpdate(true)
                } else {
                    Rpc.instance.isUpdateDisabled = true
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        createdActivities.remove(this)
        if (isFinishing && createdActivities.isEmpty() && !Settings.backgroundServiceEnabled) {
            Utils.shutdownApp()
        }
    }

    protected fun setPreLollipopShadow() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            Utils.setPreLollipopContentShadowOnFrame(findViewById(R.id.content_frame))
        }
    }
}