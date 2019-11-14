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

package org.equeim.tremotesf.utils

import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.Observer


// MediatorLiveData that sets its value from getMediatorValue (or to null if getMediatorValue is null)
// every time its sources change.
// When observer is added to it for the first time, it will get initial value only once
// (as opposed to androidx.lifecycle.MediatorLiveData that will dispatch initial value as many times as there are sources)
class BasicMediatorLiveData<T>(vararg sources: LiveData<*>, private val getMediatorValue: (() -> T)? = null) : MediatorLiveData<T>() {
    private val sourceObservers = mutableListOf<SourceObserver<*>>()

    init {
        for (source in sources) {
            addSource(source)
        }
    }

    override fun <S> addSource(source: LiveData<S>, observer: Observer<in S>) = throw UnsupportedOperationException()

    override fun <S> removeSource(toRemove: LiveData<S>) {
        sourceObservers.removeAll { it.source == toRemove }
        super.removeSource(toRemove)
    }

    private fun <S> addSource(source: LiveData<S>) {
        val wrapper = SourceObserver(source)
        sourceObservers.add(wrapper)
        super.addSource(source, wrapper)
    }

    private inner class SourceObserver<S>(val source: LiveData<S>) : Observer<S> {
        private var gotFirstValue = false

        override fun onChanged(t: S) {
            gotFirstValue = true
            if (sourceObservers.last().gotFirstValue) {
                value = getMediatorValue?.invoke()
            }
        }
    }
}
