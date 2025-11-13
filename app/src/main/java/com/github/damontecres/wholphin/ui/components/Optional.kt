package com.github.damontecres.wholphin.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class Optional<T> private constructor(
    initialValue: T?,
) {
    private var value by mutableStateOf(initialValue)

    @Composable
    fun compose(run: @Composable (T) -> Unit) {
        value?.let { run.invoke(it) }
    }

    fun makeAbsent() {
        value = null
    }

    fun makePresent(value: T) {
        this.value = value
    }

    companion object {
        fun <T> absent() = Optional<T>(null)

        fun <T> present(value: T) = Optional(value)
    }
}
