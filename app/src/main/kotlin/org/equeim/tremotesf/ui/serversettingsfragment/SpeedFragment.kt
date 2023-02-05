// SPDX-FileCopyrightText: 2017-2022 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package org.equeim.tremotesf.ui.serversettingsfragment

import android.content.Context
import android.os.Bundle
import android.text.format.DateFormat
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.LinearLayout
import androidx.annotation.AttrRes
import androidx.core.content.withStyledAttributes
import androidx.fragment.app.Fragment
import androidx.fragment.app.findFragment
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import org.equeim.libtremotesf.ServerSettingsData
import org.equeim.tremotesf.R
import org.equeim.tremotesf.databinding.ServerSettingsSpeedFragmentBinding
import org.equeim.tremotesf.databinding.ServerSettingsTimePickerItemBinding
import org.equeim.tremotesf.rpc.GlobalRpc
import org.equeim.tremotesf.ui.utils.ArrayDropdownAdapter
import org.equeim.tremotesf.ui.utils.IntFilter
import org.equeim.tremotesf.ui.utils.doAfterTextChangedAndNotEmpty
import org.equeim.tremotesf.ui.utils.setDependentViews
import org.threeten.bp.DayOfWeek
import org.threeten.bp.LocalTime
import org.threeten.bp.format.DateTimeFormatter
import org.threeten.bp.format.FormatStyle
import org.threeten.bp.format.TextStyle
import org.threeten.bp.temporal.WeekFields
import timber.log.Timber
import java.util.*


class SpeedFragment : ServerSettingsFragment.BaseFragment(
    R.layout.server_settings_speed_fragment,
    R.string.server_settings_speed
) {
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

        val firstDayOfWeek = WeekFields.of(Locale.getDefault()).firstDayOfWeek
        val daysOfWeek = generateSequence(firstDayOfWeek) {
            val next = it + 1
            if (next != firstDayOfWeek) next else null
        }

        for (day in daysOfWeek) {
            daysSpinnerItems.add(day.getDisplayName(TextStyle.FULL_STANDALONE, Locale.getDefault()))
            days.add(
                when (day) {
                    DayOfWeek.SUNDAY -> ServerSettingsData.AlternativeSpeedLimitsDays.Sunday
                    DayOfWeek.MONDAY -> ServerSettingsData.AlternativeSpeedLimitsDays.Monday
                    DayOfWeek.TUESDAY -> ServerSettingsData.AlternativeSpeedLimitsDays.Tuesday
                    DayOfWeek.WEDNESDAY -> ServerSettingsData.AlternativeSpeedLimitsDays.Wednesday
                    DayOfWeek.THURSDAY -> ServerSettingsData.AlternativeSpeedLimitsDays.Thursday
                    DayOfWeek.FRIDAY -> ServerSettingsData.AlternativeSpeedLimitsDays.Friday
                    DayOfWeek.SATURDAY -> ServerSettingsData.AlternativeSpeedLimitsDays.Saturday
                }
            )
        }
    }

    override fun onViewStateRestored(savedInstanceState: Bundle?) {
        super.onViewStateRestored(savedInstanceState)

        val limitsFilters = arrayOf(IntFilter(0 until 4 * 1024 * 1024))

        with(ServerSettingsSpeedFragmentBinding.bind(requireView())) {
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

            beginTimeItem.apply {
                setTime(GlobalRpc.serverSettings.alternativeSpeedLimitsBeginTime)
                onTimeChangedListener = {
                    GlobalRpc.serverSettings.alternativeSpeedLimitsBeginTime = it
                }
            }

            endTimeItem.apply {
                setTime(GlobalRpc.serverSettings.alternativeSpeedLimitsEndTime)
                onTimeChangedListener = {
                    GlobalRpc.serverSettings.alternativeSpeedLimitsEndTime = it
                }
            }

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
    private val formatter = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
    private var time: LocalTime = LocalTime.MIN

    var onTimeChangedListener: ((LocalTime) -> Unit)? = null

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
            val picker = MaterialTimePicker.Builder()
                .setTimeFormat(if (DateFormat.is24HourFormat(context)) TimeFormat.CLOCK_24H else TimeFormat.CLOCK_12H)
                .setHour(time.hour)
                .setMinute(time.minute)
                .build()
            picker.addOnPositiveButtonClickListener {
                setTime(LocalTime.of(picker.hour, picker.minute))
            }
            picker.show(findFragment<Fragment>().childFragmentManager, null)
        }
    }

    override fun setEnabled(enabled: Boolean) {
        super.setEnabled(enabled)
        with(binding) {
            titleTextView.isEnabled = enabled
            textView.isEnabled = enabled
        }
    }

    fun setTime(time: LocalTime) {
        if (time != this.time) {
            this.time = time
            binding.textView.text = formatter.format(time)
            onTimeChangedListener?.invoke(time)
        }
    }
}
