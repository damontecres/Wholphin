package com.github.damontecres.wholphin.services

import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.services.hilt.AuthOkHttpClient
import com.github.damontecres.wholphin.util.HomeRowLoadingState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import org.jellyfin.sdk.api.client.util.AuthorizationHeaderBuilder
import org.jellyfin.sdk.model.ClientInfo
import org.jellyfin.sdk.model.DeviceInfo
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemDtoQueryResult
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.request.GetItemsRequest
import org.jellyfin.sdk.model.serializer.toUUIDOrNull
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

data class CustomHomeRow(
    val sectionId: String,
    val row: HomeRowLoadingState,
)

/**
 * Service for fetching custom home screen sections from the Home Screen Sections plugin.
 * Falls back gracefully if the plugin is not available.
 */
@Singleton
class HomeScreenSectionsService
    @Inject
    constructor(
        private val api: ApiClient,
        @param:AuthOkHttpClient private val okHttpClient: OkHttpClient,
        private val clientInfo: ClientInfo,
        private val deviceInfo: DeviceInfo,
    ) {
        companion object {
            // Section types to exclude (not supported in Wholphin)
            private val EXCLUDED_SECTIONS = setOf(
                "RecentlyAddedAlbums",
                "RecentlyAddedArtists",
                "RecentlyAddedBooks",
                "RecentlyAddedAudiobooks",
                "RecentlyAddedMusicVideos",
                "LatestAlbums",
                "LatestBooks",
                "LatestAudiobooks",
                "LatestMusicVideos",
                "MyList",
                "Discover",
                "DiscoverMovies",
                "DiscoverTV",
                "DiscoverTVShows",
                "UpcomingShows",
                "UpcomingMovies",
                "UpcomingBooks",
                "UpcomingMusic",
                "MyRequests",
            )
        }
        /**
         * Fetches custom home screen sections from the plugin.
         * Returns null if the plugin is not available or not configured.
         */
        suspend fun getCustomSections(userId: UUID): List<CustomHomeRow>? =
            withContext(Dispatchers.IO) {
                try {
                    Timber.d("HomeScreenSectionsService: getCustomSections called for userId=$userId")
                    val baseUrl = api.baseUrl
                    val accessToken = api.accessToken

                    if (baseUrl.isNullOrBlank() || accessToken.isNullOrBlank()) {
                        Timber.w("HomeScreenSectionsService: No base URL or access token. baseUrl=$baseUrl, accessToken=${if (accessToken.isNullOrBlank()) "null/blank" else "present"}")
                        return@withContext null
                    }

                    Timber.d("HomeScreenSectionsService: Fetching sections from $baseUrl")
                    // Fetch available sections
                    val sections = fetchAvailableSections(baseUrl, accessToken, userId)
                    if (sections == null) {
                        Timber.d("HomeScreenSectionsService: No sections returned from fetchAvailableSections")
                        return@withContext null
                    }
                    
                    Timber.d("HomeScreenSectionsService: Found ${sections.size} total sections")

                    // Filter out unsupported section types
                    val supportedSections = sections.filter { section ->
                        val isSupported = section.id !in EXCLUDED_SECTIONS
                        if (!isSupported) {
                            Timber.d("HomeScreenSectionsService: Excluding unsupported section: ${section.id}")
                        }
                        isSupported
                    }
                    Timber.d("HomeScreenSectionsService: ${supportedSections.size} supported sections after filtering")

                    // Fetch items for each enabled section
                    val rows = supportedSections
                        .filter { it.enabled }
                        .mapNotNull { section ->
                            try {
                                val items = fetchSectionItems(baseUrl, accessToken, section, userId)
                                if (items.isNotEmpty()) {
                                    CustomHomeRow(
                                        sectionId = section.id,
                                        row =
                                            HomeRowLoadingState.Success(
                                                title = section.displayText,
                                                items = items,
                                            ),
                                    )
                                } else {
                                    null
                                }
                            } catch (ex: Exception) {
                                Timber.e(ex, "Error fetching section ${section.id}")
                                CustomHomeRow(
                                    sectionId = section.id,
                                    row =
                                        HomeRowLoadingState.Error(
                                            title = section.displayText,
                                            exception = ex,
                                        ),
                                )
                            }
                        }

                    if (rows.isEmpty()) {
                        Timber.w("HomeScreenSectionsService: No enabled sections found after filtering")
                        return@withContext null
                    }

                    Timber.i("HomeScreenSectionsService: Returning ${rows.size} custom sections")
                    return@withContext rows
                } catch (ex: Exception) {
                    Timber.e(ex, "HomeScreenSectionsService: Plugin not available or error occurred")
                    return@withContext null
                }
            }

        private fun fetchAvailableSections(
            baseUrl: String,
            accessToken: String,
            userId: UUID,
        ): List<HomeSection>? {
            return try {
                // The endpoint is /HomeScreen/Sections with UserId parameter (capital U)
                val url = "$baseUrl/HomeScreen/Sections?UserId=$userId"
                
                Timber.d("HomeScreenSectionsService: Fetching sections from: $url")
                val authHeader = AuthorizationHeaderBuilder.buildHeader(
                    clientName = clientInfo.name,
                    clientVersion = clientInfo.version,
                    deviceId = deviceInfo.id,
                    deviceName = deviceInfo.name,
                    accessToken = accessToken,
                )
                val request =
                    Request
                        .Builder()
                        .url(url)
                        .addHeader("Authorization", authHeader)
                        .get()
                        .build()

                okHttpClient.newCall(request).execute().use { response ->
                    Timber.d("HomeScreenSectionsService: Response status: ${response.code}")
                    if (response.isSuccessful) {
                        val jsonString = response.body.string()
                        Timber.d("HomeScreenSectionsService: Response body length: ${jsonString.length}")
                        val sections = parseSectionsResponse(jsonString)
                        if (sections != null && sections.isNotEmpty()) {
                            Timber.i("HomeScreenSectionsService: Successfully fetched ${sections.size} sections")
                            return sections
                        } else {
                            Timber.w("HomeScreenSectionsService: No sections parsed from response")
                            null
                        }
                    } else {
                        Timber.w("HomeScreenSectionsService: Failed to fetch sections, status=${response.code}, message=${response.message}")
                        null
                    }
                }
            } catch (ex: Exception) {
                Timber.e(ex, "HomeScreenSectionsService: Error fetching available sections")
                null
            }
        }

        private fun parseSectionsResponse(jsonString: String): List<HomeSection>? {
            return try {
                val json = Json.parseToJsonElement(jsonString)
                
                // Response has Items array (capital I)
                val sectionsArray = json.jsonObject["Items"]?.jsonArray
                    ?: json.jsonObject["items"]?.jsonArray
                    ?: (json as? kotlinx.serialization.json.JsonArray)
                
                if (sectionsArray == null) {
                    Timber.w("HomeScreenSectionsService: Could not find Items array in response")
                    return null
                }
                
                Timber.d("HomeScreenSectionsService: Found ${sectionsArray.size} sections")
                val sections = sectionsArray.mapNotNull { element ->
                    val obj = element.jsonObject
                    // The section ID is in the "Section" field (capital S), not "id"
                    val sectionId = obj["Section"]?.jsonPrimitive?.contentOrNull
                    if (sectionId == null) {
                        Timber.w("HomeScreenSectionsService: Section missing Section field")
                        return@mapNotNull null
                    }
                    // All sections in the response are enabled (no enabled field)
                    val section = HomeSection(
                        id = sectionId,
                        displayText = obj["DisplayText"]?.jsonPrimitive?.contentOrNull ?: "",
                        enabled = true, // All sections returned are enabled
                        limit = obj["Limit"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 1,
                        route = obj["Route"]?.jsonPrimitive?.contentOrNull,
                        additionalData = obj["AdditionalData"]?.jsonPrimitive?.contentOrNull,
                        originalPayloadId =
                            obj["OriginalPayload"]
                                ?.jsonObject
                                ?.get("Id")
                                ?.jsonPrimitive
                                ?.contentOrNull,
                    )
                    Timber.d("HomeScreenSectionsService: Parsed section: ${section.id} - ${section.displayText}")
                    section
                }
                
                sections
            } catch (ex: Exception) {
                Timber.e(ex, "HomeScreenSectionsService: Error parsing sections response")
                null
            }
        }

        private suspend fun fetchSectionItems(
            baseUrl: String,
            accessToken: String,
            section: HomeSection,
            userId: UUID,
        ): List<BaseItem> {
            // The endpoint is /HomeScreen/Section/{Section} with UserId parameter (capital U)
            // Some sections (e.g. BecauseYouWatched) also require AdditionalData.
            val base = baseUrl.toHttpUrlOrNull()
            if (base == null) {
                Timber.w("HomeScreenSectionsService: Invalid baseUrl=$baseUrl")
                return emptyList()
            }
            val url =
                base
                    .newBuilder()
                    .addPathSegment("HomeScreen")
                    .addPathSegment("Section")
                    .addPathSegment(section.id)
                    .addQueryParameter("UserId", userId.toString())
                    .apply {
                        section.additionalData?.takeIf { it.isNotBlank() }?.let {
                            addQueryParameter("AdditionalData", it)
                        }
                        if (section.limit > 0) addQueryParameter("Limit", section.limit.toString())
                    }.build()
            Timber.d("HomeScreenSectionsService: Fetching section items from: $url")
            val authHeader = AuthorizationHeaderBuilder.buildHeader(
                clientName = clientInfo.name,
                clientVersion = clientInfo.version,
                deviceId = deviceInfo.id,
                deviceName = deviceInfo.name,
                accessToken = accessToken,
            )
            val request =
                Request
                    .Builder()
                    .url(url)
                    .addHeader("Authorization", authHeader)
                    .get()
                    .build()

            return okHttpClient.newCall(request).execute().use { response ->
                Timber.d("HomeScreenSectionsService: Section items response status: ${response.code}")
                if (response.isSuccessful) {
                    val jsonString = response.body.string()
                    Timber.d("HomeScreenSectionsService: Section items response length: ${jsonString.length}")
                    
                    // The plugin should return a QueryResult<BaseItemDto> which has an Items property
                    val items = try {
                        // Try to parse as QueryResult first
                        val queryResult = Json.decodeFromString<BaseItemDtoQueryResult>(jsonString)
                        Timber.d("HomeScreenSectionsService: Parsed as QueryResult with ${queryResult.items.size} items")
                        queryResult.items.mapNotNull { itemDto ->
                            try {
                                BaseItem.from(itemDto, api, true)
                            } catch (ex: Exception) {
                                Timber.e(ex, "Error creating BaseItem from BaseItemDto")
                                null
                            }
                        }
                    } catch (ex: Exception) {
                        Timber.d(ex, "HomeScreenSectionsService: Not a QueryResult, trying direct array")
                        // Fallback: try to parse as direct array
                        try {
                            val json = Json.parseToJsonElement(jsonString)
                            val itemsArray = when {
                                json is kotlinx.serialization.json.JsonArray -> json
                                json.jsonObject.containsKey("Items") -> json.jsonObject["Items"]?.jsonArray
                                json.jsonObject.containsKey("items") -> json.jsonObject["items"]?.jsonArray
                                else -> null
                            }
                            
                            if (itemsArray == null) {
                                Timber.w("HomeScreenSectionsService: Could not find items array in response")
                                return@use emptyList<BaseItem>()
                            }
                            
                            Timber.d("HomeScreenSectionsService: Parsed as array with ${itemsArray.size} items")
                            itemsArray.mapNotNull { element ->
                                try {
                                    val itemJsonString = Json.encodeToString(kotlinx.serialization.json.JsonElement.serializer(), element)
                                    val itemDto = Json.decodeFromString<BaseItemDto>(itemJsonString)
                                    BaseItem.from(itemDto, api, true)
                                } catch (ex2: Exception) {
                    Timber.e(ex2, "Error parsing item in section ${section.id}")
                                    null
                                }
                            }
                        } catch (ex2: Exception) {
            Timber.e(ex2, "Error parsing section items response for ${section.id}")
                            emptyList()
                        }
                    }
                    
                    Timber.d("HomeScreenSectionsService: Successfully parsed ${items.size} items for section ${section.id}")
                    // If this row is backed by a Collection Sections entry, append a trailing item
                    // that opens the full collection (box set). The plugin often only returns the
                    // first N items from the collection.
                    // Collection Sections tends to put the collection id in OriginalPayload.Id (32-char server id).
                    // AdditionalData is used by other section types (e.g. BecauseYouWatched), so try both.
                    val maybeCollectionIdFromSection =
                        parseAnyIdToUuidOrNull(section.originalPayloadId)
                            ?: parseAnyIdToUuidOrNull(section.additionalData)
                    // Fallback: infer from returned items. For items that are members of a collection,
                    // Jellyfin commonly sets ParentId to the BoxSet id.
                    val maybeCollectionIdFromItems =
                        items
                            .asSequence()
                            .mapNotNull { it.data.parentId }
                            .firstOrNull()

                    val candidateCollectionIds =
                        listOfNotNull(maybeCollectionIdFromSection, maybeCollectionIdFromItems)
                            .distinct()

                    Timber.d(
                        "HomeScreenSectionsService: view-all candidates for %s: fromSection=%s, fromItems=%s, rawOriginalPayloadId=%s, rawAdditionalData=%s",
                        section.id,
                        maybeCollectionIdFromSection,
                        maybeCollectionIdFromItems,
                        section.originalPayloadId,
                        section.additionalData,
                    )

                    for (candidateId in candidateCollectionIds) {
                        try {
                            val boxSetDto = api.userLibraryApi.getItem(candidateId).content
                            if (boxSetDto.type == BaseItemKind.BOX_SET) {
                                Timber.d("HomeScreenSectionsService: Appending collection link item for $candidateId")
                                val viewAllDto = boxSetDto.copy(name = "View All")
                                return@use items + BaseItem.from(viewAllDto, api, useSeriesForPrimary = false)
                            }
                        } catch (ex: Exception) {
                            Timber.d(
                                ex,
                                "HomeScreenSectionsService: Failed fetching collection item for id=$candidateId",
                            )
                        }
                    }

                    // Last-chance fallback for custom collection rows where we only have a name.
                    // Some Collection Sections setups provide AdditionalData as the collection name.
                    val maybeCollectionName = section.additionalData?.trim().orEmpty()
                    if (candidateCollectionIds.isEmpty() && maybeCollectionName.isNotBlank()) {
                        try {
                            Timber.d(
                                "HomeScreenSectionsService: Attempting to resolve collection by name \"$maybeCollectionName\"",
                            )
                            val searchResult =
                                api.itemsApi
                                    .getItems(
                                        GetItemsRequest(
                                            searchTerm = maybeCollectionName,
                                            includeItemTypes = listOf(BaseItemKind.BOX_SET),
                                            recursive = true,
                                            limit = 10,
                                        ),
                                    ).content.items
                            val match =
                                searchResult.firstOrNull {
                                    it.type == BaseItemKind.BOX_SET &&
                                        it.name?.equals(maybeCollectionName, ignoreCase = true) == true
                                } ?: searchResult.firstOrNull { it.type == BaseItemKind.BOX_SET }

                            if (match?.id != null) {
                                Timber.d(
                                    "HomeScreenSectionsService: Resolved collection by name to id=${match.id}",
                                )
                                val viewAllDto = match.copy(name = "View All")
                                return@use items + BaseItem.from(viewAllDto, api, useSeriesForPrimary = false)
                            }
                        } catch (ex: Exception) {
                            Timber.d(ex, "HomeScreenSectionsService: Failed resolving collection by name")
                        }
                    }

                    items
                } else {
                    Timber.w("HomeScreenSectionsService: Failed to fetch section items, status=${response.code}")
                    emptyList()
                }
            }
        }

        private fun parseAnyIdToUuidOrNull(raw: String?): UUID? {
            val v = raw?.trim().orEmpty()
            if (v.isBlank()) return null

            // First try Jellyfin SDK helper (handles UUID-ish strings).
            v.toUUIDOrNull()?.let { return it }

            // Jellyfin sometimes uses a 32-char hex string without dashes.
            if (v.length == 32 && v.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) {
                val dashed =
                    buildString(36) {
                        append(v.take(8))
                        append('-')
                        append(v.substring(8, 12))
                        append('-')
                        append(v.substring(12, 16))
                        append('-')
                        append(v.substring(16, 20))
                        append('-')
                        append(v.substring(20, 32))
                    }
                return try {
                    UUID.fromString(dashed)
                } catch (_: IllegalArgumentException) {
                    null
                }
            }

            return null
        }
    }

@Serializable
private data class HomeSection(
    val id: String,
    @SerialName("displayText") val displayText: String,
    val enabled: Boolean,
    val limit: Int,
    val route: String? = null,
    val additionalData: String? = null,
    val originalPayloadId: String? = null,
)
