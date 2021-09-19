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

package org.equeim.tremotesf.ui.donationsfragment

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import androidx.fragment.app.Fragment
import org.equeim.tremotesf.R
import org.equeim.tremotesf.billing.DONATE_PAYPAL_URI
import org.equeim.tremotesf.billing.DONATE_YANDEX_URI
import org.equeim.tremotesf.databinding.DonationsFragmentFdroidBinding
import org.equeim.tremotesf.ui.addNavigationBarBottomPadding
import org.equeim.tremotesf.ui.utils.Utils

class DonationsFragment : Fragment(R.layout.donations_fragment_fdroid) {
    override fun onViewStateRestored(savedInstanceState: Bundle?) {
        super.onViewStateRestored(savedInstanceState)
        with(DonationsFragmentFdroidBinding.bind(requireView())) {
            paypalDonateButton.setOnClickListener { donatePayPal() }
            yandexDonateButton.setOnClickListener { donateYandex() }
        }
        addNavigationBarBottomPadding()
    }

    private fun donatePayPal() {
        Utils.startActivityChooser(
            Intent(Intent.ACTION_VIEW, DONATE_PAYPAL_URI),
            getText(R.string.donations_paypal_title),
            requireContext()
        )
    }

    private fun donateYandex() {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, DONATE_YANDEX_URI))
        } catch (ignore: ActivityNotFoundException) {
        }
    }
}
