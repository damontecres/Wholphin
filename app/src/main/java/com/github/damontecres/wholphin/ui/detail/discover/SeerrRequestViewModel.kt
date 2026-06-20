package com.github.damontecres.wholphin.ui.detail.discover

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.damontecres.wholphin.api.seerr.model.RootFolder
import com.github.damontecres.wholphin.api.seerr.model.ServiceProfile
import com.github.damontecres.wholphin.data.model.SeerrItemType
import com.github.damontecres.wholphin.data.model.SeerrPermission
import com.github.damontecres.wholphin.data.model.hasPermission
import com.github.damontecres.wholphin.services.SeerrServerRepository
import com.github.damontecres.wholphin.services.SeerrService
import com.github.damontecres.wholphin.ui.formatBytes
import com.github.damontecres.wholphin.ui.launchIO
import com.github.damontecres.wholphin.util.LoadingState
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update

@HiltViewModel(assistedFactory = SeerrRequestViewModel.Factory::class)
class SeerrRequestViewModel
    @AssistedInject
    constructor(
        private val seerrService: SeerrService,
        private val seerrServerRepository: SeerrServerRepository,
        @Assisted private val id: Int,
        @Assisted private val type: SeerrItemType,
        @Assisted private val is4k: Boolean,
    ) : ViewModel() {
        @AssistedFactory
        interface Factory {
            fun create(
                id: Int,
                type: SeerrItemType,
                is4k: Boolean,
            ): SeerrRequestViewModel
        }

        private val _state = MutableStateFlow(SeerrRequestState())
        val state: StateFlow<SeerrRequestState> = _state

        init {
            viewModelScope.launchIO {
                val current = seerrServerRepository.current.firstOrNull()
                if (current != null && current.config.hasPermission(SeerrPermission.ADMIN)) {
                    if (type == SeerrItemType.MOVIE) {
                        val radarrs = seerrService.api.serviceApi.serviceRadarrGet()
                        val radarr = radarrs.firstOrNull { it.isDefault && !it.is4k } ?: radarrs.firstOrNull { !it.is4k }
                        val radarr4k = radarrs.firstOrNull { it.isDefault && it.is4k } ?: radarrs.firstOrNull { it.is4k }

                        if (radarr != null && radarr.id != null) {
                            val settings = seerrService.api.serviceApi.serviceRadarrRadarrIdGet(radarr.id)

                            setProfilesAndFolder(
                                settings.profiles,
                                settings.rootFolders,
                                settings.server?.activeProfileId,
                                settings.server?.activeDirectory,
                            ) { profiles, rootFolders ->
                                _state.update {
                                    it.copy(
                                        profiles = profiles,
                                        rootFolders = rootFolders,
                                    )
                                }
                            }
                        }
                        if (radarr4k != null && radarr4k.id != null) {
                            val settings = seerrService.api.serviceApi.serviceRadarrRadarrIdGet(radarr4k.id)
                            val profilesAndFolders =
                                setProfilesAndFolder(
                                    settings.profiles,
                                    settings.rootFolders,
                                    settings.server?.activeProfileId,
                                    settings.server?.activeDirectory,
                                ) { profiles, rootFolders ->
                                    _state.update {
                                        it.copy(
                                            profiles4k = profiles,
                                            rootFolders4k = rootFolders,
                                        )
                                    }
                                }
                        }
                    } else {
                    }
                }
            }
            _state.update {
                it.copy(loading = LoadingState.Success)
            }
        }

        private fun setProfilesAndFolder(
            profiles: List<ServiceProfile>?,
            rootFolders: List<RootFolder>?,
            activeProfileId: Int?,
            activeDirectory: String?,
            setter: (List<SeerrProfile>, List<SeerrRootFolder>) -> Unit,
        ) {
            val profiles =
                profiles.orEmpty().mapNotNull {
                    if (it.id != null && it.name != null) {
                        SeerrProfile(it.id, it.name, activeProfileId == it.id)
                    } else {
                        null
                    }
                }
            val rootFolders =
                rootFolders.orEmpty().mapNotNull {
                    if (it.id != null && it.path != null) {
                        val freeSpace = it.freeSpace?.let { formatBytes(it) } ?: ""
                        SeerrRootFolder(it.id, it.path, freeSpace, activeDirectory == it.path)
                    } else {
                        null
                    }
                }
            setter.invoke(profiles, rootFolders)
        }

        fun submitRequest() {
        }
    }

data class SeerrProfile(
    val id: Int,
    val name: String,
    val default: Boolean,
)

data class SeerrRootFolder(
    val id: Int,
    val path: String,
    val freeSpace: String,
    val default: Boolean,
)

data class SeerrRequestState(
    val loading: LoadingState = LoadingState.Pending,
    val profiles: List<SeerrProfile> = emptyList(),
    val rootFolders: List<SeerrRootFolder> = emptyList(),
    val profiles4k: List<SeerrProfile> = emptyList(),
    val rootFolders4k: List<SeerrRootFolder> = emptyList(),
)

data class MovieRequest(
    val movieId: Int?,
    val is4k: Boolean,
    val profileId: Int?,
    val folderId: Int?,
)

data class TvRequest(
    val tvId: Int,
    val seasons: List<Int>,
    val is4k: Boolean,
    val profileId: Int?,
    val folderId: Int?,
)
