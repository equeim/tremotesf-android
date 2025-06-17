// SPDX-FileCopyrightText: 2025 The Android Open Source Project
//
// SPDX-License-Identifier: Apache-2.0

package org.equeim.tremotesf.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.materialIcon
import androidx.compose.material.icons.materialPath
import androidx.compose.ui.graphics.vector.ImageVector

val Icons.Filled.SelectAll: ImageVector
    get() {
        if (_selectAll != null) {
            return _selectAll!!
        }
        _selectAll = materialIcon(name = "Filled.SelectAll") {
            materialPath {
                moveTo(3.0f, 5.0f)
                horizontalLineToRelative(2.0f)
                lineTo(5.0f, 3.0f)
                curveToRelative(-1.1f, 0.0f, -2.0f, 0.9f, -2.0f, 2.0f)
                close()
                moveTo(3.0f, 13.0f)
                horizontalLineToRelative(2.0f)
                verticalLineToRelative(-2.0f)
                lineTo(3.0f, 11.0f)
                verticalLineToRelative(2.0f)
                close()
                moveTo(7.0f, 21.0f)
                horizontalLineToRelative(2.0f)
                verticalLineToRelative(-2.0f)
                lineTo(7.0f, 19.0f)
                verticalLineToRelative(2.0f)
                close()
                moveTo(3.0f, 9.0f)
                horizontalLineToRelative(2.0f)
                lineTo(5.0f, 7.0f)
                lineTo(3.0f, 7.0f)
                verticalLineToRelative(2.0f)
                close()
                moveTo(13.0f, 3.0f)
                horizontalLineToRelative(-2.0f)
                verticalLineToRelative(2.0f)
                horizontalLineToRelative(2.0f)
                lineTo(13.0f, 3.0f)
                close()
                moveTo(19.0f, 3.0f)
                verticalLineToRelative(2.0f)
                horizontalLineToRelative(2.0f)
                curveToRelative(0.0f, -1.1f, -0.9f, -2.0f, -2.0f, -2.0f)
                close()
                moveTo(5.0f, 21.0f)
                verticalLineToRelative(-2.0f)
                lineTo(3.0f, 19.0f)
                curveToRelative(0.0f, 1.1f, 0.9f, 2.0f, 2.0f, 2.0f)
                close()
                moveTo(3.0f, 17.0f)
                horizontalLineToRelative(2.0f)
                verticalLineToRelative(-2.0f)
                lineTo(3.0f, 15.0f)
                verticalLineToRelative(2.0f)
                close()
                moveTo(9.0f, 3.0f)
                lineTo(7.0f, 3.0f)
                verticalLineToRelative(2.0f)
                horizontalLineToRelative(2.0f)
                lineTo(9.0f, 3.0f)
                close()
                moveTo(11.0f, 21.0f)
                horizontalLineToRelative(2.0f)
                verticalLineToRelative(-2.0f)
                horizontalLineToRelative(-2.0f)
                verticalLineToRelative(2.0f)
                close()
                moveTo(19.0f, 13.0f)
                horizontalLineToRelative(2.0f)
                verticalLineToRelative(-2.0f)
                horizontalLineToRelative(-2.0f)
                verticalLineToRelative(2.0f)
                close()
                moveTo(19.0f, 21.0f)
                curveToRelative(1.1f, 0.0f, 2.0f, -0.9f, 2.0f, -2.0f)
                horizontalLineToRelative(-2.0f)
                verticalLineToRelative(2.0f)
                close()
                moveTo(19.0f, 9.0f)
                horizontalLineToRelative(2.0f)
                lineTo(21.0f, 7.0f)
                horizontalLineToRelative(-2.0f)
                verticalLineToRelative(2.0f)
                close()
                moveTo(19.0f, 17.0f)
                horizontalLineToRelative(2.0f)
                verticalLineToRelative(-2.0f)
                horizontalLineToRelative(-2.0f)
                verticalLineToRelative(2.0f)
                close()
                moveTo(15.0f, 21.0f)
                horizontalLineToRelative(2.0f)
                verticalLineToRelative(-2.0f)
                horizontalLineToRelative(-2.0f)
                verticalLineToRelative(2.0f)
                close()
                moveTo(15.0f, 5.0f)
                horizontalLineToRelative(2.0f)
                lineTo(17.0f, 3.0f)
                horizontalLineToRelative(-2.0f)
                verticalLineToRelative(2.0f)
                close()
                moveTo(7.0f, 17.0f)
                horizontalLineToRelative(10.0f)
                lineTo(17.0f, 7.0f)
                lineTo(7.0f, 7.0f)
                verticalLineToRelative(10.0f)
                close()
                moveTo(9.0f, 9.0f)
                horizontalLineToRelative(6.0f)
                verticalLineToRelative(6.0f)
                lineTo(9.0f, 15.0f)
                lineTo(9.0f, 9.0f)
                close()
            }
        }
        return _selectAll!!
    }

