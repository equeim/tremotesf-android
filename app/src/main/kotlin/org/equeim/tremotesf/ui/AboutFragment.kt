// SPDX-FileCopyrightText: 2017-2024 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package org.equeim.tremotesf.ui

import android.os.Bundle
import android.text.Html
import androidx.annotation.LayoutRes
import androidx.annotation.StringRes
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.google.android.material.tabs.TabLayoutMediator
import org.equeim.tremotesf.BuildConfig
import org.equeim.tremotesf.R
import org.equeim.tremotesf.databinding.AboutFragmentBaseTabFragmentBinding
import org.equeim.tremotesf.databinding.AboutFragmentBinding
import org.equeim.tremotesf.databinding.AboutFragmentLicenseTabFragmentBinding
import timber.log.Timber


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
            val html = buildString {
                appendLine("""
                    <!DOCTYPE html>
                    <p>&#169; 2017-2024 Alexey Rochev &lt;<a href="mailto:equeim@gmail.com">equeim@gmail.com</a>&gt;</p>
                """.trimIndent())
                append("<p>")
                append(getString(R.string.source_code_url, makeUrlTag(SOURCE_CODE_URL)))
                appendLine("</p>")
                append("<p>")
                append(getString(R.string.translations_url, makeUrlTag(TRANSLATIONS_URL)))
                appendLine("</p>")
            }
            with(AboutFragmentBaseTabFragmentBinding.bind(requireView())) {
                textView.text = try {
                    Html.fromHtml(html, 0)
                } catch (e: Exception) {
                    Timber.e(e, "Failed to parse html")
                    null
                }
            }
        }

        private companion object {
            const val SOURCE_CODE_URL = "https://github.com/equeim/tremotesf-android"
            const val TRANSLATIONS_URL = "https://www.transifex.com/equeim/tremotesf-android"
            fun makeUrlTag(url: String): String = """<a href="$url">$url</a>"""
        }
    }

    class AuthorsTabFragment : PagerFragment(R.layout.about_fragment_base_tab_fragment) {
        override fun onViewStateRestored(savedInstanceState: Bundle?) {
            super.onViewStateRestored(savedInstanceState)
            val maintainer = getString(R.string.maintainer)
            val contributor = getString(R.string.contributor)
            val html = """
                <!DOCTYPE html>
                <p>
                    Alexey Rochev &lt;<a href="mailto:equeim@gmail.com">equeim@gmail.com</a>&gt;
                    <br/>
                    <i>$maintainer</i>
                </p>
                <p>
                    Kevin Richter &lt;<a href="mailto:me@kevinrichter.nl">me@kevinrichter.nl</a>&gt;
                    <br/>
                    <i>$contributor</i>
                </p>
            """.trimIndent()
            with(AboutFragmentBaseTabFragmentBinding.bind(requireView())) {
                textView.text = try {
                    Html.fromHtml(html, 0)
                } catch (e: Exception) {
                    Timber.e(e, "Failed to parse html")
                    null
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
