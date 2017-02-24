/*
 * Copyright (C) 2017 Alexey Rochev <equeim@gmail.com>
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

import android.app.Fragment
import android.os.Bundle
import android.text.Html

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup

import android.webkit.WebView

import android.support.v7.widget.Toolbar
import android.support.v13.app.FragmentPagerAdapter

import kotlinx.android.synthetic.main.about_activity.*
import kotlinx.android.synthetic.main.about_activity_pager_fragment.*

class AboutActivity : BaseActivity() {
    private lateinit var licenseFragmentView: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setTheme(Settings.themeNoActionBar)
        setContentView(R.layout.about_activity)
        setPreLollipopShadow()

        title = "%s %s".format(getString(R.string.app_name), BuildConfig.VERSION_NAME)

        setSupportActionBar(toolbar as Toolbar)
        supportActionBar!!.setDisplayHomeAsUpEnabled(true)

        pager.adapter = PagerAdapter()
        tab_layout.setupWithViewPager(pager)

        licenseFragmentView = layoutInflater.inflate(R.layout.about_activity_license_fragment, null)
        val inputStream = resources.openRawResource(R.raw.license)
        (licenseFragmentView.findViewById(R.id.web_view) as WebView).loadData(inputStream.reader().readText(),
                                                                              "text/html",
                                                                              null)
        inputStream.close()
    }

    private inner class PagerAdapter : FragmentPagerAdapter(fragmentManager) {
        override fun getCount() = 4

        override fun getItem(position: Int): Fragment? {
            return when (position) {
                0 -> AboutFragment()
                1 -> AuthorsFragment()
                2 -> TranslatorsFragment()
                3 -> LicenseFragment()
                else -> null
            }
        }

        override fun getPageTitle(position: Int): CharSequence {
            return when (position) {
                0 -> getString(R.string.about)
                1 -> getString(R.string.authors)
                2 -> getString(R.string.translators)
                3 -> getString(R.string.license)
                else -> String()
            }
        }
    }

    class AboutFragment : Fragment() {
        override fun onCreateView(inflater: LayoutInflater,
                                  container: ViewGroup?,
                                  savedInstanceState: Bundle?): View {
            return inflater.inflate(R.layout.about_activity_pager_fragment, container, false)
        }

        override fun onViewCreated(view: View?, savedInstanceState: Bundle?) {
            super.onViewCreated(view, savedInstanceState)
            val inputStream = resources.openRawResource(R.raw.about)
            text_view.text = Html.fromHtml(inputStream.reader().readText())
            inputStream.close()
        }
    }

    class AuthorsFragment : Fragment() {
        override fun onCreateView(inflater: LayoutInflater,
                                  container: ViewGroup?,
                                  savedInstanceState: Bundle?): View {
            return inflater.inflate(R.layout.about_activity_pager_fragment, container, false)
        }

        override fun onViewCreated(view: View?, savedInstanceState: Bundle?) {
            super.onViewCreated(view, savedInstanceState)
            val inputStream = resources.openRawResource(R.raw.authors)
            text_view.text = Html.fromHtml(inputStream.reader().readText())
            inputStream.close()
        }
    }

    class TranslatorsFragment : Fragment() {
        override fun onCreateView(inflater: LayoutInflater,
                                  container: ViewGroup?,
                                  savedInstanceState: Bundle?): View {
            return inflater.inflate(R.layout.about_activity_pager_fragment, container, false)
        }

        override fun onViewCreated(view: View?, savedInstanceState: Bundle?) {
            super.onViewCreated(view, savedInstanceState)
            val inputStream = resources.openRawResource(R.raw.translators)
            text_view.text = inputStream.reader().readText()
            inputStream.close()
        }
    }

    class LicenseFragment : Fragment() {
        override fun onCreateView(inflater: LayoutInflater,
                                  container: ViewGroup?,
                                  savedInstanceState: Bundle?): View {
            return (activity as AboutActivity).licenseFragmentView
        }
    }
}