private var _selectAll: ImageVector? = null

val Icons.Filled.Error: ImageVector
    get() {
        if (_error != null) {
            return _error!!
        }
        _error = materialIcon(name = "Filled.Error") {
            materialPath {
                moveTo(12.0f, 2.0f)
                curveTo(6.48f, 2.0f, 2.0f, 6.48f, 2.0f, 12.0f)
                reflectiveCurveToRelative(4.48f, 10.0f, 10.0f, 10.0f)
                reflectiveCurveToRelative(10.0f, -4.48f, 10.0f, -10.0f)
                reflectiveCurveTo(17.52f, 2.0f, 12.0f, 2.0f)
                close()
                moveTo(13.0f, 17.0f)
                horizontalLineToRelative(-2.0f)
                verticalLineToRelative(-2.0f)
                horizontalLineToRelative(2.0f)
                verticalLineToRelative(2.0f)
                close()
                moveTo(13.0f, 13.0f)
                horizontalLineToRelative(-2.0f)
                lineTo(11.0f, 7.0f)
                horizontalLineToRelative(2.0f)
                verticalLineToRelative(6.0f)
                close()
            }
        }
        return _error!!
    }

private var _error: ImageVector? = null

val Icons.Filled.History: ImageVector
    get() {
        if (_history != null) {
            return _history!!
        }
        _history = materialIcon(name = "Filled.History") {
            materialPath {
                moveTo(13.0f, 3.0f)
                curveToRelative(-4.97f, 0.0f, -9.0f, 4.03f, -9.0f, 9.0f)
                lineTo(1.0f, 12.0f)
                lineToRelative(3.89f, 3.89f)
                lineToRelative(0.07f, 0.14f)
                lineTo(9.0f, 12.0f)
                lineTo(6.0f, 12.0f)
                curveToRelative(0.0f, -3.87f, 3.13f, -7.0f, 7.0f, -7.0f)
                reflectiveCurveToRelative(7.0f, 3.13f, 7.0f, 7.0f)
                reflectiveCurveToRelative(-3.13f, 7.0f, -7.0f, 7.0f)
                curveToRelative(-1.93f, 0.0f, -3.68f, -0.79f, -4.94f, -2.06f)
                lineToRelative(-1.42f, 1.42f)
                curveTo(8.27f, 19.99f, 10.51f, 21.0f, 13.0f, 21.0f)
                curveToRelative(4.97f, 0.0f, 9.0f, -4.03f, 9.0f, -9.0f)
                reflectiveCurveToRelative(-4.03f, -9.0f, -9.0f, -9.0f)
                close()
                moveTo(12.0f, 8.0f)
                verticalLineToRelative(5.0f)
                lineToRelative(4.28f, 2.54f)
                lineToRelative(0.72f, -1.21f)
                lineToRelative(-3.5f, -2.08f)
                lineTo(13.5f, 8.0f)
                lineTo(12.0f, 8.0f)
                close()
            }
        }
        return _history!!
    }

private var _history: ImageVector? = null

val Icons.AutoMirrored.Outlined.Label: ImageVector
    get() {
        if (_label != null) {
            return _label!!
        }
        _label = materialIcon(name = "AutoMirrored.Outlined.Label", autoMirror = true) {
            materialPath {
                moveTo(17.63f, 5.84f)
                curveTo(17.27f, 5.33f, 16.67f, 5.0f, 16.0f, 5.0f)
                lineTo(5.0f, 5.01f)
                curveTo(3.9f, 5.01f, 3.0f, 5.9f, 3.0f, 7.0f)
                verticalLineToRelative(10.0f)
                curveToRelative(0.0f, 1.1f, 0.9f, 1.99f, 2.0f, 1.99f)
                lineTo(16.0f, 19.0f)
                curveToRelative(0.67f, 0.0f, 1.27f, -0.33f, 1.63f, -0.84f)
                lineTo(22.0f, 12.0f)
                lineToRelative(-4.37f, -6.16f)
                close()
                moveTo(16.0f, 17.0f)
                horizontalLineTo(5.0f)
                verticalLineTo(7.0f)
                horizontalLineToRelative(11.0f)
                lineToRelative(3.55f, 5.0f)
                lineTo(16.0f, 17.0f)
                close()
            }
        }
        return _label!!
    }

private var _label: ImageVector? = null
