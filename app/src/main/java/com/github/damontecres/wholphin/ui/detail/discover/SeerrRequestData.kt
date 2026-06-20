package com.github.damontecres.wholphin.ui.detail.discover

data class SeerrRequestData(
    val serverId: Int = -1,
    val server4kId: Int = -1,
    val profiles: List<SeerrProfile> = emptyList(),
    val rootFolders: List<SeerrRootFolder> = emptyList(),
    val profiles4k: List<SeerrProfile> = emptyList(),
    val rootFolders4k: List<SeerrRootFolder> = emptyList(),
)

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

data class MovieRequest(
    val data: SeerrRequestData,
    val movieId: Int?,
    val is4k: Boolean,
    val profileId: Int?,
    val folder: String?,
)

data class TvRequest(
    val data: SeerrRequestData,
    val tvId: Int,
    val seasons: List<Int>,
    val is4k: Boolean,
    val profileId: Int?,
    val folder: String?,
)
