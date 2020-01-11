/*
 * Copyright (C) 2017-2019 Alexey Rochev <equeim@gmail.com>
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

package org.equeim.tremotesf

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.text.Html

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup

import androidx.annotation.StringRes
import androidx.core.net.toUri
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter

import com.google.android.material.tabs.TabLayoutMediator

import org.sufficientlysecure.donations.DonationsFragment

import org.equeim.tremotesf.utils.findFragment

import kotlinx.android.synthetic.main.about_fragment.*
import kotlinx.android.synthetic.main.about_fragment_license_tab_fragment.*
import kotlinx.android.synthetic.main.about_fragment_base_tab_fragment.*
import kotlinx.android.synthetic.main.donate_fragment.*
import kotlinx.android.synthetic.main.donate_fragment_yandex.*


class AboutFragment : NavigationFragment(R.layout.about_fragment) {
    companion object {
        const val DONATE = "donate"
    }

    private var pagerAdapter: PagerAdapter? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        toolbar?.title = "%s %s".format(getString(R.string.app_name), BuildConfig.VERSION_NAME)

        pagerAdapter = PagerAdapter(this)
        pager.adapter = pagerAdapter
        TabLayoutMediator(tab_layout, pager) { tab, position ->
            tab.setText(PagerAdapter.getTitle(position))
        }.attach()

        if (requireArguments().getBoolean(DONATE)) {
            pager.currentItem = 1
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        findFragment<DonateTabFragment>()?.onActivityResult(requestCode, resultCode, data)
        super.onActivityResult(requestCode, resultCode, data)
    }

    private class PagerAdapter(fragment: Fragment) : FragmentStateAdapter(fragment) {
        companion object {
            private val tabs = Tab.values()

            @StringRes
            fun getTitle(position: Int): Int {
                return when (tabs[position]) {
                    Tab.Main -> R.string.about
                    Tab.Donate -> R.string.donate
                    Tab.Authors -> R.string.authors
                    Tab.Translators -> R.string.translators
                    Tab.License -> R.string.license
                }
            }
        }

        private enum class Tab {
            Main,
            Donate,
            Authors,
            Translators,
            License
        }

        override fun getItemCount() = tabs.size

        override fun createFragment(position: Int): Fragment {
            return when (tabs[position]) {
                Tab.Main -> MainTabFragment()
                Tab.Donate -> DonateTabFragment.newInstance()
                Tab.Authors -> AuthorsTabFragment()
                Tab.Translators -> TranslatorsTabFragment()
                Tab.License -> LicenseTabFragment()
            }
        }
    }

    class MainTabFragment : Fragment(R.layout.about_fragment_base_tab_fragment) {
        override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
            super.onViewCreated(view, savedInstanceState)
            val inputStream = resources.openRawResource(R.raw.about)
            text_view.text = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                Html.fromHtml(inputStream.reader().readText(), 0)
            } else {
                @Suppress("DEPRECATION")
                Html.fromHtml(inputStream.reader().readText())
            }
            inputStream.close()
        }
    }

    class DonateTabFragment : DonationsFragment() {
        companion object {
            private const val GOOGLE_PUBKEY = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAh3VArCqp6ZWl/GfnY6c+f4NeqQ8TeAOHmfnfD9RC7QRa+YffB9mKX+2EkgiSlprMO/ddu4V1If8L0CP9n/n21sQy7Bf5+Zrq5GL3PhXXGMv1c896YZptopzeDg2dVawsZEDLuiQrmbUMkYj4QGOxTm9yVRlX52gg6IjzujBcEMlcdhyNF0W590R65VIN1DtYJ7/vM0FbF7UwseoJy6z10zsVfNB5M6lYDNbROjTn2xrYWj4kJ3QZQohV/ZNvLJHmrh5YA3Ybf5cX8AW5b/ET8G5NVQbo3ESEfNJsbiHpvQxvQBlwyMZBFJKouQv8c7xzrA5NQBHrIch5CDpL1JKe7wIDAQAB"
            private val GOOGLE_CATALOG = arrayOf("tremotesf.1",
                                                 "tremotesf.2",
                                                 "tremotesf.3",
                                                 "tremotesf.5",
                                                 "tremotesf.10")
            private val GOOGLE_CATALOG_VALUES = arrayOf("$1",
                                                        "$2",
                                                        "$3",
                                                        "$5",
                                                        "$10")

            private const val PAYPAL_USER = "DDQTRHTY5YV2G"
            private const val PAYPAL_CURRENCY_CODE = "USD"
            private const val PAYPAL_ITEM_NAME = "Support Tremotesf (Android) development"

            fun newInstance(): DonateTabFragment {
                val fragment = DonateTabFragment()
                fragment.arguments = bundleOf(ARG_DEBUG to BuildConfig.DEBUG,
                                              ARG_GOOGLE_ENABLED to BuildConfig.DONATIONS_GOOGLE,
                                              ARG_GOOGLE_PUBKEY to GOOGLE_PUBKEY,
                                              ARG_GOOGLE_CATALOG to GOOGLE_CATALOG,
                                              ARG_GOOGLE_CATALOG_VALUES to GOOGLE_CATALOG_VALUES,
                                              ARG_PAYPAL_ENABLED to !BuildConfig.DONATIONS_GOOGLE,
                                              ARG_PAYPAL_USER to PAYPAL_USER,
                                              ARG_PAYPAL_CURRENCY_CODE to PAYPAL_CURRENCY_CODE,
                                              ARG_PAYPAL_ITEM_NAME to PAYPAL_ITEM_NAME,
                                              ARG_FLATTR_ENABLED to false,
                                              ARG_FLATTR_PROJECT_URL to null,
                                              ARG_FLATTR_URL to null,
                                              ARG_BITCOIN_ENABLED to false,
                                              ARG_BITCOIN_ADDRESS to null)
                return fragment
            }
        }

        override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
            return inflater.inflate(R.layout.donate_fragment, container, false)
        }

        override fun onActivityCreated(savedInstanceState: Bundle?) {
            super.onActivityCreated(savedInstanceState)

            if (!mGoogleEnabled) {
                donations__yandex_stub.inflate()
                donations__yandex_donate_button.setOnClickListener {
                    startActivity(Intent(Intent.ACTION_VIEW, "https://yasobe.ru/na/equeim_tremotesf_android".toUri()))
                }
            }
        }
    }

    class AuthorsTabFragment : Fragment(R.layout.about_fragment_base_tab_fragment) {
        override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
            super.onViewCreated(view, savedInstanceState)
            val inputStream = resources.openRawResource(R.raw.authors)
            text_view.text = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                Html.fromHtml(inputStream.reader().readText(), 0)
            } else {
                @Suppress("DEPRECATION")
                Html.fromHtml(inputStream.reader().readText())
            }
            inputStream.close()
        }
    }

    class TranslatorsTabFragment : Fragment(R.layout.about_fragment_base_tab_fragment) {
        override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
            super.onViewCreated(view, savedInstanceState)
            val inputStream = resources.openRawResource(R.raw.translators)
            text_view.text = inputStream.reader().readText()
            inputStream.close()
        }
    }

    class LicenseTabFragment : Fragment(R.layout.about_fragment_license_tab_fragment) {
        override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
            super.onViewCreated(view, savedInstanceState)
            val inputStream = resources.openRawResource(R.raw.license)
            web_view.loadData(inputStream.reader().readText(), "text/html", null)
            inputStream.close()
        }
    }
}
