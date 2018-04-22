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

package org.equeim.tremotesf.serversettingsactivity

import java.text.DateFormat
import java.text.DateFormatSymbols
import java.util.Calendar

import android.app.Dialog
import android.app.TimePickerDialog

import android.content.Context
import android.os.Bundle

import android.text.Editable
import android.text.TextWatcher

import android.util.AttributeSet

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup

import android.widget.AdapterView
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.TimePicker

import android.support.v4.app.DialogFragment
import android.support.v4.app.Fragment
import android.support.v7.app.AppCompatActivity

import org.equeim.tremotesf.R
import org.equeim.tremotesf.Rpc
import org.equeim.tremotesf.ServerSettings
import org.equeim.tremotesf.utils.ArraySpinnerAdapter
import org.equeim.tremotesf.utils.IntFilter
import org.equeim.tremotesf.utils.setChildrenEnabled

import kotlinx.android.synthetic.main.server_settings_speed_fragment.*


class SpeedFragment : Fragment() {
    companion object {
        const val TAG = "org.equeim.tremotesf.ServerSettingsActivity.SpeedFragment"
    }


    private val days = mutableListOf(ServerSettings.Days.ALL,
                                     ServerSettings.Days.WEEKDAYS,
                                     ServerSettings.Days.WEEKENDS)
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
                Calendar.SUNDAY -> ServerSettings.Days.SUNDAY
                Calendar.MONDAY -> ServerSettings.Days.MONDAY
                Calendar.TUESDAY -> ServerSettings.Days.TUESDAY
                Calendar.WEDNESDAY -> ServerSettings.Days.WEDNESDAY
                Calendar.THURSDAY -> ServerSettings.Days.THURSDAY
                Calendar.FRIDAY -> ServerSettings.Days.FRIDAY
                Calendar.SATURDAY -> ServerSettings.Days.SATURDAY
                else -> 0
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

    override fun onCreateView(inflater: LayoutInflater,
                              container: ViewGroup?,
                              savedInstanceState: Bundle?): View {
        activity?.title = getString(R.string.server_settings_speed)
        return inflater.inflate(R.layout.server_settings_speed_fragment, container, false)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        (activity as ServerSettingsActivity).hideKeyboard()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val limitsFilters = arrayOf(IntFilter(0..(4 * 1024 * 1024 - 1)))

        download_speed_limit_check_box.isChecked = Rpc.serverSettings.downloadSpeedLimited
        download_speed_limit_check_box.setOnCheckedChangeListener { _, checked ->
            download_speed_limit_layout!!.isEnabled = checked
            Rpc.serverSettings.downloadSpeedLimited = checked
        }

        download_speed_limit_layout.isEnabled = download_speed_limit_check_box.isChecked

        download_speed_limit_edit.filters = limitsFilters
        download_speed_limit_edit.setText(Rpc.serverSettings.downloadSpeedLimit.toString())
        download_speed_limit_edit.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable) {
                if (s.isNotEmpty()) {
                    Rpc.serverSettings.downloadSpeedLimit = s.toString().toInt()
                }
            }

            override fun beforeTextChanged(s: CharSequence?,
                                           start: Int,
                                           count: Int,
                                           after: Int) {
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        upload_speed_limit_check_box.isChecked = Rpc.serverSettings.uploadSpeedLimited
        upload_speed_limit_check_box.setOnCheckedChangeListener { _, checked ->
            upload_speed_limit_layout.isEnabled = checked
            Rpc.serverSettings.uploadSpeedLimited = checked
        }

        upload_speed_limit_layout.isEnabled = upload_speed_limit_check_box.isChecked

        upload_speed_limit_edit.filters = limitsFilters
        upload_speed_limit_edit.setText(Rpc.serverSettings.uploadSpeedLimit.toString())
        upload_speed_limit_edit.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable) {
                if (s.isNotEmpty()) {
                    Rpc.serverSettings.uploadSpeedLimit = s.toString().toInt()
                }
            }

            override fun beforeTextChanged(s: CharSequence?,
                                           start: Int,
                                           count: Int,
                                           after: Int) {
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        alternative_limits_check_box.isChecked = Rpc.serverSettings.alternativeSpeedLimitsEnabled
        alternative_limits_check_box.setOnCheckedChangeListener { _, checked ->
            alternative_download_speed_limit_layout!!.isEnabled = checked
            alternative_upload_speed_limit_layout!!.isEnabled = checked
            Rpc.serverSettings.alternativeSpeedLimitsEnabled = checked
        }

        alternative_download_speed_limit_layout.isEnabled = alternative_limits_check_box.isChecked
        alternative_upload_speed_limit_layout.isEnabled = alternative_limits_check_box.isChecked

        alternative_download_speed_limit_edit.filters = limitsFilters
        alternative_download_speed_limit_edit.setText(Rpc.serverSettings.alternativeDownloadSpeedLimit.toString())
        alternative_download_speed_limit_edit.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable) {
                if (s.isNotEmpty()) {
                    Rpc.serverSettings.alternativeDownloadSpeedLimit = s.toString().toInt()
                }
            }

