// SPDX-FileCopyrightText: 2017-2023 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package org.equeim.tremotesf.ui

import android.os.Bundle
import androidx.annotation.LayoutRes
import androidx.annotation.StringRes
import androidx.core.text.HtmlCompat
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.google.android.material.tabs.TabLayoutMediator
import org.equeim.tremotesf.BuildConfig
import org.equeim.tremotesf.R
import org.equeim.tremotesf.databinding.AboutFragmentBaseTabFragmentBinding
import org.equeim.tremotesf.databinding.AboutFragmentBinding
import org.equeim.tremotesf.databinding.AboutFragmentLicenseTabFragmentBinding


class AboutFragment : NavigationFragment(R.layout.about_fragment) {
    private var pagerAdapter: PagerAdapter? = null

    override fun onViewStateRestored(savedInstanceState: Bundle?) {
        super.onViewStateRestored(savedInstanceState)
        toolbar.title = "%s %s".format(getString(R.string.app_name), BuildConfig.VERSION_NAME)

        pagerAdapter = PagerAdapter(this)

        with(AboutFragmentBinding.bind(requireView())) {
            pager.adapter = pagerAdapter
            TabLayoutMediator(tabLayout, pager) { tab, position ->
                tab.setText(PagerAdapter.getTitle(position))
            }.attach()
        }
    }

    private class PagerAdapter(fragment: Fragment) : FragmentStateAdapter(fragment) {
        companion object {
            @StringRes
            fun getTitle(position: Int): Int {
                return when (Tab.entries[position]) {
                    Tab.Main -> R.string.about
                    Tab.Authors -> R.string.authors
                    Tab.Translators -> R.string.translators
                    Tab.License -> R.string.license
                }
            }
        }

        private enum class Tab {
            Main,
            Authors,
            Translators,
            License
        }

        override fun getItemCount() = Tab.entries.size

        override fun createFragment(position: Int): Fragment {
            return when (Tab.entries[position]) {
                Tab.Main -> MainTabFragment()
                Tab.Authors -> AuthorsTabFragment()
                Tab.Translators -> TranslatorsTabFragment()
                Tab.License -> LicenseTabFragment()
            }
        }
    }

    open class PagerFragment(@LayoutRes contentLayoutId: Int) : Fragment(contentLayoutId) {
        override fun onViewStateRestored(savedInstanceState: Bundle?) {
            super.onViewStateRestored(savedInstanceState)
            applyNavigationBarBottomInset()
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
