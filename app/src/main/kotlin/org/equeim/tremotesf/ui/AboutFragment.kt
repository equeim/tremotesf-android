// SPDX-FileCopyrightText: 2017-2025 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package org.equeim.tremotesf.ui

import android.annotation.SuppressLint
import android.content.Context
import android.view.View
import android.webkit.WebView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavController
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import kotlinx.coroutines.launch
import org.equeim.tremotesf.BuildConfig
import org.equeim.tremotesf.R
import org.equeim.tremotesf.ui.components.TremotesfTopAppBar
import org.equeim.tremotesf.ui.components.TremotesfTopAppBarDefaults
import org.equeim.tremotesf.ui.utils.safeNavigate
import timber.log.Timber

class AboutFragment : ComposeFragment() {
    @Composable
    override fun Content(navController: NavController) {
        AboutScreen(
            navigateUp = navController::navigateUp,
            showLicense = { navController.safeNavigate(AboutFragmentDirections.toLicenseFragment()) })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AboutScreen(navigateUp: () -> Unit, showLicense: () -> Unit) {
    val pagerState = rememberPagerState { Tab.entries.size }
    val coroutineScope = rememberCoroutineScope()
    Scaffold(
        topBar = {
            Column {
                TremotesfTopAppBar(
                    title = "${stringResource(R.string.app_name)} ${BuildConfig.VERSION_NAME}",
                    navigateUp = navigateUp,
                )
                PrimaryScrollableTabRow(
                    selectedTabIndex = pagerState.currentPage,
                    containerColor = TremotesfTopAppBarDefaults.containerColor(),
                    divider = {}
                ) {
                    for (tab in Tab.entries) {
                        Tab(
                            selected = pagerState.currentPage == tab.ordinal,
                            onClick = { coroutineScope.launch { pagerState.animateScrollToPage(tab.ordinal) } },
                            selectedContentColor = MaterialTheme.colorScheme.primary,
                            unselectedContentColor = TopAppBarDefaults.topAppBarColors().titleContentColor,
                            text = {
                                Text(
                                    stringResource(
                                        when (tab) {
                                            Tab.About -> R.string.about
                                            Tab.Authors -> R.string.authors
                                            Tab.Translators -> R.string.translators
                                        }
                                    )
                                )
                            }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        HorizontalPager(
            pagerState,
            modifier = Modifier
                .fillMaxSize()
                .consumeWindowInsets(innerPadding)
        ) { currentPage ->
            val currentTab = Tab.entries[currentPage]
            when (currentTab) {
                Tab.About -> AboutTab(innerPadding, showLicense)
                Tab.Authors -> AuthorsTab(innerPadding)
                Tab.Translators -> TranslatorsTab(innerPadding)
            }
        }
    }
}

@Composable
private fun AboutTab(innerPadding: PaddingValues, showLicense: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(innerPadding)
            .padding(Dimens.screenContentPadding()),
        verticalArrangement = Arrangement.spacedBy(Dimens.SpacingSmall),
    ) {
        val linkStyles = TextLinkStyles(style = SpanStyle(color = MaterialTheme.colorScheme.secondary))
        val text = buildAnnotatedString {
            withStyle(ParagraphStyle()) {
                append("\u00A9 2017-2025 Alexey Rochev <")
                withLink(LinkAnnotation.Url(EMAIL_URL, linkStyles)) {
                    append(EMAIL)
                }
                appendLine(">")
            }
            pushStyle(ParagraphStyle())
            appendLineContainingLinkAndAnnotateIt(
                stringResource(R.string.source_code_url, SOURCE_CODE_URL),
                SOURCE_CODE_URL,
                linkStyles
            )
            pop()
            pushStyle(ParagraphStyle())
            appendLineContainingLinkAndAnnotateIt(
                stringResource(R.string.translations_url, TRANSLATIONS_URL),
                TRANSLATIONS_URL,
                linkStyles
            )
            pop()
        }
        SelectionContainer {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium
            )
        }

        OutlinedButton(onClick = showLicense, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.license))
        }
    }
}

private fun AnnotatedString.Builder.appendLineContainingLinkAndAnnotateIt(
    line: String,
    url: String,
    linkStyles: TextLinkStyles
) {
    val lengthBeforeAppend = length
    appendLine(line)
    val start = line.indexOf(url).takeIf { it != -1 }?.plus(lengthBeforeAppend)
    if (start != null) {
        addLink(LinkAnnotation.Url(url, linkStyles), start, start + url.length)
    }
}

private const val EMAIL = "equeim@gmail.com"
private const val EMAIL_URL = "mailto:$EMAIL"
private const val SOURCE_CODE_URL = "https://github.com/equeim/tremotesf-android"
private const val TRANSLATIONS_URL = "https://www.transifex.com/equeim/tremotesf-android"

@Composable
private fun AuthorsTab(innerPadding: PaddingValues) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(innerPadding)
            .padding(Dimens.screenContentPadding())
    ) {
        val linkStyles = TextLinkStyles(style = SpanStyle(color = MaterialTheme.colorScheme.secondary))
        val text = buildAnnotatedString {
            for (author in AUTHORS) {
                pushStyle(ParagraphStyle())
                append(author.name)
                append(" <")
                withLink(LinkAnnotation.Url("mailto:${author.email}", linkStyles)) {
                    append(author.email)
                }
                appendLine(">")
                withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                    appendLine(
                        stringResource(
                            when (author.type) {
                                Author.Type.Maintainer -> R.string.maintainer
                                Author.Type.Contributor -> R.string.contributor
                            }
                        )
                    )
                }
                pop()
            }
        }
        SelectionContainer {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

private data class Author(
    val name: String,
    val email: String,
    val type: Type,
) {
    enum class Type {
        Maintainer,
        Contributor,
    }
}

private val AUTHORS = listOf(
    Author(
        name = "Alexey Rochev",
        email = EMAIL,
        type = Author.Type.Maintainer,
    ),
    Author(
        name = "Kevin Richter",
        email = "me@kevinrichter.nl",
        type = Author.Type.Contributor,
    )
)

@Composable
private fun TranslatorsTab(innerPadding: PaddingValues) {
    val context = LocalContext.current
    val text = remember {
        context.resources.openRawResource(R.raw.translators).use { it.reader().readText() }
    }
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(innerPadding)
            .padding(Dimens.screenContentPadding())
    ) {
        SelectionContainer {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

class LicenceFragment : ComposeFragment() {
    @Composable
    override fun Content(navController: NavController) {
        LicenseScreen(navController::navigateUp)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LicenseScreen(navigateUp: () -> Unit) {
    Scaffold(
        topBar = { TremotesfTopAppBar(stringResource(R.string.license), navigateUp) }
    ) { innerPadding ->
        val layoutDirection = LocalLayoutDirection.current
        val screenPadding = Dimens.screenContentPadding()
        AndroidView(
            modifier = Modifier
                .consumeWindowInsets(innerPadding)
                .padding(top = innerPadding.calculateTopPadding())
                .fillMaxSize(),
            factory = { createLicenseWebView(it) ?: View(it) },
            update = { (it as? WebView)?.setinnerPadding(innerPadding, screenPadding, layoutDirection) }
        )
    }
}

private fun createLicenseWebView(context: Context): WebView? {
    val view = try {
        WebView(context)
    } catch (e: Exception) {
        Timber.e(e, "Failed to create WebView")
        return null
    }
    view.apply {
        @SuppressLint("SetJavaScriptEnabled")
        settings.javaScriptEnabled = true
        clipToPadding = false
        try {
            if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
                WebSettingsCompat.setAlgorithmicDarkeningAllowed(settings, true)
            }
        } catch (e: Throwable) {
            Timber.e(e, "Failed to enable algorithmic darkening on WebView")
        }
        val html = context.resources.openRawResource(R.raw.license).use { it.reader().readText() }
        loadData(html, "text/html", null)
    }
    return view
}

private fun WebView.setinnerPadding(
    scaffoldInnerPadding: PaddingValues,
    screenPadding: PaddingValues,
    layoutDirection: LayoutDirection,
) {
    val top = screenPadding.calculateTopPadding().value
    val right = (scaffoldInnerPadding.calculateRightPadding(layoutDirection) +
            screenPadding.calculateRightPadding(layoutDirection)).value
    val bottom = (scaffoldInnerPadding.calculateBottomPadding() + screenPadding.calculateBottomPadding()).value
    val left = (scaffoldInnerPadding.calculateLeftPadding(layoutDirection) +
            screenPadding.calculateLeftPadding(layoutDirection)).value
    val script = """
        document.body.style.margin = "${top}px ${right}px ${bottom}px ${left}px"
    """.trimIndent()
    evaluateJavascript(script) {}
}

private enum class Tab {
    About,
    Authors,
    Translators
}

@Preview
@Composable
private fun AboutScreenPreview() = ScreenPreview {
    AboutScreen(
        navigateUp = {},
        showLicense = {}
    )
}