            override fun beforeTextChanged(s: CharSequence?,
                                           start: Int,
                                           count: Int,
                                           after: Int) {
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        alternative_upload_speed_limit_edit.filters = limitsFilters
        alternative_upload_speed_limit_edit.setText(Rpc.serverSettings.alternativeUploadSpeedLimit.toString())
        alternative_upload_speed_limit_edit.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable) {
                if (s.isNotEmpty()) {
                    Rpc.serverSettings.alternativeUploadSpeedLimit = s.toString().toInt()
                }
            }

            override fun beforeTextChanged(s: CharSequence?,
                                           start: Int,
                                           count: Int,
                                           after: Int) {
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        schedule_check_box.isChecked = Rpc.serverSettings.alternativeSpeedLimitScheduled
        schedule_check_box.setOnCheckedChangeListener { _, checked ->
            schedule_layout.setChildrenEnabled(checked)
            Rpc.serverSettings.alternativeSpeedLimitScheduled = checked
        }

        schedule_layout.setChildrenEnabled(schedule_check_box.isChecked)

        begin_time_item.beginTime = true
        begin_time_item.setTime(Rpc.serverSettings.alternativeSpeedLimitsBeginTime)

        end_time_item.beginTime = false
        end_time_item.setTime(Rpc.serverSettings.alternativeSpeedLimitsEndTime)

        days_spinner.adapter = ArraySpinnerAdapter(context!!, daysSpinnerItems.toTypedArray())
        days_spinner.setSelection(days.indexOf(Rpc.serverSettings.alternativeSpeedLimitsDays))
        days_spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?,
                                        view: View?,
                                        position: Int,
                                        id: Long) {
                Rpc.serverSettings.alternativeSpeedLimitsDays = days[position]
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }
}

class TimePickerItem(context: Context, attrs: AttributeSet) : FrameLayout(context, attrs) {
    var beginTime = false
    val calendar = Calendar.getInstance()
    private val titleTextView: View
    private val textView: TextView
    private val format = DateFormat.getTimeInstance(DateFormat.SHORT)

    init {
        inflate(context, R.layout.server_settings_time_picker_item, this)
        titleTextView = findViewById(R.id.title_text_view)
        textView = findViewById(R.id.text_view) as TextView

        val ta = context.theme.obtainStyledAttributes(attrs,
                                                      intArrayOf(android.R.attr.title),
                                                      0,
                                                      0)
        (findViewById(R.id.title_text_view) as TextView).text = ta.getText(0)
        ta.recycle()

        setOnClickListener {
            val fragment = TimePickerFragment()
            val args = Bundle()
            args.putBoolean("beginTime", beginTime)
            args.putInt("hourOfDay", calendar.get(Calendar.HOUR_OF_DAY))
            args.putInt("minute", calendar.get(Calendar.MINUTE))
            fragment.arguments = args
            fragment.show((context as AppCompatActivity).supportFragmentManager, null)
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

    class TimePickerFragment : DialogFragment(), TimePickerDialog.OnTimeSetListener {
        override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
            return TimePickerDialog(activity,
                                    this,
                                    arguments!!.getInt("hourOfDay"),
                                    arguments!!.getInt("minute"),
                                    android.text.format.DateFormat.is24HourFormat(activity))
        }

        override fun onTimeSet(view: TimePicker?, hourOfDay: Int, minute: Int) {
            val speedFragment = fragmentManager!!.findFragmentByTag(SpeedFragment.TAG) as SpeedFragment?
            if (speedFragment != null) {
                if (arguments!!.getBoolean("beginTime")) {
                    speedFragment.begin_time_item.setTime(hourOfDay, minute)
                    Rpc.serverSettings.alternativeSpeedLimitsBeginTime = (hourOfDay * 60) + minute
                } else {
                    speedFragment.end_time_item.setTime(hourOfDay, minute)
                    Rpc.serverSettings.alternativeSpeedLimitsEndTime = (hourOfDay * 60) + minute
                }
            }
        }
    }
}