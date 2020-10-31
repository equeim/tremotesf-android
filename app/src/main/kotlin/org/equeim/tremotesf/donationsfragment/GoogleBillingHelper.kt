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

import android.app.Activity

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface GoogleBillingHelper {
    enum class PurchaseError {
        None,
        Cancelled,
        Error
    }

    val isSetUp: StateFlow<Boolean>
    val skus: List<SkuData>
    val purchasesUpdatedEvent: Flow<PurchaseError>

    fun launchBillingFlow(skuIndex: Int, activity: Activity): PurchaseError

    fun endConnection()

    data class SkuData(val sku: String,
                       val price: String)
}