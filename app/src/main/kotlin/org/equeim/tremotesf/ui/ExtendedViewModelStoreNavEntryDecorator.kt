// SPDX-FileCopyrightText: 2017-2026 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package org.equeim.tremotesf.ui

import android.app.Application
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.MutableCreationExtras
import androidx.lifecycle.viewmodel.ViewModelStoreProvider
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.rememberViewModelStoreOwner
import androidx.navigation3.runtime.NavEntryDecorator
import androidx.savedstate.compose.LocalSavedStateRegistryOwner

@Composable
fun <T : Any> rememberExtendedViewModelStoreNavEntryDecorator(
    viewModelStoreOwner: ViewModelStoreOwner =
        checkNotNull(LocalViewModelStoreOwner.current) {
            "No ViewModelStoreOwner was provided via LocalViewModelStoreOwner"
        }
): ExtendedViewModelStoreNavEntryDecorator<T> {
    val application = checkNotNull(LocalActivity.current).application
    return remember(viewModelStoreOwner) {
        ExtendedViewModelStoreNavEntryDecorator(
            viewModelStoreOwner = viewModelStoreOwner,
            application = application
        )
    }
}

class ExtendedViewModelStoreNavEntryDecorator<T : Any>(private val viewModelStoreProvider: ViewModelStoreProvider) :
    NavEntryDecorator<T>(
        onPop = viewModelStoreProvider::clearKey,
        decorate = { entry ->
            val childViewModelStoreOwner = rememberViewModelStoreOwner(
                provider = viewModelStoreProvider,
                savedStateRegistryOwner = LocalSavedStateRegistryOwner.current,
                key = entry.contentKey,
            )
            CompositionLocalProvider(LocalViewModelStoreOwner provides childViewModelStoreOwner) {
                entry.Content()
            }
        },
    ) {

    constructor(
        viewModelStoreOwner: ViewModelStoreOwner,
        application: Application,
    ) : this(
        viewModelStoreProvider = ViewModelStoreProvider(
            parentStore = viewModelStoreOwner.viewModelStore,
            parentKey = ExtendedViewModelStoreNavEntryDecorator::class.qualifiedName,
            defaultCreationExtras = MutableCreationExtras().also {
                it[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] = application
            })
    )

    @Composable
    fun viewModelStoreOwnerForKey(contentKey: Any): ViewModelStoreOwner {
        return rememberViewModelStoreOwner(
            provider = viewModelStoreProvider,
            savedStateRegistryOwner = LocalSavedStateRegistryOwner.current,
            key = contentKey
        )
    }
}
