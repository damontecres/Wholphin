package com.github.damontecres.wholphin.ui.main

import android.content.Context
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.NavDrawerItemRepository
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.preferences.UserPreferences
import com.github.damontecres.wholphin.services.BackdropService
import com.github.damontecres.wholphin.services.DatePlayedService
import com.github.damontecres.wholphin.services.FavoriteWatchManager
import com.github.damontecres.wholphin.services.LatestNextUpService
import com.github.damontecres.wholphin.services.NavigationManager
import com.github.damontecres.wholphin.services.WholphinPluginService
import com.github.damontecres.wholphin.ui.launchIO
import com.github.damontecres.wholphin.ui.nav.ServerNavDrawerItem
import com.github.damontecres.wholphin.ui.setValueOnMain
import com.github.damontecres.wholphin.util.ExceptionHandler
import com.github.damontecres.wholphin.util.HomeRowLoadingState
import com.github.damontecres.wholphin.util.LoadingExceptionHandler
import com.github.damontecres.wholphin.util.LoadingState
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import org.jellyfin.sdk.model.api.CollectionType
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.request.GetLatestMediaRequest
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class HomeViewModel
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        val api: ApiClient,
        val navigationManager: NavigationManager,
        val serverRepository: ServerRepository,
        val navDrawerItemRepository: NavDrawerItemRepository,
        private val favoriteWatchManager: FavoriteWatchManager,
        private val datePlayedService: DatePlayedService,
        private val latestNextUpService: LatestNextUpService,
        private val backdropService: BackdropService,
        private val wholphinPluginService: WholphinPluginService,
    ) : ViewModel() {
        val loadingState = MutableLiveData<LoadingState>(LoadingState.Pending)
        val refreshState = MutableLiveData<LoadingState>(LoadingState.Pending)
        
        // Keep separate for backward compatibility with existing UI
        val watchingRows = MutableLiveData<List<HomeRowLoadingState>>(listOf())
        val latestRows = MutableLiveData<List<HomeRowLoadingState>>(listOf())

        private lateinit var preferences: UserPreferences
        private var usingPluginConfiguration = false
        
        private val json = Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

        init {
            datePlayedService.invalidateAll()
        }

        fun init(preferences: UserPreferences): Job {
            val reload = loadingState.value != LoadingState.Success
            if (reload) {
                loadingState.value = LoadingState.Loading
            }
            refreshState.value = LoadingState.Loading
            this.preferences = preferences
            val prefs = preferences.appPreferences.homePagePreferences
            val limit = prefs.maxItemsPerRow
            
            return viewModelScope.launch(
                Dispatchers.IO +
                    LoadingExceptionHandler(
                        loadingState,
                        "Error loading home page",
                    ),
            ) {
                Timber.d("init HomeViewModel")
                if (reload) {
                    backdropService.clearBackdrop()
                }

                serverRepository.currentUserDto.value?.let { userDto ->
                    // Try to load plugin configuration first
                    val serverUrl = serverRepository.currentServer.value?.url
                    val pluginConfig = if (serverUrl != null) {
                        wholphinPluginService.getHomeConfiguration(serverUrl)
                    } else {
                        null
                    }

                    if (pluginConfig != null && pluginConfig.sections.isNotEmpty()) {
                        // Use plugin-driven configuration
                        Timber.i("Using Wholphin plugin home configuration with ${pluginConfig.sections.size} sections")
                        usingPluginConfiguration = true
                        loadPluginSections(pluginConfig, userDto.id, limit)
                    } else {
                        // Fallback to default behavior
                        Timber.d("Using default home configuration")
                        usingPluginConfiguration = false
                        loadDefaultSections(userDto, prefs, limit, reload)
                    }
                }
            }
        }

        /**
         * Load home sections from plugin configuration
         */
        private suspend fun loadPluginSections(
            config: com.github.damontecres.wholphin.services.HomeConfiguration,
            userId: UUID,
            defaultLimit: Int,
        ) {
            // Show all sections as loading first
            val pendingRows = config.sections.map { 
                HomeRowLoadingState.Loading(it.title) 
            }
            
            withContext(Dispatchers.Main) {
                watchingRows.value = emptyList()
                latestRows.value = pendingRows
                loadingState.value = LoadingState.Success
            }
            
            refreshState.setValueOnMain(LoadingState.Success)
            
            // Load each section based on its type
            val loadedRows = config.sections.map { section ->
                try {
                    val items = when (section.type) {
                        com.github.damontecres.wholphin.services.HomeSectionType.RESUME -> 
                            loadResumeSection(userId, section.limit)
                        
                        com.github.damontecres.wholphin.services.HomeSectionType.NEXT_UP -> 
                            loadNextUpSection(userId, section)
                        
                        com.github.damontecres.wholphin.services.HomeSectionType.LATEST -> 
                            loadLatestSection(userId, section)
                        
                        com.github.damontecres.wholphin.services.HomeSectionType.ITEMS -> 
                            loadItemsSection(userId, section)
                        
                        com.github.damontecres.wholphin.services.HomeSectionType.CUSTOM -> 
                            loadCustomSection(section)
                    }
                    
                    if (items.isNotEmpty()) {
                        HomeRowLoadingState.Success(section.title, items)
                    } else {
                        null // Skip empty sections
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Error loading section ${section.id} (${section.type})")
                    HomeRowLoadingState.Error(
                        title = section.title,
                        exception = e
                    )
                }
            }.filterNotNull()
            
            // Update UI with loaded sections
            latestRows.setValueOnMain(loadedRows)
        }

        /**
         * Load RESUME section (Continue Watching)
         */
        private suspend fun loadResumeSection(userId: UUID, limit: Int): List<BaseItem> {
            return latestNextUpService.getResume(userId, limit, includeEpisodes = true)
        }

        /**
         * Load NEXT_UP section
         */
        private suspend fun loadNextUpSection(
            userId: UUID,
            section: com.github.damontecres.wholphin.services.HomeSection,
        ): List<BaseItem> {
            val enableRewatching = section.query?.enableRewatching ?: false
            val enableResumable = section.query?.enableResumable ?: false
            return latestNextUpService.getNextUp(
                userId, 
                section.limit, 
                enableRewatching,
                enableResumable
            )
        }

        /**
         * Load LATEST section (Recently Added)
         */
        private suspend fun loadLatestSection(
            userId: UUID,
            section: com.github.damontecres.wholphin.services.HomeSection,
        ): List<BaseItem> {
            val parentId = section.query?.parentId?.let { parseUUID(it) }
            
            if (parentId != null) {
                // Single library
                val request = org.jellyfin.sdk.model.api.request.GetLatestMediaRequest(
                    fields = com.github.damontecres.wholphin.ui.SlimItemFields,
                    imageTypeLimit = 1,
                    parentId = parentId,
                    groupItems = true,
                    limit = section.limit,
                    isPlayed = null,
                )
                
                return api.userLibraryApi
                    .getLatestMedia(request)
                    .content
                    .map { BaseItem.from(it, api, true) }
            } else {
                // All libraries - use existing logic
                val user = serverRepository.currentUserDto.value ?: return emptyList()
                val includedIds = navDrawerItemRepository
                    .getFilteredNavDrawerItems(navDrawerItemRepository.getNavDrawerItems())
                    .filter { it is ServerNavDrawerItem }
                    .map { (it as ServerNavDrawerItem).itemId }
                    
                val latestData = latestNextUpService.getLatest(user, section.limit, includedIds)
                val rows = latestNextUpService.loadLatest(latestData)
                
                // Flatten all items from all libraries
                return rows.filterIsInstance<HomeRowLoadingState.Success>()
                    .flatMap { it.items }
                    .filterNotNull()
                    .take(section.limit)
            }
        }

        /**
         * Load ITEMS section (Custom Query)
         */
        private suspend fun loadItemsSection(
            userId: UUID,
            section: com.github.damontecres.wholphin.services.HomeSection,
        ): List<BaseItem> {
            val query = section.query ?: return emptyList()
            
            // Parse filters
            val filters = query.filters?.mapNotNull { filterStr ->
                try {
                    org.jellyfin.sdk.model.api.ItemFilter.valueOf(filterStr)
                } catch (e: Exception) {
                    Timber.w("Unknown filter: $filterStr")
                    null
                }
            }
            
            // Parse item types
            val itemTypes = query.includeItemTypes?.mapNotNull { typeStr ->
                try {
                    org.jellyfin.sdk.model.api.BaseItemKind.valueOf(typeStr.uppercase())
                } catch (e: Exception) {
                    Timber.w("Unknown item type: $typeStr")
                    null
                }
            }
            
            // Parse sort by
            val sortBy = query.sortBy?.mapNotNull { sortStr ->
                ItemSortBy.fromNameOrNull(sortStr).also { sortBy ->
                    if (sortBy == null) {
                        Timber.w("Unknown sort by: $sortStr")
                    }
                }
            }
            
            // Parse sort order
            val sortOrder = query.sortOrder?.let { orderStr ->
                try {
                    listOf(org.jellyfin.sdk.model.api.SortOrder.valueOf(orderStr.uppercase()))
                } catch (e: Exception) {
                    Timber.w("Unknown sort order: $orderStr")
                    null
                }
            }
            
            // Build request
            val request = org.jellyfin.sdk.model.api.request.GetItemsRequest(
                userId = userId,
                parentId = query.parentId?.let { parseUUID(it) },
                filters = filters,
                includeItemTypes = itemTypes,
                sortBy = sortBy,
                sortOrder = sortOrder,
                limit = section.limit,
                fields = com.github.damontecres.wholphin.ui.SlimItemFields,
                enableUserData = true,
                recursive = true,
            )
            
            return api.itemsApi
                .getItems(request)
                .content
                .items
                .map { BaseItem.from(it, api, true) }
        }
        
        /**
         * Parse UUID from string, supporting both formats with and without dashes
         * Jellyfin GUIDs come without dashes from C# (e.g., "39e84a9304c28059a7c197d5d9a5edbe")
         */
        private fun parseUUID(uuidString: String): UUID {
            return if (uuidString.contains("-")) {
                // Already has dashes
                UUID.fromString(uuidString)
            } else if (uuidString.length == 32) {
                // No dashes, add them: xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx
                val formatted = buildString {
                    append(uuidString.substring(0, 8))
                    append('-')
                    append(uuidString.substring(8, 12))
                    append('-')
                    append(uuidString.substring(12, 16))
                    append('-')
                    append(uuidString.substring(16, 20))
                    append('-')
                    append(uuidString.substring(20, 32))
                }
                UUID.fromString(formatted)
            } else {
                throw IllegalArgumentException("Invalid UUID string: $uuidString")
            }
        }

        /**
         * Load CUSTOM section (Plugin Endpoint)
         */
        private suspend fun loadCustomSection(
            section: com.github.damontecres.wholphin.services.HomeSection,
        ): List<BaseItem> {
            val endpoint = section.endpoint ?: run {
                Timber.w("Custom section ${section.id} has no endpoint")
                return emptyList()
            }
            
            val serverUrl = serverRepository.currentServer.value?.url ?: return emptyList()
            
            val response = wholphinPluginService.makeAuthenticatedRequest(serverUrl, endpoint)
            
            if (response != null) {
                try {
                    val body = response.body?.string()
                    if (body != null) {
                        // Expect JSON array of BaseItemDto
                        val items = json.decodeFromString<List<org.jellyfin.sdk.model.api.BaseItemDto>>(body)
                        return items.map { BaseItem.from(it, api, true) }
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Failed to parse custom endpoint response for ${section.id}")
                } finally {
                    response.close()
                }
            }
            
            return emptyList()
        }

        /**
         * Load home sections using default behavior (existing logic)
         */
        private suspend fun loadDefaultSections(
            userDto: org.jellyfin.sdk.model.api.UserDto,
            prefs: com.github.damontecres.wholphin.preferences.HomePagePreferences,
            limit: Int,
            reload: Boolean,
        ) {
            val includedIds =
                navDrawerItemRepository
                    .getFilteredNavDrawerItems(navDrawerItemRepository.getNavDrawerItems())
                    .filter { it is ServerNavDrawerItem }
                    .map { (it as ServerNavDrawerItem).itemId }
            val resume = latestNextUpService.getResume(userDto.id, limit, true)
            val nextUp =
                latestNextUpService.getNextUp(
                    userDto.id,
                    limit,
                    prefs.enableRewatchingNextUp,
                    false,
                )
            val watching =
                buildList {
                    if (prefs.combineContinueNext) {
                        val items = latestNextUpService.buildCombined(resume, nextUp)
                        add(
                            HomeRowLoadingState.Success(
                                title = context.getString(R.string.continue_watching),
                                items = items,
                            ),
                        )
                    } else {
                        if (resume.isNotEmpty()) {
                            add(
                                HomeRowLoadingState.Success(
                                    title = context.getString(R.string.continue_watching),
                                    items = resume,
                                ),
                            )
                        }
                        if (nextUp.isNotEmpty()) {
                            add(
                                HomeRowLoadingState.Success(
                                    title = context.getString(R.string.next_up),
                                    items = nextUp,
                                ),
                            )
                        }
                    }
                }

            val latest = latestNextUpService.getLatest(userDto, limit, includedIds)
            val pendingLatest = latest.map { HomeRowLoadingState.Loading(it.title) }

            withContext(Dispatchers.Main) {
                this@HomeViewModel.watchingRows.value = watching
                if (reload) {
                    this@HomeViewModel.latestRows.value = pendingLatest
                }
                loadingState.value = LoadingState.Success
            }
            refreshState.setValueOnMain(LoadingState.Success)
            val loadedLatest = latestNextUpService.loadLatest(latest)
            this@HomeViewModel.latestRows.setValueOnMain(loadedLatest)
        }

        fun setWatched(
            itemId: UUID,
            played: Boolean,
        ) = viewModelScope.launch(ExceptionHandler() + Dispatchers.IO) {
            favoriteWatchManager.setWatched(itemId, played)
            withContext(Dispatchers.Main) {
                init(preferences)
            }
        }

        fun setFavorite(
            itemId: UUID,
            favorite: Boolean,
        ) = viewModelScope.launch(ExceptionHandler() + Dispatchers.IO) {
            favoriteWatchManager.setFavorite(itemId, favorite)
            withContext(Dispatchers.Main) {
                init(preferences)
            }
        }

        fun updateBackdrop(item: BaseItem) {
            viewModelScope.launchIO {
                backdropService.submit(item)
            }
        }
    }

val supportedLatestCollectionTypes =
    setOf(
        CollectionType.MOVIES,
        CollectionType.TVSHOWS,
        CollectionType.HOMEVIDEOS,
        // Exclude Live TV because a recording folder view will be used instead
        null, // Recordings & mixed collection types
    )

data class LatestData(
    val title: String,
    val request: GetLatestMediaRequest,
)
