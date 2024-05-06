// SPDX-FileCopyrightText: 2017-2024 Alexey Rochev <equeim@gmail.com>
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
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.findFragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.onEach
import org.equeim.tremotesf.R
import org.equeim.tremotesf.databinding.ServerSettingsSpeedFragmentBinding
import org.equeim.tremotesf.databinding.ServerSettingsTimePickerItemBinding
import org.equeim.tremotesf.rpc.GlobalRpcClient
import org.equeim.tremotesf.rpc.RpcClient
import org.equeim.tremotesf.rpc.RpcRequestError
import org.equeim.tremotesf.rpc.RpcRequestState
import org.equeim.tremotesf.rpc.performRecoveringRequest
import org.equeim.tremotesf.rpc.requests.TransferRate
import org.equeim.tremotesf.rpc.requests.serversettings.SpeedServerSettings
import org.equeim.tremotesf.rpc.requests.serversettings.SpeedServerSettings.AlternativeLimitsDays
import org.equeim.tremotesf.rpc.requests.serversettings.getSpeedServerSettings
import org.equeim.tremotesf.rpc.requests.serversettings.setAlternativeDownloadSpeedLimit
import org.equeim.tremotesf.rpc.requests.serversettings.setAlternativeLimitsBeginTime
import org.equeim.tremotesf.rpc.requests.serversettings.setAlternativeLimitsDays
import org.equeim.tremotesf.rpc.requests.serversettings.setAlternativeLimitsEnabled
import org.equeim.tremotesf.rpc.requests.serversettings.setAlternativeLimitsEndTime
import org.equeim.tremotesf.rpc.requests.serversettings.setAlternativeLimitsScheduled
import org.equeim.tremotesf.rpc.requests.serversettings.setAlternativeUploadSpeedLimit
import org.equeim.tremotesf.rpc.requests.serversettings.setDownloadSpeedLimit
import org.equeim.tremotesf.rpc.requests.serversettings.setDownloadSpeedLimited
import org.equeim.tremotesf.rpc.requests.serversettings.setUploadSpeedLimit
import org.equeim.tremotesf.rpc.requests.serversettings.setUploadSpeedLimited
import org.equeim.tremotesf.rpc.stateIn
import org.equeim.tremotesf.ui.NavigationFragment
import org.equeim.tremotesf.ui.utils.ArrayDropdownAdapter
import org.equeim.tremotesf.ui.utils.handleNumberRangeError
import org.equeim.tremotesf.ui.utils.hide
import org.equeim.tremotesf.ui.utils.hideKeyboard
import org.equeim.tremotesf.ui.utils.launchAndCollectWhenStarted
import org.equeim.tremotesf.ui.utils.setDependentViews
import org.equeim.tremotesf.ui.utils.showError
import org.equeim.tremotesf.ui.utils.showLoading
import org.equeim.tremotesf.ui.utils.viewLifecycleObject
import timber.log.Timber
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Locale


