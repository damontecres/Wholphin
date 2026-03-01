package com.github.damontecres.wholphin.ui.detail.music

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.services.MusicService
import com.github.damontecres.wholphin.services.NavigationManager
import com.github.damontecres.wholphin.ui.launchDefault
import com.github.damontecres.wholphin.ui.launchIO
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.util.ApiRequestPager
import com.github.damontecres.wholphin.util.BlockingList
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.model.api.BaseItemKind
import timber.log.Timber
import java.util.UUID

abstract class MusicViewModel(
    internal val api: ApiClient,
    internal val musicService: MusicService,
    internal val navigationManager: NavigationManager,
) : ViewModel() {
    fun play(
        pager: ApiRequestPager<*>,
        startIndex: Int = 0,
        shuffled: Boolean = false,
    ) {
        viewModelScope.launchIO {
            musicService.setQueue(pager, startIndex, shuffled)
        }
    }

    fun play(
        item: BaseItem,
        startIndex: Int = 0,
        shuffled: Boolean = false,
    ) {
        viewModelScope.launchIO {
            Timber.v("Playing %s %s", item.type, item.id)
            when (item.type) {
                BaseItemKind.AUDIO -> {
                    musicService.setQueue(listOf(item), shuffled)
                }

                BaseItemKind.MUSIC_ALBUM -> {
                    val pager = getPagerForAlbum(api, item.id)
                    musicService.setQueue(pager, startIndex, shuffled)
                }

                BaseItemKind.MUSIC_ARTIST -> {
                    val pager = getPagerForArtist(api, item.id)
                    musicService.setQueue(pager, startIndex, shuffled)
                }

                else -> {}
            }
        }
    }

    fun playNext(song: BaseItem) {
        viewModelScope.launchDefault {
            musicService.playNext(song)
        }
    }

    fun addToQueue(
        item: BaseItem,
        index: Int,
    ) {
        viewModelScope.launchIO {
            Timber.v("addToQueue %s %s", item.type, item.id)
            when (item.type) {
                BaseItemKind.AUDIO -> {
                    musicService.addAllToQueue(BlockingList.of(listOf(item)), 0)
                }

                BaseItemKind.MUSIC_ALBUM -> {
                    val pager = getPagerForAlbum(api, item.id)
                    musicService.addAllToQueue(pager, 0)
                }

                BaseItemKind.MUSIC_ARTIST -> {
                    val pager = getPagerForArtist(api, item.id)
                    musicService.addAllToQueue(pager, 0)
                }

                else -> {}
            }
        }
    }

    fun startInstantMix(itemId: UUID) {
        viewModelScope.launchIO {
            Timber.v("Starting instant mix for %s", itemId)
            musicService.startInstantMix(itemId)
            navigationManager.navigateTo(Destination.NowPlaying)
        }
    }
}
