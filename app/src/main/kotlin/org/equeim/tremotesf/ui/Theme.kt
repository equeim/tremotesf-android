// SPDX-FileCopyrightText: 2017-2025 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package org.equeim.tremotesf.ui

import android.annotation.SuppressLint
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle

private val darkRedColorScheme = darkColorScheme(
    primary = Color(0xFFDF6454),
    onPrimary = Color(0xFF680003),
    primaryContainer = Color(0xFF930007),
    onPrimaryContainer = Color(0xFFFFDAD4),
    inversePrimary = Color(0xFFB91D1D),
    secondary = Color(0xFFDF6454),
    onSecondary = Color(0xFF442A27),
    secondaryContainer = Color(0xFF5D3F3C),
    onSecondaryContainer = Color(0xFFFFDAD5),
    tertiary = Color(0xFFE0C38C),
    onTertiary = Color(0xFF3F2E04),
    tertiaryContainer = Color(0xFF584419),
    onTertiaryContainer = Color(0xFFFEDFA6),

    background = Color(0xFF211A19),
    onBackground = Color(0xFFEDE0DE),

    surface = Color(0xFF211A19),
    onSurface = Color(0xFFEDE0DE),
    surfaceVariant = Color(0xFF534341),
    onSurfaceVariant = Color(0xFFD8C2BF),
    inverseSurface = Color(0xFFEDE0DE),
    inverseOnSurface = Color(0xFF211A19),

    error = Color(0xFFFFB4A9),
    onError = Color(0xFF680003),
    errorContainer = Color(0xFF930006),
    onErrorContainer = Color(0xFFFFDAD4),

    outline = Color(0xFFA08C89),
    outlineVariant = Color(0xFF6F615E),

    surfaceBright = Color(0xFF453E3D),
    surfaceContainer = Color(0xFF312A29),
    surfaceContainerHigh = Color(0xFF37302F),
    surfaceContainerHighest = Color(0xFF3E3736),
    surfaceContainerLow = Color(0xFF2B2423),
    surfaceContainerLowest = Color(0xFF261F1E),
    surfaceDim = Color(0xFF211A19),
)

private val darkTealColorScheme = darkColorScheme(
    primary = Color(0xFF55DBC8),
    onPrimary = Color(0xFF003730),
    primaryContainer = Color(0xFF005046),
    onPrimaryContainer = Color(0xFF75F8E4),
    inversePrimary = Color(0xFF006B5E),
    secondary = Color(0xFFB1CCC6),
    onSecondary = Color(0xFF1C3531),
    secondaryContainer = Color(0xFF334B47),
    onSecondaryContainer = Color(0xFFCCE8E1),
    tertiary = Color(0xFFADCAE5),
    onTertiary = Color(0xFF143349),
    tertiaryContainer = Color(0xFF2D4960),
    onTertiaryContainer = Color(0xFFCAE6FF),

    background = Color(0xFF191C1B),
    onBackground = Color(0xFFE1E3E1),

    surface = Color(0xFF191C1B),
    onSurface = Color(0xFFE1E3E1),
    surfaceVariant = Color(0xFF3F4946),
    onSurfaceVariant = Color(0xFFBFC9C6),
    inverseSurface = Color(0xFFE1E3E1),
    inverseOnSurface = Color(0xFF191C1B),

    error = Color(0xFFFFB4A9),
    errorContainer = Color(0xFF930006),
    onError = Color(0xFF680003),
    onErrorContainer = Color(0xFFFFDAD4),

    outline = Color(0xFF899390),
    outlineVariant = Color(0xFF55625E),

    surfaceBright = Color(0xFF3E4140),
    surfaceContainer = Color(0xFF292C2B),
    surfaceContainerHigh = Color(0xFF2F3231),
    surfaceContainerHighest = Color(0xFF363938),
    surfaceContainerLow = Color(0xFF232625),
    surfaceContainerLowest = Color(0xFF1E2120),
    surfaceDim = Color(0xFF191C1B),
)

