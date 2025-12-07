package com.github.damontecres.wholphin.services

import android.content.Context
import android.content.SharedPreferences
import com.github.damontecres.api.seerr.SeerrApiClient
import dagger.hilt.android.qualifiers.ApplicationContext
import okhttp3.OkHttpClient

class SeerrApi(
    @param:ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient,
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("seerr", Context.MODE_PRIVATE)

    var api: SeerrApiClient =
        SeerrApiClient(
            prefs.getString("baseUrl", null)!!,
            prefs.getString("apiKey", null)!!,
            okHttpClient,
        )
        private set

    fun update(
        baseUrl: String,
        apiKey: String,
    ) {
        api = SeerrApiClient(baseUrl, apiKey, okHttpClient)
    }
}
