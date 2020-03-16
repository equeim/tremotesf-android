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

package org.equeim.tremotesf.serversettingsfragment

import java.text.DateFormat
import java.text.DateFormatSymbols
import java.util.Calendar

import android.app.Dialog
import android.app.TimePickerDialog

import android.content.Context
import android.os.Bundle

import android.util.AttributeSet

import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.TimePicker

import androidx.core.os.bundleOf
import androidx.navigation.findNavController

import org.equeim.libtremotesf.ServerSettingsData
import org.equeim.tremotesf.NavigationDialogFragment
import org.equeim.tremotesf.R
import org.equeim.tremotesf.Rpc
import org.equeim.tremotesf.utils.ArrayDropdownAdapter
import org.equeim.tremotesf.utils.IntFilter
import org.equeim.tremotesf.utils.doAfterTextChangedAndNotEmpty

import kotlinx.android.synthetic.main.server_settings_speed_fragment.*
import kotlinx.android.synthetic.main.server_settings_time_picker_item.view.*


class SpeedFragment : ServerSettingsFragment.BaseFragment(R.layout.server_settings_speed_fragment,
                                                          R.string.server_settings_speed) {
    private val days = mutableListOf(ServerSettingsData.AlternativeSpeedLimitsDays.All,
                                     ServerSettingsData.AlternativeSpeedLimitsDays.Weekdays,
                                     ServerSettingsData.AlternativeSpeedLimitsDays.Weekends)
    private val daysSpinnerItems = mutableListOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        daysSpinnerItems.add(getString(R.string.every_day))
        daysSpinnerItems.add(getString(R.string.weekdays))
        daysSpinnerItems.add(getString(R.string.weekends))

        val dayNames = DateFormatSymbols.getInstance().weekdays

        val nextDay = { day: Int ->
            if (day == Calendar.SATURDAY) {
                Calendar.SUNDAY
            } else {
                day + 1
            }
        }

        val dayFromCalendarDay = { day: Int ->
            when (day) {
                Calendar.SUNDAY -> ServerSettingsData.AlternativeSpeedLimitsDays.Sunday
                Calendar.MONDAY -> ServerSettingsData.AlternativeSpeedLimitsDays.Monday
                Calendar.TUESDAY -> ServerSettingsData.AlternativeSpeedLimitsDays.Tuesday
                Calendar.WEDNESDAY -> ServerSettingsData.AlternativeSpeedLimitsDays.Wednesday
                Calendar.THURSDAY -> ServerSettingsData.AlternativeSpeedLimitsDays.Thursday
                Calendar.FRIDAY -> ServerSettingsData.AlternativeSpeedLimitsDays.Friday
                Calendar.SATURDAY -> ServerSettingsData.AlternativeSpeedLimitsDays.Saturday
                else -> ServerSettingsData.AlternativeSpeedLimitsDays.Monday
            }
        }

        val first = Calendar.getInstance().firstDayOfWeek
        days.add(dayFromCalendarDay(first))
        daysSpinnerItems.add(dayNames[first])

        var day = nextDay(first)
        while (day != first) {
            days.add(dayFromCalendarDay(day))
            daysSpinnerItems.add(dayNames[day])
            day = nextDay(day)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val limitsFilters = arrayOf(IntFilter(0 until 4 * 1024 * 1024))

        download_speed_limit_check_box.isChecked = Rpc.serverSettings.isDownloadSpeedLimited
        download_speed_limit_check_box.setOnCheckedChangeListener { _, checked ->
            download_speed_limit_layout.isEnabled = checked
            Rpc.serverSettings.isDownloadSpeedLimited = checked
        }

        download_speed_limit_layout.isEnabled = download_speed_limit_check_box.isChecked

        download_speed_limit_edit.filters = limitsFilters
        download_speed_limit_edit.setText(Rpc.serverSettings.downloadSpeedLimit().toString())
        download_speed_limit_edit.doAfterTextChangedAndNotEmpty {
            Rpc.serverSettings.setDownloadSpeedLimit(it.toString().toInt())
        }

        upload_speed_limit_check_box.isChecked = Rpc.serverSettings.isUploadSpeedLimited
        upload_speed_limit_check_box.setOnCheckedChangeListener { _, checked ->
            upload_speed_limit_layout.isEnabled = checked
            Rpc.serverSettings.isUploadSpeedLimited = checked
        }

        upload_speed_limit_layout.isEnabled = upload_speed_limit_check_box.isChecked

        upload_speed_limit_edit.filters = limitsFilters
        upload_speed_limit_edit.setText(Rpc.serverSettings.uploadSpeedLimit().toString())
        upload_speed_limit_edit.doAfterTextChangedAndNotEmpty {
            Rpc.serverSettings.setUploadSpeedLimit(it.toString().toInt())
        }

        alternative_limits_check_box.isChecked = Rpc.serverSettings.isAlternativeSpeedLimitsEnabled
        alternative_limits_check_box.setOnCheckedChangeListener { _, checked ->
            alternative_download_speed_limit_layout.isEnabled = checked
            alternative_upload_speed_limit_layout.isEnabled = checked
            Rpc.serverSettings.isAlternativeSpeedLimitsEnabled = checked
        }

        alternative_download_speed_limit_layout.isEnabled = alternative_limits_check_box.isChecked
        alternative_upload_speed_limit_layout.isEnabled = alternative_limits_check_box.isChecked

        alternative_download_speed_limit_edit.filters = limitsFilters
        alternative_download_speed_limit_edit.setText(Rpc.serverSettings.alternativeDownloadSpeedLimit().toString())
        alternative_download_speed_limit_edit.doAfterTextChangedAndNotEmpty {
            Rpc.serverSettings.setAlternativeDownloadSpeedLimit(it.toString().toInt())
        }

        alternative_upload_speed_limit_edit.filters = limitsFilters
        alternative_upload_speed_limit_edit.setText(Rpc.serverSettings.alternativeUploadSpeedLimit().toString())
        alternative_download_speed_limit_edit.doAfterTextChangedAndNotEmpty {
            Rpc.serverSettings.setAlternativeUploadSpeedLimit(it.toString().toInt())
        }

        schedule_check_box.isChecked = Rpc.serverSettings.isAlternativeSpeedLimitsScheduled
        val setEnabled = { enabled: Boolean ->
            begin_time_item.isEnabled = enabled
            end_time_item.isEnabled = enabled
            days_view_layout.isEnabled = enabled
        }
        schedule_check_box.setOnCheckedChangeListener { _, checked ->
            setEnabled(checked)
            Rpc.serverSettings.isAlternativeSpeedLimitsScheduled = checked
        }

        setEnabled(schedule_check_box.isChecked)

        begin_time_item.beginTime = true
        begin_time_item.setTime(Rpc.serverSettings.alternativeSpeedLimitsBeginTime())

        end_time_item.beginTime = false
        end_time_item.setTime(Rpc.serverSettings.alternativeSpeedLimitsEndTime())

        days_view.setAdapter(ArrayDropdownAdapter(daysSpinnerItems))
        days_view.setText(days_view.adapter.getItem(days.indexOf(Rpc.serverSettings.alternativeSpeedLimitsDays())).toString())
        days_view.setOnItemClickListener { _, _, position, _ ->
            Rpc.serverSettings.setAlternativeSpeedLimitsDays(days[position])
        }
    }
}

