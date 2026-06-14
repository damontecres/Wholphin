package com.github.damontecres.wholphin.ui.playback

import android.content.Context
import com.github.damontecres.wholphin.data.ItemPlaybackDao
import com.github.damontecres.wholphin.data.ItemPlaybackRepository
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.preferences.AppPreferences
import com.github.damontecres.wholphin.preferences.PlaybackPreferences
import com.github.damontecres.wholphin.preferences.UserPreferences
import com.github.damontecres.wholphin.preferences.updatePlaybackPreferences
import com.github.damontecres.wholphin.services.DatePlayedService
import com.github.damontecres.wholphin.services.DeviceProfileService
import com.github.damontecres.wholphin.services.ImageUrlService
import com.github.damontecres.wholphin.services.MusicService
import com.github.damontecres.wholphin.services.NavigationManager
import com.github.damontecres.wholphin.services.PlayerFactory
import com.github.damontecres.wholphin.services.PlaylistCreator
import com.github.damontecres.wholphin.services.RefreshRateService
import com.github.damontecres.wholphin.services.ScreensaverService
import com.github.damontecres.wholphin.services.StreamChoiceService
import com.github.damontecres.wholphin.services.UserPreferencesService
import com.github.damontecres.wholphin.ui.nav.Destination
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.Response
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import org.jellyfin.sdk.api.operations.UserLibraryApi
import org.jellyfin.sdk.model.DeviceInfo
import org.jellyfin.sdk.model.UUID
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemDtoQueryResult
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.NameGuidPair
import org.junit.After
import org.junit.Before
import org.junit.Test

class PlaybackViewModelTests {
    private val testDispatcher = StandardTestDispatcher()

    private val mockContext = mockk<Context>(relaxed = true)
    private val mockApi = mockk<ApiClient>(relaxed = true)
    private val mockNavigationManager = mockk<NavigationManager>(relaxed = true)
    private val mockPlaylistCreator = mockk<PlaylistCreator>(relaxed = true)
    private val mockItemPlaybackDao = mockk<ItemPlaybackDao>(relaxed = true)
    private val mockServerRepository = mockk<ServerRepository>(relaxed = true)
    private val mockItemPlaybackRepository = mockk<ItemPlaybackRepository>(relaxed = true)
    private val mockPlayerFactory = mockk<PlayerFactory>()
    private val mockDatePlayedService = mockk<DatePlayedService>(relaxed = true)
    private val mockDeviceInfo = mockk<DeviceInfo>(relaxed = true)
    private val mockDeviceProfileService = mockk<DeviceProfileService>(relaxed = true)
    private val mockRefreshRateService = mockk<RefreshRateService>(relaxed = true)
    private val mockStreamChoiceService = mockk<StreamChoiceService>(relaxed = true)
    private val mockUserPreferencesService = mockk<UserPreferencesService>(relaxed = true)
    private val mockImageUrlService = mockk<ImageUrlService>(relaxed = true)
    private val mockScreensaverService = mockk<ScreensaverService>(relaxed = true)
    private val mockMusicService = mockk<MusicService>(relaxed = true)

    private val mockUserLibraryApi = mockk<UserLibraryApi>()

    fun create(destination: Destination): PlaybackViewModel =
        PlaybackViewModel(
            context = mockContext,
            api = mockApi,
            navigationManager = mockNavigationManager,
            playlistCreator = mockPlaylistCreator,
            itemPlaybackDao = mockItemPlaybackDao,
            serverRepository = mockServerRepository,
            itemPlaybackRepository = mockItemPlaybackRepository,
            playerFactory = mockPlayerFactory,
            datePlayedService = mockDatePlayedService,
            deviceInfo = mockDeviceInfo,
            deviceProfileService = mockDeviceProfileService,
            refreshRateService = mockRefreshRateService,
            streamChoiceService = mockStreamChoiceService,
            userPreferencesService = mockUserPreferencesService,
            imageUrlService = mockImageUrlService,
            screensaverService = mockScreensaverService,
            musicService = mockMusicService,
            destination = destination,
        )

    fun buildPrefs(block: PlaybackPreferences.Builder.() -> Unit): AppPreferences =
        AppPreferences.getDefaultInstance().updatePlaybackPreferences(block)

    private fun withPreferences(block: PlaybackPreferences.Builder.() -> Unit) {
        val prefs = UserPreferences(buildPrefs(block))
        coEvery { mockUserPreferencesService.getCurrent() } returns prefs
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { mockApi.userLibraryApi } returns mockUserLibraryApi
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `plays intro first`() {
        val movie = movie()
        withPreferences {
            cinemaMode = true
        }
        coEvery { mockUserLibraryApi.getItem(movie.id) } returns Response(movie, 200, emptyMap())
        coEvery { mockUserLibraryApi.getIntros(movie.id) } returns
            Response(
                BaseItemDtoQueryResult(
                    listOf(movie()),
                    1,
                    0,
                ),
                200,
                emptyMap(),
            )

        create(Destination.Playback(movie.id, 0L))
    }
}

private fun movie(
    id: UUID = UUID.randomUUID(),
    name: String = "Test Movie",
    genres: List<NameGuidPair>? = null,
): BaseItemDto =
    BaseItemDto(
        id = id,
        type = BaseItemKind.MOVIE,
        name = name,
        seriesId = null,
        genreItems = genres,
    )