private val lightRedColorScheme = lightColorScheme(
    primary = Color(0xFFB91D1D),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFBAAE),
    onPrimaryContainer = Color(0xFF410001),
    inversePrimary = Color(0xFFFFB3A9),
    secondary = Color(0xFFB91D1D),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFFDAD5),
    onSecondaryContainer = Color(0xFF2C1513),
    tertiary = Color(0xFF715B2E),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFEDFA6),
    onTertiaryContainer = Color(0xFF261900),

    background = Color(0xFFFCFCFC),
    onBackground = Color(0xFF211A19),

    surface = Color(0xFFFCFCFC),
    onSurface = Color(0xFF211A19),
    surfaceVariant = Color(0xFFF4DDDA),
    onSurfaceVariant = Color(0xFF534341),
    inverseOnSurface = Color(0xFFFBEEEC),
    inverseSurface = Color(0xFF362F2E),

    error = Color(0xFFBA1B1B),
    errorContainer = Color(0xFFFFDAD4),
    onError = Color(0xFFFFFFFF),
    onErrorContainer = Color(0xFF410001),

    outline = Color(0xFF857371),
    outlineVariant = Color(0xFFD5C3C1),

    surfaceBright = Color(0xFFFCFCFC),
    surfaceContainer = Color(0xFFE7E7E7),
    surfaceContainerHigh = Color(0xFFE0E0E0),
    surfaceContainerHighest = Color(0xFFDADADA),
    surfaceContainerLow = Color(0xFFEDEDED),
    surfaceContainerLowest = Color(0xFFF4F4F4),
    surfaceDim = Color(0xFFD4D4D4),
)

private val lightTealColorScheme = lightColorScheme(
    primary = Color(0xFF0CA592),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF75F8E4),
    onPrimaryContainer = Color(0xFF00201B),
    inversePrimary = Color(0xFF55DBC8),
    secondary = Color(0xFF0CA592),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFCCE8E1),
    onSecondaryContainer = Color(0xFF061F1C),
    tertiary = Color(0xFF456179),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFCAE6FF),
    onTertiaryContainer = Color(0xFF001E31),

    background = Color(0xFFFAFDFA),
    onBackground = Color(0xFF191C1B),

    surface = Color(0xFFFAFDFA),
    onSurface = Color(0xFF191C1B),
    surfaceVariant = Color(0xFFDAE5E1),
    onSurfaceVariant = Color(0xFF3F4946),
    inverseSurface = Color(0xFF2E3130),
    inverseOnSurface = Color(0xFFEFF1EF),

    outline = Color(0xFF6E7976),
    outlineVariant = Color(0xFFBEC9C6),

    error = Color(0xFFBA1B1B),
    errorContainer = Color(0xFFFFDAD4),
    onError = Color(0xFFFFFFFF),
    onErrorContainer = Color(0xFF410001),

    surfaceBright = Color(0xFFFAFDFA),
    surfaceContainer = Color(0xFFE5E8E5),
    surfaceContainerHigh = Color(0xFFDEE1DE),
    surfaceContainerHighest = Color(0xFFD8DBD8),
    surfaceContainerLow = Color(0xFFEBEEEB),
    surfaceContainerLowest = Color(0xFFF2F5F2),
    surfaceDim = Color(0xFFD2D5D2),
)

@Composable
fun ApplicationTheme(
    content: @Composable () -> Unit
) {
    val darkThemeMode: Settings.DarkThemeMode by ActivityThemeProvider.darkThemeMode.collectAsStateWithLifecycle()
    val useDarkColorScheme = when (darkThemeMode) {
        Settings.DarkThemeMode.Auto -> isSystemInDarkTheme()
        Settings.DarkThemeMode.On -> true
        Settings.DarkThemeMode.Off -> false
    }

    val colorTheme: Settings.ColorTheme by ActivityThemeProvider.colorTheme.collectAsStateWithLifecycle()
    val actualColorTheme = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        colorTheme
    } else {
        if (colorTheme == Settings.ColorTheme.System) Settings.ColorTheme.Red else colorTheme
    }

    val colorScheme = when (actualColorTheme) {
        Settings.ColorTheme.System -> {
            val context = LocalContext.current
            @SuppressLint("NewApi")
            if (useDarkColorScheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        Settings.ColorTheme.Red -> if (useDarkColorScheme) darkRedColorScheme else lightRedColorScheme
        Settings.ColorTheme.Teal -> if (useDarkColorScheme) darkTealColorScheme else lightTealColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content,
    )
}

@Composable
fun ScreenPreview(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = darkRedColorScheme) {
        // Screen itself should use either Scaffold or Surface(color = MaterialTheme.colorScheme.background)
        content()
    }
}

@Composable
fun ComponentPreview(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = darkRedColorScheme) {
        Surface(color = MaterialTheme.colorScheme.background, content = content)
    }
}

fun Color.applyDisabledAlpha(enabled: Boolean): Color = if (enabled) this else copy(alpha = 0.38f)
