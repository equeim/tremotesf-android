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

import android.os.Bundle

import androidx.annotation.LayoutRes
import androidx.annotation.StringRes
import androidx.core.text.HtmlCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.navArgs
import androidx.viewpager2.adapter.FragmentStateAdapter

import com.google.android.material.tabs.TabLayoutMediator

import org.equeim.tremotesf.BuildConfig
import org.equeim.tremotesf.R
import org.equeim.tremotesf.databinding.AboutFragmentBaseTabFragmentBinding
import org.equeim.tremotesf.databinding.AboutFragmentBinding
import org.equeim.tremotesf.databinding.AboutFragmentLicenseTabFragmentBinding
import org.equeim.tremotesf.ui.donationsfragment.DonationsFragment


class AboutFragment : NavigationFragment(R.layout.about_fragment) {
    private val args: AboutFragmentArgs by navArgs()
    private var pagerAdapter: PagerAdapter? = null

    override fun onViewStateRestored(savedInstanceState: Bundle?) {
        super.onViewStateRestored(savedInstanceState)
        toolbar?.title = "%s %s".format(getString(R.string.app_name), BuildConfig.VERSION_NAME)

        pagerAdapter = PagerAdapter(this)

        with(AboutFragmentBinding.bind(requireView())) {
            pager.adapter = pagerAdapter
            TabLayoutMediator(tabLayout, pager) { tab, position ->
                tab.setText(PagerAdapter.getTitle(position))
            }.attach()

            if (args.donate) {
                pager.currentItem = 1
            }
        }
    }

    private class PagerAdapter(fragment: Fragment) : FragmentStateAdapter(fragment) {
        companion object {
            private val tabs = Tab.values()

            @StringRes
            fun getTitle(position: Int): Int {
                return when (tabs[position]) {
                    Tab.Main -> R.string.about
                    Tab.Donate -> R.string.donate_tab
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
                Tab.Donate -> DonationsFragment()
                Tab.Authors -> AuthorsTabFragment()
                Tab.Translators -> TranslatorsTabFragment()
                Tab.License -> LicenseTabFragment()
            }
        }
    }

    open class PagerFragment(@LayoutRes contentLayoutId: Int) : Fragment(contentLayoutId) {
        override fun onViewStateRestored(savedInstanceState: Bundle?) {
            super.onViewStateRestored(savedInstanceState)
            addNavigationBarBottomPadding(true)
        }
    }

    class MainTabFragment : PagerFragment(R.layout.about_fragment_base_tab_fragment) {
        override fun onViewStateRestored(savedInstanceState: Bundle?) {
            super.onViewStateRestored(savedInstanceState)
            resources.openRawResource(R.raw.about).use { inputStream ->
                with(AboutFragmentBaseTabFragmentBinding.bind(requireView())) {
                    textView.text = HtmlCompat.fromHtml(inputStream.reader().readText(), 0)
                }
            }
        }
    }

    class AuthorsTabFragment : PagerFragment(R.layout.about_fragment_base_tab_fragment) {
        override fun onViewStateRestored(savedInstanceState: Bundle?) {
            super.onViewStateRestored(savedInstanceState)

            resources.openRawResource(R.raw.authors).use { inputStream ->
                with(AboutFragmentBaseTabFragmentBinding.bind(requireView())) {
                    textView.text = HtmlCompat.fromHtml(inputStream.reader().readText(), 0)
                }
            }
        }
    }

    class TranslatorsTabFragment : PagerFragment(R.layout.about_fragment_base_tab_fragment) {
        override fun onViewStateRestored(savedInstanceState: Bundle?) {
            super.onViewStateRestored(savedInstanceState)
            resources.openRawResource(R.raw.translators).use {
                AboutFragmentBaseTabFragmentBinding.bind(requireView()).textView.text =
                    it.reader().readText()
            }
        }
    }

    class LicenseTabFragment : PagerFragment(R.layout.about_fragment_license_tab_fragment) {
        override fun onViewStateRestored(savedInstanceState: Bundle?) {
            super.onViewStateRestored(savedInstanceState)
            resources.openRawResource(R.raw.license).use {
                AboutFragmentLicenseTabFragmentBinding.bind(requireView()).webView.loadData(
                    it.reader().readText(), "text/html", null
                )
            }
        }
    }
}
