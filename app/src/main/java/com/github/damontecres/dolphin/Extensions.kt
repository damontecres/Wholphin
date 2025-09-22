package com.github.damontecres.dolphin

import androidx.compose.ui.focus.FocusRequester
import timber.log.Timber
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

@OptIn(ExperimentalContracts::class)
fun CharSequence?.isNotNullOrBlank(): Boolean {
    contract {
        returns(true) implies (this@isNotNullOrBlank != null)
    }
    return !this.isNullOrBlank()
}

/**
 * Try to call [FocusRequester.requestFocus], but catch & log the exception if something is not configured properly
 */
fun FocusRequester.tryRequestFocus(): Boolean =
    try {
        requestFocus()
        true
    } catch (ex: IllegalStateException) {
        Timber.w(ex, "Failed to request focus")
        false
    }
