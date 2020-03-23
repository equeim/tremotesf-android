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

package org.equeim.tremotesf.donationsfragment

import android.app.Application
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View

import androidx.core.net.toUri
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.observe
import androidx.lifecycle.viewModelScope

import com.google.android.material.snackbar.Snackbar

import org.equeim.tremotesf.BuildConfig
import org.equeim.tremotesf.R
import org.equeim.tremotesf.utils.ArrayDropdownAdapter

import kotlinx.android.synthetic.main.donations_fragment_fdroid.*
import kotlinx.android.synthetic.main.donations_fragment_google.*
import org.equeim.tremotesf.utils.showSnackbar


class DonationsFragment : Fragment(if (BuildConfig.DONATIONS_GOOGLE) R.layout.donations_fragment_google else R.layout.donations_fragment_fdroid) {
    companion object {
        private const val PAYPAL_USER = "DDQTRHTY5YV2G"
        private const val PAYPAL_CURRENCY_CODE = "USD"
        private const val PAYPAL_ITEM_NAME = "Support Tremotesf (Android) development"

        private const val YANDEX_URL = "https://yasobe.ru/na/equeim_tremotesf_android"
    }

    private val model: Model by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        if (BuildConfig.DONATIONS_GOOGLE) {
            model.billing?.isSetUp?.observe(viewLifecycleOwner, ::onBillingSetup)
        } else {
            paypal_donate_button.setOnClickListener { donatePayPal() }
            yandex_donate_button.setOnClickListener { donateYandex() }
        }
    }

    private fun onBillingSetup(isSetUp: Boolean) {
        val billing = model.billing ?: return
        if (isSetUp) {
            val items = if (BuildConfig.DEBUG) {
                billing.skus.map { "${it.sku}: ${it.price}" }
            } else {
                billing.skus.map(IGoogleBillingHelper.SkuData::price)
            }
            skus_view.setAdapter(ArrayDropdownAdapter(items))
            if (items.isNotEmpty()) {
                skus_view.setText(items.first())
            }
            donate_button.setOnClickListener {
                donateGoogle(items.indexOf(skus_view.text.toString()))
            }
            billing.purchasesUpdatedEvent.observe(viewLifecycleOwner, ::onBillingPurchasesUpdated)
        }
        skus_view_layout.isEnabled = isSetUp
        donate_button.isEnabled = isSetUp
    }

    private fun donateGoogle(skuIndex: Int) {
        model.billing?.let { billing ->
            val error = billing.launchBillingFlow(skuIndex, requireActivity())
            if (error != IGoogleBillingHelper.PurchaseError.None) {
                onBillingPurchasesUpdated(error)
            }
        }
    }

    private fun onBillingPurchasesUpdated(error: IGoogleBillingHelper.PurchaseError) {
        when (error) {
            IGoogleBillingHelper.PurchaseError.None -> {
                requireView().showSnackbar(R.string.donations_snackbar_ok, Snackbar.LENGTH_SHORT)
            }
            IGoogleBillingHelper.PurchaseError.Error -> {
                requireView().showSnackbar(R.string.donations_snackbar_error, Snackbar.LENGTH_LONG)
            }
            IGoogleBillingHelper.PurchaseError.Cancelled -> {}
        }
    }

    private fun donatePayPal() {
        val builder = Uri.Builder()
                .scheme("https")
                .authority("www.paypal.com")
                .path("cgi-bin/webscr")
                .appendQueryParameter("cmd", "_donations")
                .appendQueryParameter("business", PAYPAL_USER)
                .appendQueryParameter("lc", "US")
                .appendQueryParameter("item_name", PAYPAL_ITEM_NAME)
                .appendQueryParameter("no_note", "1")
                .appendQueryParameter("no_shipping", "1")
                .appendQueryParameter("currency_code", PAYPAL_CURRENCY_CODE)
        val intent = Intent(Intent.ACTION_VIEW, builder.build())
        val chooser = Intent.createChooser(intent, getString(R.string.donations_paypal_title))
        if (intent.resolveActivity(requireActivity().packageManager) != null) {
            startActivity(chooser)
        }
    }

    private fun donateYandex() {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, YANDEX_URL.toUri()))
        } catch (ignore: ActivityNotFoundException) {}
    }

    class Model(application: Application) : AndroidViewModel(application) {
        val billing: IGoogleBillingHelper? = GoogleBillingHelperFactory().createBillingWrapper(application, viewModelScope)

        override fun onCleared() {
            billing?.endConnection()
        }
    }
}