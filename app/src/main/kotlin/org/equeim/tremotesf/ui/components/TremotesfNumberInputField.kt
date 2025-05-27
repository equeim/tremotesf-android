// SPDX-FileCopyrightText: 2017-2025 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package org.equeim.tremotesf.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import org.equeim.tremotesf.R
import org.equeim.tremotesf.ui.components.TremotesfNumberInputFieldError.OutOfRange
import org.equeim.tremotesf.ui.components.TremotesfNumberInputFieldError.ParsingFailure
import java.text.NumberFormat
import java.text.ParsePosition

@Composable
fun TremotesfNumberInputField(
    state: TremotesfIntegerNumberInputFieldState,
    range: LongRange,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    @StringRes label: Int = 0,
    @StringRes suffix: Int = 0,
    imeAction: ImeAction = ImeAction.Unspecified,
) {
    val format = remember(LocalConfiguration.current.locales) { createIntegerParseNumberFormat() }
    val error = state.error
    OutlinedTextField(
        value = state.textFieldValue,
        onValueChange = {
            val number = format.parseOrNull(it)
            when (number) {
                null -> state.update(it, ParsingFailure)
                !is Long, !in range -> state.update(it, OutOfRange)
                else -> state.update(it, number)
            }
        },
        modifier = modifier,
        enabled = enabled,
        label = label.takeIf { it != 0 }?.let { { Text(stringResource(it)) } },
        suffix = suffix.takeIf { it != 0 }?.let { { Text(stringResource(it)) } },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = imeAction),
        singleLine = true,
        supportingText = if (error != null) {
            {
                Text(
                    when (error) {
                        ParsingFailure ->
                            stringResource(R.string.number_parse_error_integer)

                        OutOfRange -> stringResource(
                            R.string.number_range_error,
                            range.start,
                            range.endInclusive
                        )
                    }
                )
            }
        } else {
            null
        },
        isError = error != null,
    )
}

@Composable
fun rememberTremotesfIntegerNumberInputFieldState(
    initialNumberValue: Long = 0,
    onNumberValueChange: ((Long) -> Unit)? = null,
): TremotesfIntegerNumberInputFieldState {
    return rememberSaveable(
        saver = TremotesfIntegerNumberInputFieldState.Saver(onNumberValueChange)
    ) {
        TremotesfIntegerNumberInputFieldState(
            initialNumberValue = initialNumberValue,
            onNumberValueChange = onNumberValueChange,
        )
    }
}

@Stable
class TremotesfIntegerNumberInputFieldState(
    initialNumberValue: Long = 0,
    savedTextFieldValue: String? = null,
    savedError: TremotesfNumberInputFieldError? = null,
    private val onNumberValueChange: ((Long) -> Unit)? = null,
) {
    var numberValue: Long = initialNumberValue
        private set

    var textFieldValue: String by mutableStateOf(
        savedTextFieldValue ?: createIntegerDisplayNumberFormat().format(initialNumberValue)
    )
        private set

    var error: TremotesfNumberInputFieldError? by mutableStateOf(savedError)
        private set

    fun update(newTextFieldValue: String, newNumberValue: Long) {
        textFieldValue = newTextFieldValue
        error = null
        if (newNumberValue != numberValue) {
            numberValue = newNumberValue
            onNumberValueChange?.invoke(newNumberValue)
        }
    }

    fun update(newTextFieldValue: String, newError: TremotesfNumberInputFieldError) {
        textFieldValue = newTextFieldValue
        error = newError
    }

    fun reset(initialNumberValue: Long) {
        numberValue = initialNumberValue
        textFieldValue = createIntegerDisplayNumberFormat().format(initialNumberValue)
        error = null
    }

    companion object {
        fun Saver(onNumberValueChange: ((Long) -> Unit)? = null): Saver<TremotesfIntegerNumberInputFieldState, *> =
            listSaver(
                save = { listOf(it.numberValue, it.textFieldValue, it.error) },
                restore = {
                    TremotesfIntegerNumberInputFieldState(
                        initialNumberValue = it[0] as Long,
                        savedTextFieldValue = it[1] as String,
                        savedError = it[2] as TremotesfNumberInputFieldError?,
                        onNumberValueChange = onNumberValueChange
                    )
                }
            )
    }
}