class TimePickerItem(context: Context, attrs: AttributeSet) : FrameLayout(context, attrs) {
    var beginTime = false
    private val calendar: Calendar = Calendar.getInstance()
    private val titleTextView: View
    private val textView: TextView
    private val format = DateFormat.getTimeInstance(DateFormat.SHORT)

    init {
        inflate(context, R.layout.server_settings_time_picker_item, this)
        titleTextView = title_text_view
        textView = text_view

        val ta = context.theme.obtainStyledAttributes(attrs,
                                                      intArrayOf(android.R.attr.title),
                                                      0,
                                                      0)
        title_text_view.text = ta.getText(0)
        ta.recycle()

        setOnClickListener {
            findNavController().navigate(R.id.action_speedFragment_to_timePickerFragment,
                                         bundleOf("beginTime" to beginTime,
                                                  "hourOfDay" to calendar.get(Calendar.HOUR_OF_DAY),
                                                  "minute" to calendar.get(Calendar.MINUTE)))
        }
    }

    override fun setEnabled(enabled: Boolean) {
        super.setEnabled(enabled)
        titleTextView.isEnabled = enabled
        textView.isEnabled = enabled
    }

    fun setTime(hourOfDay: Int, minute: Int) {
        calendar.set(Calendar.HOUR_OF_DAY, hourOfDay)
        calendar.set(Calendar.MINUTE, minute)
        textView.text = format.format(calendar.time)
    }

    fun setTime(minutesFromStartOfDay: Int) {
        calendar.set(Calendar.HOUR_OF_DAY, minutesFromStartOfDay / 60)
        calendar.set(Calendar.MINUTE, minutesFromStartOfDay.rem(60))
        textView.text = format.format(calendar.time)
    }

    class TimePickerFragment : NavigationDialogFragment(), TimePickerDialog.OnTimeSetListener {
        companion object {
            const val BEGIN_TIME = "beginTime"
            const val HOUR_OF_DAY = "hourOfDay"
            const val MINUTE = "minute"
        }

        override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
            return TimePickerDialog(activity,
                                    this,
                                    requireArguments().getInt(HOUR_OF_DAY),
                                    requireArguments().getInt(MINUTE),
                                    android.text.format.DateFormat.is24HourFormat(activity))
        }

        override fun onTimeSet(view: TimePicker?, hourOfDay: Int, minute: Int) {
            val speedFragment = parentFragmentManager.primaryNavigationFragment as? SpeedFragment
            if (speedFragment != null) {
                if (requireArguments().getBoolean(BEGIN_TIME)) {
                    speedFragment.begin_time_item.setTime(hourOfDay, minute)
                    Rpc.serverSettings.setAlternativeSpeedLimitsBeginTime((hourOfDay * 60) + minute)
                } else {
                    speedFragment.end_time_item.setTime(hourOfDay, minute)
                    Rpc.serverSettings.setAlternativeSpeedLimitsEndTime((hourOfDay * 60) + minute)
                }
            }
        }
    }
}