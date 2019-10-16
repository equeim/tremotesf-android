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
import android.view.ViewStub
import android.widget.Button

import androidx.appcompat.widget.Toolbar
import androidx.core.net.toUri
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentPagerAdapter

import org.sufficientlysecure.donations.DonationsFragment

import kotlinx.android.synthetic.main.about_activity.*
import kotlinx.android.synthetic.main.about_activity_license_fragment.*
import kotlinx.android.synthetic.main.about_activity_pager_fragment.*


class AboutActivity : BaseActivity(R.layout.about_activity, true) {
    private lateinit var pagerAdapter: PagerAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        title = "%s %s".format(getString(R.string.app_name), BuildConfig.VERSION_NAME)

        setSupportActionBar(toolbar as Toolbar)
        supportActionBar!!.setDisplayHomeAsUpEnabled(true)

        pagerAdapter = PagerAdapter()
        pager.adapter = pagerAdapter
        tab_layout.setupWithViewPager(pager)

        if (intent.getBooleanExtra("donate", false)) {
            pager.currentItem = 1
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        pagerAdapter.donateFragment?.onActivityResult(requestCode, resultCode, data)

    }

    private inner class PagerAdapter : FragmentPagerAdapter(supportFragmentManager, BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT) {
        var donateFragment: DonateFragment? = null

        override fun getCount() = 5

        override fun getItem(position: Int): Fragment {
            return when (position) {
                0 -> AboutFragment()
                1 -> DonateFragment.newInstance()
                2 -> AuthorsFragment()
                3 -> TranslatorsFragment()
                4 -> LicenseFragment()
                else -> Fragment()
            }
        }

        override fun instantiateItem(container: ViewGroup, position: Int): Any {
            val item = super.instantiateItem(container, position)
            if (position == 1) {
                donateFragment = item as DonateFragment
            }
            return item
        }

        override fun destroyItem(container: ViewGroup, position: Int, `object`: Any) {
            if (position == 1) {
                donateFragment = null
            }
            super.destroyItem(container, position, `object`)
        }

        override fun getPageTitle(position: Int): CharSequence {
            return when (position) {
                0 -> getString(R.string.about)
                1 -> getString(R.string.donate)
                2 -> getString(R.string.authors)
                3 -> getString(R.string.translators)
                4 -> getString(R.string.license)
                else -> ""
            }
        }
    }

    class AboutFragment : Fragment(R.layout.about_activity_pager_fragment) {
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

    class DonateFragment : DonationsFragment() {
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

            fun newInstance(): DonateFragment {
                val fragment = DonateFragment()
                fragment.arguments = bundleOf(ARG_DEBUG to false,
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
				requireActivity().findViewById<ViewStub>(R.id.donations__yandex_stub).inflate()
                requireActivity().findViewById<Button>(R.id.donations__yandex_donate_button).setOnClickListener {
                    startActivity(Intent(Intent.ACTION_VIEW, "https://yasobe.ru/na/equeim_tremotesf_android".toUri()))
                }
            }
        }
    }

    class AuthorsFragment : Fragment(R.layout.about_activity_pager_fragment) {
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

    class TranslatorsFragment : Fragment(R.layout.about_activity_pager_fragment) {
        override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
            super.onViewCreated(view, savedInstanceState)
            val inputStream = resources.openRawResource(R.raw.translators)
            text_view.text = inputStream.reader().readText()
            inputStream.close()
        }
    }

    class LicenseFragment : Fragment(R.layout.about_activity_license_fragment) {
        override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
            super.onViewCreated(view, savedInstanceState)
            val inputStream = resources.openRawResource(R.raw.license)
            web_view.loadData(inputStream.reader().readText(), "text/html", null)
            inputStream.close()
        }
    }
}
