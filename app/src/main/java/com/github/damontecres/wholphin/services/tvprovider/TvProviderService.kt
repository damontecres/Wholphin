package com.github.damontecres.wholphin.services.tvprovider

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TvProviderService
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
    ) {
        init {
        }
    }
