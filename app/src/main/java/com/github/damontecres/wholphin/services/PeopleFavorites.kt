package com.github.damontecres.wholphin.services

import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.data.model.Person
import com.github.damontecres.wholphin.ui.letNotEmpty
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.itemsApi
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PeopleFavorites
    @Inject
    constructor(
        private val api: ApiClient,
    ) {
        suspend fun getPeopleFor(item: BaseItem): List<Person> =
            item.data.people.orEmpty().map { it.id }.chunked(50).let { chunks ->
                val favorites =
                    chunks
                        .map {
                            api.itemsApi
                                .getItems(ids = it)
                                .content.items
                        }.flatten()
                        .associateBy({ it.id }, { it.userData?.isFavorite ?: false })
                val people =
                    item.data.people
                        ?.letNotEmpty { people ->
                            people.map { Person.fromDto(it, favorites[it.id] ?: false, api) }
                        }.orEmpty()
                people
            }
    }
