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

package org.equeim.tremotesf.utils

import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer


class NonNullMutableLiveData<T : Any>(value: T) : MutableLiveData<T>(value) {
    override fun getValue() = checkNotNull(super.getValue())
    //override fun setValue(value: T) = super.setValue(value)

    inline fun observe(owner: LifecycleOwner, crossinline onChanged: (T) -> Unit) {
        observe(owner, Observer { onChanged(it) })
    }

    inline fun observeForever(crossinline onChanged: (t: T) -> Unit) {
        observeForever(Observer { onChanged(it) })
    }
}

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

// LiveData that sets its value to null after calling all observers
class LiveEvent<T> : LiveData<T>(null) {
    private val eventObservers = mutableListOf<ObserverWrapper<T>>()

    override fun observe(owner: LifecycleOwner, observer: Observer<in T>) {
        if (!hasObservers()) {
            value = null
        }
        val eventObserver = ObserverWrapper(observer)
        eventObservers.add(eventObserver)
        super.observe(owner, eventObserver)
    }

    override fun observeForever(observer: Observer<in T>) {
        if (!hasObservers()) {
            value = null
        }
        val eventObserver = ObserverWrapper(observer)
        eventObservers.add(eventObserver)
        super.observeForever(eventObserver)
    }

    override fun removeObserver(observer: Observer<in T>) {
        eventObservers.removeAll { it.observer == observer }
        super.removeObserver(observer)
        if (!hasObservers()) {
            value = null
        }
    }

    fun emit(value: T) {
        this.value = value
    }

    private fun resetValueIfAllCalled() {
        if (eventObservers.last().called) {
            value = null
        }
    }

    private inner class ObserverWrapper<T>(val observer: Observer<in T>) : Observer<T> {
        var called = false
            private set

        override fun onChanged(t: T) {
            if (t != null) {
                observer.onChanged(t)
                called = true
                resetValueIfAllCalled()
            } else {
                called = false
            }
        }
    }
}

fun LiveEvent<Unit>.emit() {
    emit(Unit)
}