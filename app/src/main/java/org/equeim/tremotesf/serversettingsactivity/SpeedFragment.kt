/*
 * Copyright (C) 2017 Alexey Rochev <equeim@gmail.com>
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

import android.app.Activity
import android.app.Dialog
import android.app.DialogFragment
import android.app.Fragment
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
import android.widget.CheckBox
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.TimePicker

import org.equeim.tremotesf.R
import org.equeim.tremotesf.Rpc
import org.equeim.tremotesf.ServerSettings
import org.equeim.tremotesf.utils.ArraySpinnerAdapter
import org.equeim.tremotesf.utils.IntFilter
import org.equeim.tremotesf.utils.setChildrenEnabled


class SpeedFragment : Fragment() {
    companion object {
        const val TAG = "org.equeim.tremotesf.ServerSettingsActivity.SpeedFragment"
    }


    private val days = mutableListOf(ServerSettings.Days.ALL,
                                     ServerSettings.Days.WEEKDAYS,
                                     ServerSettings.Days.WEEKENDS)
    private val daysSpinnerItems = mutableListOf<String>()

    private var beginTimeItem: TimePickerItem? = null
    private var endTimeItem: TimePickerItem? = null

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
        activity.title = getString(R.string.server_settings_speed)

        val view = inflater.inflate(R.layout.server_settings_speed_fragment, container, false)

        val limitsFilters = arrayOf(IntFilter(0..(4 * 1024 * 1024 - 1)))

        val downloadSpeedLimitCheckBox = view.findViewById(R.id.download_speed_limit_check_box) as CheckBox
        downloadSpeedLimitCheckBox.isChecked = Rpc.serverSettings.downloadSpeedLimited

        val downloadSpeedLimitLayout = view.findViewById(R.id.download_speed_limit_layout)
        downloadSpeedLimitLayout.isEnabled = downloadSpeedLimitCheckBox.isChecked

        downloadSpeedLimitCheckBox.setOnCheckedChangeListener { checkBox, checked ->
            downloadSpeedLimitLayout!!.isEnabled = checked
            Rpc.serverSettings.downloadSpeedLimited = checked
        }

        val downloadSpeedLimitEdit = view.findViewById(R.id.download_speed_limit_edit) as EditText
        downloadSpeedLimitEdit.filters = limitsFilters
        downloadSpeedLimitEdit.setText(Rpc.serverSettings.downloadSpeedLimit.toString())
        downloadSpeedLimitEdit.addTextChangedListener(object : TextWatcher {
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

        val uploadSpeedLimitCheckBox = view.findViewById(R.id.upload_speed_limit_check_box) as CheckBox
        uploadSpeedLimitCheckBox.isChecked = Rpc.serverSettings.uploadSpeedLimited

        val uploadSpeedLimitLayout = view.findViewById(R.id.upload_speed_limit_layout)
        uploadSpeedLimitLayout.isEnabled = uploadSpeedLimitCheckBox.isChecked

        uploadSpeedLimitCheckBox.setOnCheckedChangeListener { checkBox, checked ->
            uploadSpeedLimitLayout.isEnabled = checked
            Rpc.serverSettings.uploadSpeedLimited = checked
        }

        val uploadSpeedLimitEdit = view.findViewById(R.id.upload_speed_limit_edit) as EditText
        uploadSpeedLimitEdit.filters = limitsFilters
        uploadSpeedLimitEdit.setText(Rpc.serverSettings.uploadSpeedLimit.toString())
        uploadSpeedLimitEdit.addTextChangedListener(object : TextWatcher {
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

        val alternativeLimitsCheckBox = view.findViewById(R.id.alternative_limits_check_box) as CheckBox
        alternativeLimitsCheckBox.isChecked = Rpc.serverSettings.alternativeSpeedLimitsEnabled

        val alternativeDownloadSpeedLimitLayout = view.findViewById(R.id.alternative_download_speed_limit_layout)
        val alternativeUploadSpeedLimitLayout = view.findViewById(R.id.alternative_upload_speed_limit_layout)
        alternativeDownloadSpeedLimitLayout.isEnabled = alternativeLimitsCheckBox.isChecked
        alternativeUploadSpeedLimitLayout.isEnabled = alternativeLimitsCheckBox.isChecked

        alternativeLimitsCheckBox.setOnCheckedChangeListener { checkBox, checked ->
            alternativeDownloadSpeedLimitLayout!!.isEnabled = checked
            alternativeUploadSpeedLimitLayout!!.isEnabled = checked
            Rpc.serverSettings.alternativeSpeedLimitsEnabled = checked
        }

        val alternativeDownloadSpeedLimitEdit = view.findViewById(R.id.alternative_download_speed_limit_edit) as EditText
        alternativeDownloadSpeedLimitEdit.filters = limitsFilters
        alternativeDownloadSpeedLimitEdit.setText(Rpc.serverSettings.alternativeDownloadSpeedLimit.toString())
        alternativeDownloadSpeedLimitEdit.addTextChangedListener(object : TextWatcher {
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

        val alternativeUploadSpeedLimitEdit = view.findViewById(R.id.alternative_upload_speed_limit_edit) as EditText
        alternativeUploadSpeedLimitEdit.filters = limitsFilters
        alternativeUploadSpeedLimitEdit.setText(Rpc.serverSettings.alternativeUploadSpeedLimit.toString())
        alternativeUploadSpeedLimitEdit.addTextChangedListener(object : TextWatcher {
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

        val scheduleCheckBox = view.findViewById(R.id.schedule_check_box) as CheckBox
        scheduleCheckBox.isChecked = Rpc.serverSettings.alternativeSpeedLimitScheduled

        val scheduleLayout = view.findViewById(R.id.schedule_layout) as ViewGroup
        scheduleLayout.setChildrenEnabled(scheduleCheckBox.isChecked)

        scheduleCheckBox.setOnCheckedChangeListener { checkBox, checked ->
            scheduleLayout.setChildrenEnabled(checked)
            Rpc.serverSettings.alternativeSpeedLimitScheduled = checked
        }

        beginTimeItem = view.findViewById(R.id.begin_time_item) as TimePickerItem
        beginTimeItem!!.beginTime = true
        beginTimeItem!!.setTime(Rpc.serverSettings.alternativeSpeedLimitsBeginTime)

        endTimeItem = view.findViewById(R.id.end_time_item) as TimePickerItem
        endTimeItem!!.beginTime = false
        endTimeItem!!.setTime(Rpc.serverSettings.alternativeSpeedLimitsEndTime)

        val daysSpinner = view.findViewById(R.id.days_spinner) as Spinner
        daysSpinner.adapter = ArraySpinnerAdapter(activity, daysSpinnerItems.toTypedArray())
        daysSpinner.setSelection(days.indexOf(Rpc.serverSettings.alternativeSpeedLimitsDays))
        daysSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?,
                                        view: View?,
                                        position: Int,
                                        id: Long) {
                Rpc.serverSettings.alternativeSpeedLimitsDays = days[position]
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        return view
    }

    override fun onDestroyView() {
        super.onDestroyView()
        beginTimeItem = null
        endTimeItem = null
        (activity as ServerSettingsActivity).hideKeyboard()
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
                fragment.show((context as Activity).fragmentManager, null)
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
            calendar.set(Calendar.MINUTE, minutesFromStartOfDay.mod(60))
            textView.text = format.format(calendar.time)
        }

        class TimePickerFragment : DialogFragment(), TimePickerDialog.OnTimeSetListener {
            override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
                return TimePickerDialog(activity,
                                        this,
                                        arguments.getInt("hourOfDay"),
                                        arguments.getInt("minute"),
                                        android.text.format.DateFormat.is24HourFormat(activity))
            }

            override fun onTimeSet(view: TimePicker?, hourOfDay: Int, minute: Int) {
                val speedFragment = fragmentManager.findFragmentByTag(SpeedFragment.TAG) as SpeedFragment?
                if (speedFragment != null) {
                    if (arguments.getBoolean("beginTime")) {
                        speedFragment.beginTimeItem?.setTime(hourOfDay, minute)
                        Rpc.serverSettings.alternativeSpeedLimitsBeginTime = (hourOfDay * 60) + minute
                    } else {
                        speedFragment.endTimeItem?.setTime(hourOfDay, minute)
                        Rpc.serverSettings.alternativeSpeedLimitsEndTime = (hourOfDay * 60) + minute
                    }
                }
            }
        }
    }
}