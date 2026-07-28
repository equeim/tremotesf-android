// SPDX-FileCopyrightText: 2017-2026 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package org.equeim.tremotesf.ui

import android.os.ParcelUuid
import android.os.Parcelable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.ViewModelStoreOwner
import kotlinx.parcelize.Parcelize
import org.equeim.tremotesf.ui.NavController.BackStackEntry
import java.util.UUID

interface Destination : Parcelable {
    @Composable
    fun Content(navController: NavController)

    val metadata: Map<String, Any>
        get() = emptyMap()
}

@Stable
class NavController(
    initialBackStack: List<BackStackEntry>,
    private val viewModelStoreDecorator: ExtendedViewModelStoreNavEntryDecorator<BackStackEntry>
) {
    val backStack: List<BackStackEntry>
        field = mutableStateListOf<BackStackEntry>().apply { addAll(initialBackStack) }

    fun navigateTo(destination: Destination) {
        backStack.add(BackStackEntry(destination))
    }

    fun popBackStack() {
        backStack.removeLastOrNull()
    }

    fun <T : Destination> popUpTo(destinationClass: Class<T>) {
        val index = backStack.indexOfLast { destinationClass.isInstance(it.destination) }
        if (index in 0..<backStack.lastIndex) {
            backStack.removeRange(index + 1, backStack.size)
        }
    }

    inline fun <reified T : Destination> popUpTo() {
        popUpTo(T::class.java)
    }

    fun resetFirstDestination(destination: Destination) {
        if (backStack.size > 1) {
            backStack.removeRange(1, backStack.size)
        }
        backStack[0] = BackStackEntry(destination)
    }

    @Composable
    fun <T : Destination> viewModelStoreOwnerForDestinationOrNull(destinationClass: Class<T>): ViewModelStoreOwner? {
        val backStackEntry = backStack.findLast { destinationClass.isInstance(it.destination) } ?: return null
        return viewModelStoreDecorator.viewModelStoreOwnerForKey(backStackEntry.contentKey)
    }

    @Composable
    inline fun <reified T : Destination> viewModelStoreOwnerForDestinationOrNull(): ViewModelStoreOwner? =
        viewModelStoreOwnerForDestinationOrNull(T::class.java)

    @Composable
    fun <T : Destination> viewModelStoreOwnerForDestination(destinationClass: Class<T>): ViewModelStoreOwner {
        val owner = viewModelStoreOwnerForDestinationOrNull(destinationClass)
        return checkNotNull(owner) { "Destination of type ${destinationClass.simpleName} does not exist on the back stack\nBack stack: $backStack" }
    }

    @Composable
    inline fun <reified T : Destination> viewModelStoreOwnerForDestination(): ViewModelStoreOwner =
        viewModelStoreOwnerForDestination(T::class.java)

    @Parcelize
    data class BackStackEntry(
        val destination: Destination,
        private val uuid: ParcelUuid = ParcelUuid(UUID.randomUUID())
    ) : Parcelable {
        val contentKey: Any get() = uuid
    }

    companion object {
        fun Saver(viewModelStoreDecorator: ExtendedViewModelStoreNavEntryDecorator<BackStackEntry>) =
            Saver<NavController, List<BackStackEntry>>(
                save = { it.backStack },
                restore = { NavController(initialBackStack = it, viewModelStoreDecorator = viewModelStoreDecorator) }
            )
    }
}

@Composable
fun rememberNavController(
    initialDestinations: List<Destination>,
    viewModelStoreDecorator: ExtendedViewModelStoreNavEntryDecorator<BackStackEntry>
): NavController {
    return rememberSaveable(saver = NavController.Saver(viewModelStoreDecorator)) {
        NavController(
            initialBackStack = initialDestinations.map(::BackStackEntry),
            viewModelStoreDecorator = viewModelStoreDecorator
        )
    }
}
