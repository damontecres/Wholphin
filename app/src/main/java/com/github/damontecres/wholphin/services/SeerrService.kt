package com.github.damontecres.wholphin.services

import com.github.damontecres.api.seerr.SeerrApiClient

class SeerrService constructor(
    private val api: SeerrApiClient,
) {
    fun init() {
        api.searchApi.discoverTvGet(1)
    }
}
