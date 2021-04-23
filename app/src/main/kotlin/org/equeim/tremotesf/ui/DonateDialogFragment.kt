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

package org.equeim.tremotesf.ui

import android.app.Dialog
import android.os.Bundle
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.equeim.tremotesf.R

class DonateDialogFragment : NavigationDialogFragment() {
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        Settings.donateDialogShown = true
        return MaterialAlertDialogBuilder(requireContext())
            .setMessage(getString(R.string.donations_description) + "\n\n" + getString(R.string.donate_dialog_again))
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.donations_donate) { _, _ ->
                navigate(DonateDialogFragmentDirections.aboutFragment(true))
            }
            .create()
    }
}