@Composable
fun TremotesfNumberInputField(
    state: TremotesfDecimalNumberInputFieldState,
    range: ClosedFloatingPointRange<Double>,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    @StringRes label: Int = 0,
    @StringRes suffix: Int = 0,
) {
    val format = remember(LocalConfiguration.current.locales) { createDecimalParseNumberFormat() }
    val error = state.error
    OutlinedTextField(
        value = state.textFieldValue,
        onValueChange = {
            val number = format.parseOrNull(it)
            if (number != null) {
                val double = number.toDouble()
                if (double in range) {
                    state.update(it, double)
                } else {
                    state.update(it, OutOfRange)
                }
            } else {
                state.update(it, ParsingFailure)
            }
        },
        modifier = modifier,
        enabled = enabled,
        label = label.takeIf { it != 0 }?.let { { Text(stringResource(it)) } },
        suffix = suffix.takeIf { it != 0 }?.let { { Text(stringResource(it)) } },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        supportingText = if (error != null) {
            {
                Text(
                    when (error) {
                        ParsingFailure ->
                            stringResource(R.string.number_parse_error_float)

                        OutOfRange -> stringResource(
                            R.string.number_range_error_float,
                            range.start,
                            range.endInclusive
                        )
                    }
                )
            }
        } else {
            null
        },
        isError = error != null,
    )
}

@Composable
fun rememberTremotesfDecimalNumberInputFieldState(
    initialNumberValue: Double = 0.0,
    onNumberValueChange: ((Double) -> Unit)? = null,
): TremotesfDecimalNumberInputFieldState {
    return rememberSaveable(
        saver = TremotesfDecimalNumberInputFieldState.Saver(onNumberValueChange)
    ) {
        TremotesfDecimalNumberInputFieldState(
            initialNumberValue = initialNumberValue,
            onNumberValueChange = onNumberValueChange
        )
    }
}

@Stable
class TremotesfDecimalNumberInputFieldState(
    initialNumberValue: Double = 0.0,
    savedTextFieldValue: String? = null,
    savedError: TremotesfNumberInputFieldError? = null,
    private val onNumberValueChange: ((Double) -> Unit)? = null,
) {
    var numberValue: Double = initialNumberValue
        private set

    var textFieldValue: String by mutableStateOf(
        savedTextFieldValue ?: createDecimalDisplayNumberFormat().format(initialNumberValue)
    )
        private set

    var error: TremotesfNumberInputFieldError? by mutableStateOf(savedError)
        private set

    fun update(newTextFieldValue: String, newNumberValue: Double) {
        textFieldValue = newTextFieldValue
        error = null
        if (newNumberValue != numberValue) {
            numberValue = newNumberValue
            onNumberValueChange?.invoke(newNumberValue)
        }
    }

    fun update(newTextFieldValue: String, newError: TremotesfNumberInputFieldError) {
        textFieldValue = newTextFieldValue
        error = newError
    }

    fun reset(initialNumberValue: Double) {
        numberValue = initialNumberValue
        textFieldValue = createDecimalDisplayNumberFormat().format(initialNumberValue)
        error = null
    }

    companion object {
        fun Saver(onNumberValueChange: ((Double) -> Unit)? = null): Saver<TremotesfDecimalNumberInputFieldState, *> =
            listSaver(
                save = { listOf(it.numberValue, it.textFieldValue, it.error) },
                restore = {
                    TremotesfDecimalNumberInputFieldState(
                        initialNumberValue = it[0] as Double,
                        savedTextFieldValue = it[1] as String,
                        savedError = it[2] as TremotesfNumberInputFieldError?,
                        onNumberValueChange = onNumberValueChange
                    )
                }
            )
    }
}

enum class TremotesfNumberInputFieldError {
    ParsingFailure,
    OutOfRange
}

private fun createIntegerDisplayNumberFormat(): NumberFormat = NumberFormat.getInstance().apply {
    isGroupingUsed = false
}

private fun createIntegerParseNumberFormat(): NumberFormat = NumberFormat.getInstance().apply {
    isParseIntegerOnly = true
}

private fun createDecimalDisplayNumberFormat(): NumberFormat = NumberFormat.getInstance().apply {
    isGroupingUsed = false
    maximumFractionDigits = 16
}

private fun createDecimalParseNumberFormat(): NumberFormat = NumberFormat.getInstance()

private fun NumberFormat.parseOrNull(string: String): Number? {
    if (string.isEmpty()) return 0L
    val position = ParsePosition(0)
    val parsed = parse(string, position)
    return if (parsed != null && position.index == string.length) {
        parsed
    } else {
        null
    }
}

val NON_NEGATIVE_INTEGERS_RANGE: LongRange = 0L..Long.MAX_VALUE
val NON_NEGATIVE_DECIMALS_RANGE: ClosedFloatingPointRange<Double> = 0.0..Double.MAX_VALUE
val UNSIGNED_16BIT_RANGE: LongRange = 0L..UShort.MAX_VALUE.toLong()
