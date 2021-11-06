/*
 * Copyright (C) 2017-2020 Alexey Rochev <equeim@gmail.com>
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

package org.equeim.tremotesf.ui.serversettingsfragment

import android.app.Dialog
import android.app.TimePickerDialog
import android.content.Context
import android.os.Bundle
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.LinearLayout
import android.widget.TimePicker
import androidx.annotation.AttrRes
import androidx.core.content.withStyledAttributes
import androidx.navigation.findNavController
import androidx.navigation.fragment.navArgs
import org.equeim.libtremotesf.ServerSettingsData
import org.equeim.tremotesf.R
import org.equeim.tremotesf.databinding.ServerSettingsSpeedFragmentBinding
import org.equeim.tremotesf.databinding.ServerSettingsTimePickerItemBinding
import org.equeim.tremotesf.rpc.GlobalRpc
import org.equeim.tremotesf.ui.NavigationDialogFragment
import org.equeim.tremotesf.ui.utils.ArrayDropdownAdapter
import org.equeim.tremotesf.ui.utils.IntFilter
import org.equeim.tremotesf.ui.utils.doAfterTextChangedAndNotEmpty
import org.equeim.tremotesf.ui.utils.safeNavigate
import org.equeim.tremotesf.ui.utils.setDependentViews
import org.equeim.tremotesf.ui.utils.viewBinding
import timber.log.Timber
import java.text.DateFormat
import java.text.DateFormatSymbols
import java.util.Calendar


class SpeedFragment : ServerSettingsFragment.BaseFragment(
    R.layout.server_settings_speed_fragment,
    R.string.server_settings_speed
) {
    val binding by viewBinding(ServerSettingsSpeedFragmentBinding::bind)

    private val days = mutableListOf(
        ServerSettingsData.AlternativeSpeedLimitsDays.All,
        ServerSettingsData.AlternativeSpeedLimitsDays.Weekdays,
        ServerSettingsData.AlternativeSpeedLimitsDays.Weekends
    )
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

    override fun onViewStateRestored(savedInstanceState: Bundle?) {
        super.onViewStateRestored(savedInstanceState)

        val limitsFilters = arrayOf(IntFilter(0 until 4 * 1024 * 1024))

        with(binding) {
            downloadSpeedLimitCheckBox.isChecked = GlobalRpc.serverSettings.downloadSpeedLimited
            downloadSpeedLimitCheckBox.setDependentViews(downloadSpeedLimitLayout) { checked ->
                GlobalRpc.serverSettings.downloadSpeedLimited = checked
            }

            downloadSpeedLimitEdit.filters = limitsFilters
            downloadSpeedLimitEdit.setText(GlobalRpc.serverSettings.downloadSpeedLimit.toString())
            downloadSpeedLimitEdit.doAfterTextChangedAndNotEmpty {
                try {
                    GlobalRpc.serverSettings.downloadSpeedLimit = it.toString().toInt()
                } catch (e: NumberFormatException) {
                    Timber.e(e, "Failed to parse download speed limit $it")
                }
            }

            uploadSpeedLimitCheckBox.isChecked = GlobalRpc.serverSettings.uploadSpeedLimited
            uploadSpeedLimitCheckBox.setDependentViews(uploadSpeedLimitLayout) { checked ->
                GlobalRpc.serverSettings.uploadSpeedLimited = checked
            }

            uploadSpeedLimitEdit.filters = limitsFilters
            uploadSpeedLimitEdit.setText(GlobalRpc.serverSettings.uploadSpeedLimit.toString())
            uploadSpeedLimitEdit.doAfterTextChangedAndNotEmpty {
                try {
                    GlobalRpc.serverSettings.uploadSpeedLimit = it.toString().toInt()
                } catch (e: NumberFormatException) {
                    Timber.e(e, "Failed to parse upload speed limit $it")
                }
            }

            alternativeLimitsCheckBox.isChecked = GlobalRpc.serverSettings.alternativeSpeedLimitsEnabled
            alternativeLimitsCheckBox.setDependentViews(
                alternativeDownloadSpeedLimitLayout,
                alternativeUploadSpeedLimitLayout
            ) { checked ->
                GlobalRpc.serverSettings.alternativeSpeedLimitsEnabled = checked
            }

            alternativeDownloadSpeedLimitEdit.filters = limitsFilters
            alternativeDownloadSpeedLimitEdit.setText(GlobalRpc.serverSettings.alternativeDownloadSpeedLimit.toString())
            alternativeDownloadSpeedLimitEdit.doAfterTextChangedAndNotEmpty {
                try {
                    GlobalRpc.serverSettings.alternativeDownloadSpeedLimit = it.toString().toInt()
                } catch (e: NumberFormatException) {
                    Timber.e(e, "Failed to parse alternative download speed limit $it")
                }
            }

            alternativeUploadSpeedLimitEdit.filters = limitsFilters
            alternativeUploadSpeedLimitEdit.setText(GlobalRpc.serverSettings.alternativeUploadSpeedLimit.toString())
            alternativeUploadSpeedLimitEdit.doAfterTextChangedAndNotEmpty {
                try {
                    GlobalRpc.serverSettings.alternativeUploadSpeedLimit = it.toString().toInt()
                } catch (e: NumberFormatException) {
                    Timber.e(e, "Failed to parse alternative upload speed limit $it")
                }
            }

            scheduleCheckBox.isChecked = GlobalRpc.serverSettings.alternativeSpeedLimitsScheduled
            scheduleCheckBox.setDependentViews(
                beginTimeItem,
                endTimeItem,
                daysViewLayout
            ) { checked ->
                GlobalRpc.serverSettings.alternativeSpeedLimitsScheduled = checked
            }

            beginTimeItem.beginTime = true
            beginTimeItem.setTime(GlobalRpc.serverSettings.alternativeSpeedLimitsBeginTime)

            endTimeItem.beginTime = false
            endTimeItem.setTime(GlobalRpc.serverSettings.alternativeSpeedLimitsEndTime)

            daysView.setAdapter(ArrayDropdownAdapter(daysSpinnerItems))
            daysView.setText(
                daysView.adapter.getItem(days.indexOf(GlobalRpc.serverSettings.alternativeSpeedLimitsDays))
                    .toString()
            )
            daysView.setOnItemClickListener { _, _, position, _ ->
                GlobalRpc.serverSettings.alternativeSpeedLimitsDays = days[position]
            }
        }
    }
}

class TimePickerItem @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    @AttrRes defStyleAttr: Int = R.attr.timePickerItemStyle
) : LinearLayout(context, attrs, defStyleAttr) {

    var beginTime = false
    private val calendar: Calendar = Calendar.getInstance()
    private val format = DateFormat.getTimeInstance(DateFormat.SHORT)

    private val binding =
        ServerSettingsTimePickerItemBinding.inflate(LayoutInflater.from(context), this)

    init {
        context.withStyledAttributes(
            attrs,
            R.styleable.TimePickerItem,
            0,
            R.style.Widget_Tremotesf_TimePickerItem
        ) {
            binding.titleTextView.text = getText(R.styleable.TimePickerItem_android_title)
        }

        setOnClickListener {
            findNavController().safeNavigate(
                SpeedFragmentDirections.toTimePickerDialog(
                    beginTime,
                    calendar.get(Calendar.HOUR_OF_DAY),
                    calendar.get(Calendar.MINUTE)
                )
            )
        }
    }

    override fun setEnabled(enabled: Boolean) {
        super.setEnabled(enabled)
        with(binding) {
            titleTextView.isEnabled = enabled
            textView.isEnabled = enabled
        }
    }

    fun setTime(hourOfDay: Int, minute: Int) {
        calendar.set(Calendar.HOUR_OF_DAY, hourOfDay)
        calendar.set(Calendar.MINUTE, minute)
        binding.textView.text = format.format(calendar.time)
    }

    fun setTime(minutesFromStartOfDay: Int) {
        calendar.set(Calendar.HOUR_OF_DAY, minutesFromStartOfDay / 60)
        calendar.set(Calendar.MINUTE, minutesFromStartOfDay.rem(60))
        binding.textView.text = format.format(calendar.time)
    }
}

class SpeedTimePickerFragment : NavigationDialogFragment(), TimePickerDialog.OnTimeSetListener {
    private val args: SpeedTimePickerFragmentArgs by navArgs()

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return TimePickerDialog(
            activity,
            this,
            args.hourOfDay,
            args.minute,
            android.text.format.DateFormat.is24HourFormat(activity)
        )
    }

    override fun onTimeSet(view: TimePicker?, hourOfDay: Int, minute: Int) {
        val speedFragment = parentFragmentManager.primaryNavigationFragment as? SpeedFragment
        if (speedFragment != null) {
            if (args.beginTime) {
                speedFragment.binding.beginTimeItem.setTime(hourOfDay, minute)
                GlobalRpc.serverSettings.alternativeSpeedLimitsBeginTime = (hourOfDay * 60) + minute
            } else {
                speedFragment.binding.endTimeItem.setTime(hourOfDay, minute)
                GlobalRpc.serverSettings.alternativeSpeedLimitsEndTime = (hourOfDay * 60) + minute
            }
        }
    }
}
