package com.github.damontecres.wholphin.ui.playback

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.github.damontecres.wholphin.data.ItemPlaybackDao
import com.github.damontecres.wholphin.data.ItemPlaybackRepository
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.data.model.JellyfinServer
import com.github.damontecres.wholphin.data.model.JellyfinUser
import com.github.damontecres.wholphin.preferences.AppPreferences
import com.github.damontecres.wholphin.preferences.PlaybackPreferences
import com.github.damontecres.wholphin.preferences.UserPreferences
import com.github.damontecres.wholphin.preferences.updatePlaybackPreferences
import com.github.damontecres.wholphin.services.DatePlayedService
import com.github.damontecres.wholphin.services.DeviceProfileService
import com.github.damontecres.wholphin.services.ImageUrlService
import com.github.damontecres.wholphin.services.MusicService
import com.github.damontecres.wholphin.services.NavigationManager
import com.github.damontecres.wholphin.services.PlayerCreation
import com.github.damontecres.wholphin.services.PlayerFactory
import com.github.damontecres.wholphin.services.PlaylistCreator
import com.github.damontecres.wholphin.services.RefreshRateService
import com.github.damontecres.wholphin.services.ScreensaverService
import com.github.damontecres.wholphin.services.StreamChoiceService
import com.github.damontecres.wholphin.services.UserPreferencesService
import com.github.damontecres.wholphin.test.TestTracks
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.ui.successQueryResult
import com.github.damontecres.wholphin.ui.successResponse
import com.github.damontecres.wholphin.util.LoadingState
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.mediaInfoApi
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import org.jellyfin.sdk.api.client.extensions.videosApi
import org.jellyfin.sdk.api.operations.MediaInfoApi
import org.jellyfin.sdk.api.operations.UserLibraryApi
import org.jellyfin.sdk.api.operations.VideosApi
import org.jellyfin.sdk.model.DeviceInfo
import org.jellyfin.sdk.model.UUID
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.MediaStreamType
import org.jellyfin.sdk.model.api.NameGuidPair
import org.jellyfin.sdk.model.api.PlaybackInfoResponse
import org.jellyfin.sdk.model.api.UserDto
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
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
    private val mockMediaInfoApi = mockk<MediaInfoApi>()
    private val mockVideosApi = mockk<VideosApi>()
    private val mockPlayer = mockk<Player>(relaxed = true)

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
            ioDispatcher = testDispatcher,
            mainDispatcher = testDispatcher,
        )

    fun buildPrefs(block: PlaybackPreferences.Builder.() -> Unit): AppPreferences =
        AppPreferences.getDefaultInstance().updatePlaybackPreferences(block)

    private fun withPreferences(block: PlaybackPreferences.Builder.() -> Unit) {
        val prefs = UserPreferences(buildPrefs(block))
        coEvery { mockUserPreferencesService.getCurrent() } returns prefs
    }

    private val serverId = UUID.randomUUID()
    private val userId = UUID.randomUUID()
    private val server =
        JellyfinServer(serverId, "test server", "http://localhost:8096", "10.11.11")
    private val user =
        JellyfinUser(
            rowId = 1,
            id = userId,
            serverId = serverId,
            name = "test-user",
            accessToken = "token",
            pin = "1234",
        )
    private val userDto =
        UserDto(
            id = userId,
            name = "test-user",
            serverName = "test server",
            hasPassword = true,
            hasConfiguredPassword = true,
            hasConfiguredEasyPassword = false,
        )

    private val mediaSource =
        TestTracks
            .Builder()
            .addVideo()
            .addAudio()
            .addSubtitle()
            .buildForExoPlayer()
            .toMediaSourceInfo()

    private val playSessionId = "playsessionid12345"
    private val videoStreamUrl = "http://localhost:8096/video/stream"

    @OptIn(ExperimentalCoroutinesApi::class)
    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        every { mockApi.userLibraryApi } returns mockUserLibraryApi
        every { mockApi.mediaInfoApi } returns mockMediaInfoApi
        every { mockApi.videosApi } returns mockVideosApi

        coEvery { mockMediaInfoApi.getPostedPlaybackInfo(any(), any()) } returns
            successResponse(
                PlaybackInfoResponse(
                    mediaSources = listOf(mediaSource),
                    playSessionId = playSessionId,
                ),
            )

        every {
            mockVideosApi.getVideoStreamUrl(
                itemId = any(),
                mediaSourceId = mediaSource.id,
                static = true,
                tag = mediaSource.eTag,
                playSessionId = playSessionId,
            )
        } returns videoStreamUrl

        coEvery { mockServerRepository.currentUser } returns user
        coEvery { mockServerRepository.currentUserDto } returns userDto

        coEvery { mockItemPlaybackDao.getItem(user, any()) } returns null
        coEvery { mockStreamChoiceService.chooseSource(any(), any()) } returns mediaSource
        coEvery { mockStreamChoiceService.getPlaybackLanguageChoice(any()) } returns null
        coEvery { mockPlayerFactory.createVideoPlayer(any(), any()) } returns
            PlayerCreation(mockPlayer)
        every { mockPlayerFactory.createMediaSession(any()) } returns mockk(relaxed = true)

        coEvery {
            mockStreamChoiceService.chooseAudioStream(
                source = mediaSource,
                any(),
                any(),
                any(),
                any(),
            )
        } returns mediaSource.mediaStreams!!.first { it.type == MediaStreamType.AUDIO }

        coEvery {
            mockStreamChoiceService.chooseSubtitleStream(
                source = mediaSource,
                any(),
                any(),
                any(),
                any(),
                any(),
            )
        } returns mediaSource.mediaStreams!!.first { it.type == MediaStreamType.SUBTITLE }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /**
     * Create the [PlaybackViewModel] and wait for its init job to complete
     *
     * Throws an exception if the job throws one
     */
    @OptIn(InternalCoroutinesApi::class)
    private suspend fun TestScope.createViewModel(destination: Destination): PlaybackViewModel {
        val viewModel = create(destination)
        testScheduler.advanceUntilIdle()
        viewModel.initJob.join()
        viewModel.initJob
            .getCancellationException()
            .cause
            ?.let { throw it }
        return viewModel
    }

    @OptIn(InternalCoroutinesApi::class)
    @Test
    fun `Play intro first`() =
        runTest(testDispatcher) {
            val movie = movie()
            val intro = movie()
            withPreferences {
                cinemaMode = true
            }
            coEvery { mockUserLibraryApi.getItem(movie.id) } returns successResponse(movie)
            coEvery { mockUserLibraryApi.getIntros(movie.id, any()) } returns
                successQueryResult(listOf(intro))

            val viewModel = createViewModel(Destination.Playback(movie.id, 0L))

            val mediaItem = slot<MediaItem>()
            verify(exactly = 1) { mockPlayer.setMediaItem(capture(mediaItem), any<Long>()) }
            // Should be playing the intro
            Assert.assertEquals(intro.id.toString(), mediaItem.captured.mediaId)
            val state = viewModel.state.value
            Assert.assertEquals(LoadingState.Success, state.loading)
            Assert.assertEquals(mediaSource.id, state.currentMediaInfo.sourceId)
        }

    @OptIn(InternalCoroutinesApi::class)
    @Test
    fun `Play two intros first`() =
        runTest(testDispatcher) {
            val movie = movie()
            val intro = movie()
            val intro2 = movie()
            withPreferences {
                cinemaMode = true
            }
            coEvery { mockUserLibraryApi.getItem(movie.id) } returns successResponse(movie)
            coEvery { mockUserLibraryApi.getIntros(movie.id, any()) } returns
                successQueryResult(listOf(intro, intro2))

            val viewModel = createViewModel(Destination.Playback(movie.id, 0L))

            val mediaItem = slot<MediaItem>()
            verify(exactly = 1) { mockPlayer.setMediaItem(capture(mediaItem), any<Long>()) }
            // Should be playing the first intro
            Assert.assertEquals(intro.id.toString(), mediaItem.captured.mediaId)
            val state = viewModel.state.value
            Assert.assertEquals(LoadingState.Success, state.loading)
            Assert.assertEquals(mediaSource.id, state.currentMediaInfo.sourceId)
            val playlist = state.playlist.items
            Assert.assertEquals(2, playlist.size)
            Assert.assertEquals(intro.id, playlist[0].id)
            Assert.assertEquals(intro2.id, playlist[1].id)
        }

    @OptIn(InternalCoroutinesApi::class)
    @Test
    fun `Don't play intro`() =
        runTest(testDispatcher) {
            val movie = movie()
            withPreferences {
                cinemaMode = false
            }
            coEvery { mockUserLibraryApi.getItem(movie.id) } returns successResponse(movie)

            val viewModel = createViewModel(Destination.Playback(movie.id, 0L))

            coVerify(exactly = 0) { mockUserLibraryApi.getIntros(any(), any()) }
            val mediaItem = slot<MediaItem>()
            verify(exactly = 1) { mockPlayer.setMediaItem(capture(mediaItem), any<Long>()) }
            Assert.assertEquals(movie.id.toString(), mediaItem.captured.mediaId)
            val state = viewModel.state.value
            Assert.assertEquals(LoadingState.Success, state.loading)
            Assert.assertEquals(mediaSource.id, state.currentMediaInfo.sourceId)
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