class SpeedFragment : NavigationFragment(
    R.layout.server_settings_speed_fragment,
    R.string.server_settings_speed
) {
    private lateinit var daysSpinnerItems: List<Pair<String, AlternativeLimitsDays>>

    private val model by viewModels<SpeedFragmentViewModel>()
    private val binding by viewLifecycleObject(ServerSettingsSpeedFragmentBinding::bind)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val daysSpinnerItems = mutableListOf<Pair<String, AlternativeLimitsDays>>()
        daysSpinnerItems.add(getString(R.string.every_day) to AlternativeLimitsDays.All)
        daysSpinnerItems.add(getString(R.string.weekdays) to AlternativeLimitsDays.Weekdays)
        daysSpinnerItems.add(getString(R.string.weekends) to AlternativeLimitsDays.Weekends)

        val firstDayOfWeek =
            DayOfWeek.of(WeekFields.of(Locale.getDefault()).firstDayOfWeek.value)
        val daysOfWeek = generateSequence(firstDayOfWeek) { it + 1 }.take(DayOfWeek.entries.size)
        for (day in daysOfWeek) {
            daysSpinnerItems.add(
                day.getDisplayName(TextStyle.FULL_STANDALONE, Locale.getDefault()) to day.toAlternativeSpeedLimitsDays()
            )
        }

        this.daysSpinnerItems = daysSpinnerItems
    }

    override fun onViewStateRestored(savedInstanceState: Bundle?) {
        super.onViewStateRestored(savedInstanceState)

        val speedLimitRange = 0..(4 * 1024 * 1024)

        with(binding) {
            downloadSpeedLimitCheckBox.setDependentViews(downloadSpeedLimitLayout) { checked ->
                onValueChanged { setDownloadSpeedLimited(checked) }
            }

            downloadSpeedLimitEdit.handleNumberRangeError(speedLimitRange) { limit ->
                onValueChanged { setDownloadSpeedLimit(TransferRate.fromKiloBytesPerSecond(limit.toLong())) }
            }

            uploadSpeedLimitCheckBox.setDependentViews(uploadSpeedLimitLayout) { checked ->
                onValueChanged { setUploadSpeedLimited(checked) }
            }

            uploadSpeedLimitEdit.handleNumberRangeError(speedLimitRange) { limit ->
                onValueChanged { setUploadSpeedLimit(TransferRate.fromKiloBytesPerSecond(limit.toLong())) }
            }

            alternativeLimitsCheckBox.setDependentViews(
                alternativeDownloadSpeedLimitLayout,
                alternativeUploadSpeedLimitLayout
            ) { checked ->
                onValueChanged { setAlternativeLimitsEnabled(checked) }
            }

            alternativeDownloadSpeedLimitEdit.handleNumberRangeError(speedLimitRange) { limit ->
                onValueChanged { setAlternativeDownloadSpeedLimit(TransferRate.fromKiloBytesPerSecond(limit.toLong())) }
            }

            alternativeUploadSpeedLimitEdit.handleNumberRangeError(speedLimitRange) { limit ->
                onValueChanged { setAlternativeUploadSpeedLimit(TransferRate.fromKiloBytesPerSecond(limit.toLong())) }
            }

            scheduleCheckBox.setDependentViews(
                beginTimeItem,
                endTimeItem,
                daysViewLayout
            ) { checked ->
                onValueChanged { setAlternativeLimitsScheduled(checked) }
            }

            beginTimeItem.apply {
                onTimeChangedListener = {
                    onValueChanged {
                        setAlternativeLimitsBeginTime(it)
                    }
                }
            }

            endTimeItem.apply {
                onTimeChangedListener = {
                    onValueChanged {
                        setAlternativeLimitsEndTime(it)
                    }
                }
            }

            daysView.setAdapter(ArrayDropdownAdapter(daysSpinnerItems.map { it.first }))
            daysView.setOnItemClickListener { _, _, position, _ ->
                onValueChanged { setAlternativeLimitsDays(daysSpinnerItems[position].second) }
            }
        }

        model.settings.launchAndCollectWhenStarted(viewLifecycleOwner) {
            Timber.d("Settings are $it")
            when (it) {
                is RpcRequestState.Loaded -> showSettings(it.response)
                is RpcRequestState.Loading -> showPlaceholder(null)
                is RpcRequestState.Error -> showPlaceholder(it.error)
            }
        }
    }

    private fun showPlaceholder(error: RpcRequestError?) {
        hideKeyboard()
        with(binding) {
            scrollView.isVisible = false
            error?.let(placeholderView::showError) ?: placeholderView.showLoading()
        }
    }

    private fun showSettings(settings: SpeedServerSettings) {
        with(binding) {
            scrollView.isVisible = true
            placeholderView.hide()
        }
        if (model.shouldSetInitialState) {
            updateViews(settings)
            model.shouldSetInitialState = false
        }
    }

    private fun updateViews(settings: SpeedServerSettings) = with(binding) {
        downloadSpeedLimitCheckBox.isChecked = settings.downloadSpeedLimited
        downloadSpeedLimitEdit.setText(settings.downloadSpeedLimit.kiloBytesPerSecond.toString())
        uploadSpeedLimitCheckBox.isChecked = settings.uploadSpeedLimited
        uploadSpeedLimitEdit.setText(settings.uploadSpeedLimit.kiloBytesPerSecond.toString())
        alternativeLimitsCheckBox.isChecked = settings.alternativeLimitsEnabled
        alternativeDownloadSpeedLimitEdit.setText(settings.alternativeDownloadSpeedLimit.kiloBytesPerSecond.toString())
        alternativeUploadSpeedLimitEdit.setText(settings.alternativeUploadSpeedLimit.kiloBytesPerSecond.toString())
        scheduleCheckBox.isChecked = settings.alternativeLimitsScheduled
        beginTimeItem.setTime(settings.alternativeLimitsBeginTime)
        endTimeItem.setTime(settings.alternativeLimitsEndTime)
        daysView.setText(
            daysView.adapter.getItem(daysSpinnerItems.indexOfFirst { it.second == settings.alternativeLimitsDays })
                .toString()
        )
    }

    private fun onValueChanged(performRpcRequest: suspend RpcClient.() -> Unit) {
        if (!model.shouldSetInitialState) {
            GlobalRpcClient.performBackgroundRpcRequest(R.string.set_server_settings_error, performRpcRequest)
        }
    }
}

class TimePickerItem @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    @AttrRes defStyleAttr: Int = R.attr.timePickerItemStyle,
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

class SpeedFragmentViewModel : ViewModel() {
    var shouldSetInitialState = true
    val settings: StateFlow<RpcRequestState<SpeedServerSettings>> =
        GlobalRpcClient.performRecoveringRequest { getSpeedServerSettings() }
            .onEach { if (it !is RpcRequestState.Loaded) shouldSetInitialState = true }
            .stateIn(GlobalRpcClient, viewModelScope)
}

private fun DayOfWeek.toAlternativeSpeedLimitsDays(): AlternativeLimitsDays = when (this) {
    DayOfWeek.SUNDAY -> AlternativeLimitsDays.Sunday
    DayOfWeek.MONDAY -> AlternativeLimitsDays.Monday
    DayOfWeek.TUESDAY -> AlternativeLimitsDays.Tuesday
    DayOfWeek.WEDNESDAY -> AlternativeLimitsDays.Wednesday
    DayOfWeek.THURSDAY -> AlternativeLimitsDays.Thursday
    DayOfWeek.FRIDAY -> AlternativeLimitsDays.Friday
    DayOfWeek.SATURDAY -> AlternativeLimitsDays.Saturday
}
