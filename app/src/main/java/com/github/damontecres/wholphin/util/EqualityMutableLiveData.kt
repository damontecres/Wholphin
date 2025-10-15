package com.github.damontecres.wholphin.util

import androidx.lifecycle.MutableLiveData

/**
 * A [MutableLiveData] that only notifies observers if the value does not equal the previous value
 */
class EqualityMutableLiveData<T> : MutableLiveData<T> {
    constructor() : super()
    constructor(value: T) : super(value)

    override fun setValue(value: T?) {
        if (value != getValue()) {
            super.setValue(value)
        }
    }

    fun setValueNoCheck(value: T?) {
        super.setValue(value)
    }

    override fun postValue(value: T?) {
        if (value != getValue()) {
            super.postValue(value)
        }
    }
}
