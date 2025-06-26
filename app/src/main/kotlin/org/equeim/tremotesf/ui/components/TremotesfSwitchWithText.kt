// SPDX-FileCopyrightText: 2017-2025 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package org.equeim.tremotesf.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import org.equeim.tremotesf.R
import org.equeim.tremotesf.ui.ComponentPreview
import org.equeim.tremotesf.ui.Dimens
import org.equeim.tremotesf.ui.applyDisabledAlpha

@Composable
fun TremotesfSwitchWithText(
    checked: Boolean,
    @StringRes text: Int,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    horizontalContentPadding: Dp = Dp.Unspecified,
    enabled: Boolean = true
) {
    val textString = stringResource(text)
    Row(
        modifier = modifier
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(horizontal = horizontalContentPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.SpacingSmall)
    ) {
        Text(
            text = textString,
            color = LocalContentColor.current.applyDisabledAlpha(enabled),
            modifier = Modifier
                .weight(1.0f)
                .padding(vertical = Dimens.SpacingSmall),
        )
        Switch(
            checked = checked,
            onCheckedChange = null,
            enabled = enabled,
            // onCheckedChange = null reduces the size of a Switch which we want to keep
            modifier = Modifier.minimumInteractiveComponentSize()
        )
    }
}

@Preview
@Composable
private fun TremotesfSwitchWithTextPreview() = ComponentPreview {
    TremotesfSwitchWithText(
        checked = true,
        text = R.string.start_added_torrents,
        onCheckedChange = {},
    )
}
