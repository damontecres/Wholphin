package com.github.damontecres.wholphin.ui.setup.seerr

import androidx.lifecycle.ViewModel
import com.github.damontecres.wholphin.services.SeerrServerRepository
import com.github.damontecres.wholphin.services.SeerrService
import dagger.hilt.android.lifecycle.HiltViewModel
import jakarta.inject.Inject

@HiltViewModel
class SwitchSeerrViewModel
    @Inject
    constructor(
        private val seerrServerRepository: SeerrServerRepository,
        private val seerrService: SeerrService,
    ) : ViewModel() {
        fun submitServer(
            url: String,
            apiKey: String,
        ) {
        }
    }
