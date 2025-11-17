package com.github.damontecres.wholphin.services

import com.github.damontecres.wholphin.data.ExtrasItem
import com.github.damontecres.wholphin.data.model.BaseItem
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import org.jellyfin.sdk.model.api.ExtraType
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExtrasService
    @Inject
    constructor(
        private val api: ApiClient,
    ) {
        suspend fun getExtras(itemId: UUID): List<ExtrasItem> {
            val extrasMap =
                api.userLibraryApi
                    .getSpecialFeatures(itemId)
                    .content
                    .filterNot {
                        it.extraType == ExtraType.THEME_SONG ||
                            it.extraType == ExtraType.THEME_VIDEO ||
                            it.extraType == ExtraType.TRAILER
                    }.map { BaseItem.from(it, api) }
                    .groupBy { it.data.extraType ?: ExtraType.UNKNOWN }

            val result =
                extrasMap
                    .mapNotNull { (type, items) ->
                        if (items.size == 1) {
                            ExtrasItem.Single(itemId, type, items.first())
                        } else if (items.size > 1) {
                            ExtrasItem.Group(itemId, type, items)
                        } else {
                            null
                        }
                    }.sortedBy { it.type.sortOrder }
            return result
        }
    }

private val ExtraType.sortOrder: Int
    get() =
        when (this) {
            ExtraType.TRAILER -> 0
            ExtraType.FEATURETTE -> 1
            ExtraType.SHORT -> 2
            ExtraType.CLIP -> 3
            ExtraType.SCENE -> 4
            ExtraType.SAMPLE -> 5
            ExtraType.DELETED_SCENE -> 6
            ExtraType.INTERVIEW -> 7
            ExtraType.BEHIND_THE_SCENES -> 8
            ExtraType.THEME_SONG -> 9
            ExtraType.THEME_VIDEO -> 10
            ExtraType.UNKNOWN -> 11
        }
