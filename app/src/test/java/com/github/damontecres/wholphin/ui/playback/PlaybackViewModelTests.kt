package com.github.damontecres.wholphin.ui.playback

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.github.damontecres.wholphin.data.ItemPlaybackDao
import com.github.damontecres.wholphin.data.ItemPlaybackRepository
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.data.model.JellyfinServer
import com.github.damontecres.wholphin.data.model.JellyfinUser
import com.github.damontecres.wholphin.data.model.Playlist
import com.github.damontecres.wholphin.data.model.PlaylistItem
import com.github.damontecres.wholphin.data.model.SeriesTrackChoiceType
import com.github.damontecres.wholphin.data.model.ServerUserConfig
import com.github.damontecres.wholphin.data.model.TrackIndex
import com.github.damontecres.wholphin.preferences.AppPreferences
import com.github.damontecres.wholphin.preferences.PlaybackPreferences
import com.github.damontecres.wholphin.preferences.PlayerBackend
import com.github.damontecres.wholphin.preferences.ShowNextUpWhen
import com.github.damontecres.wholphin.preferences.SkipSegmentBehavior
import com.github.damontecres.wholphin.preferences.UserPreferences
import com.github.damontecres.wholphin.preferences.updatePlaybackPreferences
import com.github.damontecres.wholphin.services.DatePlayedService
import com.github.damontecres.wholphin.services.DeviceProfileService
import com.github.damontecres.wholphin.services.ImageUrlService
import com.github.damontecres.wholphin.services.MusicService
import com.github.damontecres.wholphin.services.NavigationManager
import com.github.damontecres.wholphin.services.PlayerCreation
import com.github.damontecres.wholphin.services.PlayerFactory
import com.github.damontecres.wholphin.services.PlaylistCreationResult
import com.github.damontecres.wholphin.services.PlaylistCreator
import com.github.damontecres.wholphin.services.RefreshRateService
import com.github.damontecres.wholphin.services.ScreensaverService
import com.github.damontecres.wholphin.services.StreamChoiceReason
import com.github.damontecres.wholphin.services.StreamChoiceResult
import com.github.damontecres.wholphin.services.StreamChoiceService
import com.github.damontecres.wholphin.services.UserPreferencesService
import com.github.damontecres.wholphin.test.TestTracks
import com.github.damontecres.wholphin.test.movie
import com.github.damontecres.wholphin.test.playlist
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.ui.successQueryResult
import com.github.damontecres.wholphin.ui.successResponse
import com.github.damontecres.wholphin.util.LoadingState
import com.github.damontecres.wholphin.util.WholphinDispatchers
import com.github.damontecres.wholphin.util.configure
import com.github.damontecres.wholphin.util.reset
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import io.mockk.verifyOrder
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.mediaInfoApi
import org.jellyfin.sdk.api.client.extensions.mediaSegmentsApi
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import org.jellyfin.sdk.api.client.extensions.videosApi
import org.jellyfin.sdk.api.operations.MediaInfoApi
import org.jellyfin.sdk.api.operations.MediaSegmentsApi
import org.jellyfin.sdk.api.operations.UserLibraryApi
import org.jellyfin.sdk.api.operations.VideosApi
import org.jellyfin.sdk.model.DeviceInfo
import org.jellyfin.sdk.model.UUID
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.MediaSegmentDto
import org.jellyfin.sdk.model.api.MediaSegmentDtoQueryResult
import org.jellyfin.sdk.model.api.MediaSegmentType
import org.jellyfin.sdk.model.api.MediaStreamType
import org.jellyfin.sdk.model.api.PlaybackInfoResponse
import org.jellyfin.sdk.model.extensions.inWholeTicks
import org.jellyfin.sdk.model.extensions.ticks
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [30])
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
    private val mockMediaSegmentsApi = mockk<MediaSegmentsApi>()
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
        )

    private fun setupPreferences(block: PlaybackPreferences.Builder.() -> Unit) {
        val appPrefs = AppPreferences.getDefaultInstance().updatePlaybackPreferences(block)
        val prefs = UserPreferences(appPrefs, null)
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
        ServerUserConfig(
            id = userId,
            name = "test-user",
        )

    private val mediaSource =
        TestTracks
            .Builder()
            .addVideo()
            .addAudio()
            .addSubtitle()
            .buildMediaSourceInfo()

    private val playSessionId = "playsessionid12345"
    private val videoStreamUrl = "http://localhost:8096/video/stream"

    @OptIn(ExperimentalCoroutinesApi::class)
    @Before
    fun setUp() {
        WholphinDispatchers.configure(testDispatcher)

        every { mockApi.userLibraryApi } returns mockUserLibraryApi
        every { mockApi.mediaInfoApi } returns mockMediaInfoApi
        every { mockApi.videosApi } returns mockVideosApi
        every { mockApi.mediaSegmentsApi } returns mockMediaSegmentsApi

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
        coEvery {
            mockStreamChoiceService.getSeriesTrackChoices(
                any<BaseItemDto>(),
                any<SeriesTrackChoiceType>(),
            )
        } returns emptyList()
        coEvery { mockPlayerFactory.createVideoPlayer(any(), any()) } returns
            PlayerCreation(mockPlayer)
        every { mockPlayerFactory.setReduceLoudSounds(any()) } returns Unit
        coEvery { mockPlayerFactory.tryEnableReduceLoudSoundsOnPcm() } returns false
        every { mockPlayerFactory.createMediaSession(any()) } returns mockk(relaxed = true)

        coEvery {
            mockStreamChoiceService.chooseAudioStream(
                source = mediaSource,
                any(),
                any(),
                any(),
                any(),
            )
        } returns
            StreamChoiceResult(
                mediaSource.mediaStreams!!.first { it.type == MediaStreamType.AUDIO },
                StreamChoiceReason.Unknown,
            )

        coEvery {
            mockStreamChoiceService.chooseSubtitleStream(
                item = any(),
                source = mediaSource,
                audioStream = any(),
                itemPlayback = any(),
                stc = any(),
                prefs = any(),
            )
        } returns
            StreamChoiceResult(
                mediaSource.mediaStreams!!.first { it.type == MediaStreamType.SUBTITLE },
                StreamChoiceReason.Unknown,
            )
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @After
    fun tearDown() {
        WholphinDispatchers.reset()
    }

    // Test data
    val playlist = playlist()
    val movie = movie(name = "Test Movie 1")
    val movie2 = movie(name = "Test Movie 2")
    val movie3 = movie(name = "Test Movie 3")
    val intro = movie(name = "Test Intro")
    val playlistItems =
        Playlist.fromMedia(listOf(BaseItem(movie), BaseItem(movie2), BaseItem(movie3)))
    val introSegmentMovie2 =
        MediaSegmentDto(
            id = UUID.randomUUID(),
            itemId = movie2.id,
            type = MediaSegmentType.INTRO,
            startTicks = 5.seconds.inWholeTicks,
            endTicks = 10.seconds.inWholeTicks,
        )
    val segments =
        MediaSegmentDtoQueryResult(
            items = listOf(introSegmentMovie2),
            totalRecordCount = 1,
            startIndex = 0,
        )
    // END test data

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

    @Test
    fun `Play intro first`() =
        runTest(testDispatcher) {
            setupPreferences {
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

    @Test
    fun `Play two intros first`() =
        runTest(testDispatcher) {
            val movie = movie()
            val intro = movie()
            val intro2 = movie()
            setupPreferences {
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

    @Test
    fun `Don't play intro`() =
        runTest(testDispatcher) {
            val movie = movie()
            setupPreferences {
                cinemaMode = false
            }
            coEvery { mockUserLibraryApi.getItem(movie.id) } returns successResponse(movie)

            val viewModel = createViewModel(Destination.Playback(movie.id, 0L))

            coVerify(exactly = 0) { mockUserLibraryApi.getIntros(any(), any()) }
            val mediaItem = slot<MediaItem>()
            verify(exactly = 1) { mockPlayer.setMediaItem(capture(mediaItem), any<Long>()) }
            Assert.assertEquals(movie.id.toString(), mediaItem.captured.mediaId)
            viewModel.state.value.also { state ->
                Assert.assertEquals(LoadingState.Success, state.loading)
                Assert.assertEquals(mediaSource.id, state.currentMediaInfo.sourceId)
            }
        }

    private fun setupForPlaylist() {
        coEvery { mockUserLibraryApi.getItem(playlist.id, any()) } returns
            successResponse(playlist)
        coEvery {
            mockPlaylistCreator.createFrom(
                item = playlist,
                startIndex = any(),
                sortAndDirection = any(),
                shuffled = any(),
                recursive = any(),
                filter = any(),
            )
        } returns PlaylistCreationResult.Success(playlistItems)
    }

    @Test
    fun `Play next up`() =
        runTest(testDispatcher) {
            setupForPlaylist()

            val viewModel = createViewModel(Destination.PlaybackList(playlist.id))

            viewModel.state.value.also { state ->
                Assert.assertEquals(playlistItems.items, state.playlist.items)
                Assert.assertEquals(0, state.playlistIndex)
            }

            viewModel.playNextUp()
            testScheduler.advanceUntilIdle()

            viewModel.state.value.also { state ->
                Assert.assertEquals(1, state.playlistIndex)
            }

            val mediaItem = slot<MediaItem>()
            val mediaItem2 = slot<MediaItem>()
            verifyOrder {
                mockPlayer.setMediaItem(capture(mediaItem), any<Long>())
                mockPlayer.setMediaItem(capture(mediaItem2), any<Long>())
            }
            Assert.assertEquals(movie.id.toString(), mediaItem.captured.mediaId)
            Assert.assertEquals(movie2.id.toString(), mediaItem2.captured.mediaId)
        }

    @Test
    fun `enabling on an Off-created player rebuilds now and preserves position tracks and play intent`() =
        runTest(testDispatcher) {
            for (playing in listOf(false, true)) for (subtitles in listOf(false, true)) {
                io.mockk.clearMocks(mockPlayerFactory, mockPlayer, answers = false)
                setupForPlaylist()
                setupPreferences { }
                coEvery { mockStreamChoiceService.chooseSubtitleStream(any(), any(), any(), any(), any(), any()) } returns
                    mediaSource.mediaStreams!!.first { it.type == MediaStreamType.SUBTITLE }.takeIf { subtitles }
                val oldSession = mockk<androidx.media3.session.MediaSession>(relaxed = true)
                val newSession = mockk<androidx.media3.session.MediaSession>(relaxed = true)
                every { mockPlayerFactory.createMediaSession(any()) } returnsMany listOf(oldSession, newSession)
                val replacement = mockk<Player>(relaxed = true)
                coEvery { mockPlayerFactory.createVideoPlayer(any(), any()) } returnsMany listOf(PlayerCreation(mockPlayer), PlayerCreation(replacement))
                every { mockPlayer.currentPosition } returns 123456L
                every { mockPlayer.playWhenReady } returns playing
                every { mockPlayer.playbackParameters } returns androidx.media3.common.PlaybackParameters(1.25f)
                every { mockPlayer.volume } returns .7f
                val viewModel = createViewModel(Destination.PlaybackList(playlist.id))
                val before = checkNotNull(viewModel.state.value.currentPlayback)
                io.mockk.clearMocks(mockMediaInfoApi, answers = false)
                viewModel.setReduceLoudSounds(true)
                testScheduler.advanceUntilIdle()
                coVerify(exactly = 2) { mockPlayerFactory.createVideoPlayer(any(), any()) }
                verifyOrder { oldSession.release(); mockPlayer.release() }
                verify(exactly = 0) { newSession.release() }
                verify(exactly = 2) { mockPlayerFactory.createMediaSession(any()) }
                verify { mockPlayer.removeListener(viewModel) }
                verify { replacement.addListener(viewModel) }
                verify { replacement.setMediaItem(any(), 123456L) }
                verify { replacement.prepare() }
                verify { replacement.playWhenReady = playing }
                verify { replacement.playbackParameters = androidx.media3.common.PlaybackParameters(1.25f) }
                verify { replacement.volume = .7f }
                val after = checkNotNull(viewModel.state.value.currentPlayback)
                Assert.assertEquals(before.item.id, after.item.id)
                Assert.assertEquals(before.mediaSourceInfo.id, after.mediaSourceInfo.id)
                Assert.assertEquals(before.audioIndex, after.audioIndex)
                Assert.assertEquals(before.subtitleIndex, after.subtitleIndex)
                Assert.assertEquals(if (subtitles) 2 else TrackIndex.DISABLED, after.subtitleIndex)
                coVerify(exactly = 1) {
                    mockMediaInfoApi.getPostedPlaybackInfo(
                        before.item.id,
                        match {
                            it.audioStreamIndex == before.audioIndex &&
                                it.subtitleStreamIndex == before.subtitleIndex &&
                                it.mediaSourceId == before.mediaSourceInfo.id
                        },
                    )
                }
                Assert.assertTrue(viewModel.currentPlayer.value!!.levelingPcmOnly)
                coVerify { mockUserPreferencesService.setReduceLoudSounds(true) }
                viewModel.setReduceLoudSounds(false)
                testScheduler.advanceUntilIdle()
                coVerify(exactly = 2) { mockPlayerFactory.createVideoPlayer(any(), any()) }
                verify { mockPlayerFactory.setReduceLoudSounds(false) }
                viewModel.release()
            }
        }

    @Test
    fun `delayed subtitle settings do not overwrite a different item after replacement`() = runTest(testDispatcher) {
        setupForPlaylist()
        setupPreferences { }
        val viewModel = createViewModel(Destination.PlaybackList(playlist.id))
        viewModel.updateCurrentPlayback { it?.copy(subtitleIndex = 2) }
        val before = checkNotNull(viewModel.state.value.currentPlayback)
        val pending = kotlinx.coroutines.CompletableDeferred<com.github.damontecres.wholphin.data.model.ItemTrackModification?>()
        coEvery { mockItemPlaybackRepository.getTrackModifications(before.item.id, 2) } coAnswers { pending.await() }
        val load = launch { viewModel.loadSubtitleDelay() }
        testScheduler.runCurrent()
        viewModel.updateCurrentPlayback { it?.copy(item = BaseItem(movie2), subtitleDelay = kotlin.time.Duration.ZERO) }
        pending.complete(com.github.damontecres.wholphin.data.model.ItemTrackModification(1, before.item.id, 2, 500))
        load.join()
        Assert.assertEquals(kotlin.time.Duration.ZERO, viewModel.state.value.currentPlayback!!.subtitleDelay)
        viewModel.release()
    }

    @Test
    fun `disconnect cancels old player callbacks and segment monitoring`() = runTest(testDispatcher) {
        setupForPlaylist()
        setupPreferences { showNextUpWhen = ShowNextUpWhen.DURING_CREDITS }
        val viewModel = createViewModel(Destination.PlaybackList(playlist.id))
        val segmentJob = kotlinx.coroutines.CoroutineScope(testDispatcher).launch {
            kotlinx.coroutines.awaitCancellation()
        }
        viewModel.segmentJob = segmentJob
        viewModel.onPlaybackStateChanged(Player.STATE_ENDED)
        viewModel.release()
        testScheduler.runCurrent()
        Assert.assertTrue("released player's segment loop must stop", segmentJob.isCancelled)
        Assert.assertNull("queued old ENDED callback must not open next-up", viewModel.state.value.nextUp)
        verify { mockPlayer.removeListener(viewModel) }
    }

    @Test
    fun `confirmed PCM promotion preserves player source tracks position and pause without reloading`() = runTest(testDispatcher) {
        for (playing in listOf(false, true)) {
            setupForPlaylist()
            setupPreferences { }
            coEvery { mockPlayerFactory.tryEnableReduceLoudSoundsOnPcm() } returns true
            every { mockPlayer.currentPosition } returns 123456L
            every { mockPlayer.playWhenReady } returns playing
            val viewModel = createViewModel(Destination.PlaybackList(playlist.id))
            val before = viewModel.state.value.currentPlayback
            io.mockk.clearMocks(mockPlayer, mockMediaInfoApi, answers = false)
            viewModel.setReduceLoudSounds(true)
            testScheduler.advanceUntilIdle()
            Assert.assertSame(mockPlayer, viewModel.currentPlayer.value!!.player)
            Assert.assertSame(before, viewModel.state.value.currentPlayback)
            Assert.assertTrue(viewModel.currentPlayer.value!!.levelingPcmOnly)
            verify(exactly = 0) { mockPlayer.release() }
            verify(exactly = 0) { mockPlayer.stop() }
            verify(exactly = 0) { mockPlayer.prepare() }
            verify(exactly = 0) { mockPlayer.setMediaItem(any(), any<Long>()) }
            verify(exactly = 0) { mockPlayer.seekTo(any<Long>()) }
            coVerify(exactly = 0) { mockMediaInfoApi.getPostedPlaybackInfo(any(), any()) }
            viewModel.release()
            io.mockk.clearMocks(mockPlayerFactory, answers = false)
        }
    }

    @Test
    fun `enabling on an On-created player uses live processing without rebuilding`() = runTest(testDispatcher) {
        setupForPlaylist()
        setupPreferences { reduceLoudSounds = true }
        val viewModel = createViewModel(Destination.PlaybackList(playlist.id))
        viewModel.setReduceLoudSounds(false)
        viewModel.setReduceLoudSounds(true)
        testScheduler.advanceUntilIdle()
        coVerify(exactly = 1) { mockPlayerFactory.createVideoPlayer(any(), any()) }
        verify { mockPlayerFactory.setReduceLoudSounds(true) }
    }

    @Test
    fun `Off selected while replacement is being built wins after construction`() = runTest(testDispatcher) {
        setupForPlaylist()
        setupPreferences { }
        val replacement = mockk<Player>(relaxed = true)
        val viewModel = createViewModel(Destination.PlaybackList(playlist.id))
        var building = false
        var latestChoice = false
        every { mockPlayerFactory.setReduceLoudSounds(any()) } answers { latestChoice = firstArg() }
        coEvery { mockPlayerFactory.createVideoPlayer(any(), any()) } coAnswers {
            building = true
            kotlinx.coroutines.delay(100)
            // A suspended construction started with On; it must not overwrite a newer Off.
            latestChoice = secondArg<AppPreferences>().playbackPreferences.reduceLoudSounds
            PlayerCreation(replacement)
        }
        viewModel.setReduceLoudSounds(true)
        testScheduler.runCurrent()
        Assert.assertTrue(building)
        viewModel.setReduceLoudSounds(false)
        testScheduler.advanceUntilIdle()
        Assert.assertFalse(latestChoice)
        coVerify(exactly = 2) { mockPlayerFactory.createVideoPlayer(any(), any()) }
        verify { replacement.prepare() }
        viewModel.release()
    }

    @Test
    fun `MPV playback saves the boolean without rebuilding its native player`() = runTest(testDispatcher) {
        setupForPlaylist()
        setupPreferences { playerBackend = PlayerBackend.MPV }
        val viewModel = createViewModel(Destination.PlaybackList(playlist.id))
        viewModel.setReduceLoudSounds(true)
        testScheduler.advanceUntilIdle()
        coVerify(exactly = 1) { mockPlayerFactory.createVideoPlayer(PlayerBackend.MPV, any()) }
        coVerify { mockUserPreferencesService.setReduceLoudSounds(true) }
        Assert.assertFalse(viewModel.currentPlayer.value!!.levelingPcmOnly)
        viewModel.release()
    }

    @Test
    fun `Rapid leveling choices save in selection order even when the first write is slow`() =
        runTest(testDispatcher) {
            setupForPlaylist()
            setupPreferences { }
            every { mockPlayerFactory.setReduceLoudSounds(any()) } returns Unit
            val saved = mutableListOf<Boolean>()
            coEvery { mockUserPreferencesService.setReduceLoudSounds(any()) } coAnswers {
                val mode = firstArg<Boolean>()
                if (mode == true) kotlinx.coroutines.delay(100)
                saved.add(mode)
            }
            val viewModel = createViewModel(Destination.PlaybackList(playlist.id))
            viewModel.setReduceLoudSounds(true)
            viewModel.setReduceLoudSounds(false)
            testScheduler.advanceUntilIdle()
            Assert.assertEquals(
                listOf(
                    true,
                    false,
                ),
                saved,
            )
            viewModel.playNextUp()
            testScheduler.advanceUntilIdle()
            coVerify(exactly = 1) { mockPlayerFactory.createVideoPlayer(any(), any()) }
        }

    @Test
    fun `Play previous`() =
        runTest(testDispatcher) {
            setupForPlaylist()

            val viewModel = createViewModel(Destination.PlaybackList(playlist.id))

            viewModel.state.value.also { state ->
                Assert.assertEquals(playlistItems.items, state.playlist.items)
                Assert.assertEquals(0, state.playlistIndex)
            }

            viewModel.playNextUp()
            testScheduler.advanceUntilIdle()
            viewModel.playNextUp()
            testScheduler.advanceUntilIdle()

            viewModel.state.value.also { state ->
                Assert.assertEquals(2, state.playlistIndex)
            }

            viewModel.playPrevious()
            testScheduler.advanceUntilIdle()

            viewModel.state.value.also { state ->
                Assert.assertEquals(1, state.playlistIndex)
            }

            val mediaItems = mutableListOf<MediaItem>()
            verify(exactly = 4) {
                mockPlayer.setMediaItem(withArg { mediaItems.add(it) }, any<Long>())
            }
            Assert.assertEquals(4, mediaItems.size)
            Assert.assertEquals(movie.id.toString(), mediaItems[0].mediaId)
            Assert.assertEquals(movie2.id.toString(), mediaItems[1].mediaId)
            Assert.assertEquals(movie3.id.toString(), mediaItems[2].mediaId)
            Assert.assertEquals(movie2.id.toString(), mediaItems[3].mediaId)
        }

    @OptIn(InternalCoroutinesApi::class)
    @Test
    fun `Show next up at end of playback`() =
        runTest(testDispatcher) {
            setupForPlaylist()
            setupPreferences {
                showNextUpWhen = ShowNextUpWhen.END_OF_PLAYBACK
            }

            val viewModel = createViewModel(Destination.PlaybackList(playlist.id))

            viewModel.state.value.also { state ->
                Assert.assertEquals(playlistItems.items, state.playlist.items)
                Assert.assertEquals(0, state.playlistIndex)
                Assert.assertNull(state.nextUp)
            }

            viewModel.onPlaybackStateChanged(Player.STATE_ENDED)
            testScheduler.advanceUntilIdle()

            viewModel.state.value.also { state ->
                Assert.assertEquals(movie2, state.nextUp?.data)
            }
        }

    @Test
    fun `Show next up never with automatic play`() =
        runTest(testDispatcher) {
            setupForPlaylist()
            setupPreferences {
                showNextUpWhen = ShowNextUpWhen.NEXT_UP_NEVER
                autoPlayNext = true
            }

            val viewModel = createViewModel(Destination.PlaybackList(playlist.id))

            viewModel.state.value.also { state ->
                Assert.assertEquals(playlistItems.items, state.playlist.items)
                Assert.assertEquals(0, state.playlistIndex)
                Assert.assertNull(state.nextUp)
            }

            viewModel.onPlaybackStateChanged(Player.STATE_ENDED)
            testScheduler.advanceUntilIdle()

            viewModel.state.value.also { state ->
                Assert.assertNull(state.nextUp)
                Assert.assertTrue(state.hasNext)
            }
            verify(exactly = 0) { mockNavigationManager.goBack() }
        }

    @Test
    fun `Show next up never without automatic play`() =
        runTest(testDispatcher) {
            setupForPlaylist()
            setupPreferences {
                showNextUpWhen = ShowNextUpWhen.NEXT_UP_NEVER
                autoPlayNext = false
            }

            val viewModel = createViewModel(Destination.PlaybackList(playlist.id))

            viewModel.state.value.also { state ->
                Assert.assertEquals(playlistItems.items, state.playlist.items)
                Assert.assertEquals(0, state.playlistIndex)
                Assert.assertNull(state.nextUp)
            }

            viewModel.onPlaybackStateChanged(Player.STATE_ENDED)
            testScheduler.advanceUntilIdle()

            viewModel.state.value.also { state ->
                Assert.assertNull(state.nextUp)
                Assert.assertTrue(state.hasNext)
            }
            verify(exactly = 1) { mockNavigationManager.goBack() }
        }

    @Test
    fun `Playback ends with no next up`() =
        runTest(testDispatcher) {
            setupForPlaylist()
            setupPreferences {
                showNextUpWhen = ShowNextUpWhen.END_OF_PLAYBACK
                autoPlayNext = false
            }

            val viewModel = createViewModel(Destination.PlaybackList(playlist.id))

            viewModel.state.value.also { state ->
                Assert.assertEquals(playlistItems.items, state.playlist.items)
                Assert.assertEquals(0, state.playlistIndex)
                Assert.assertNull(state.nextUp)
            }
            viewModel.playNextUp()
            viewModel.playNextUp()
            testScheduler.advanceUntilIdle()

            viewModel.onPlaybackStateChanged(Player.STATE_ENDED)
            testScheduler.advanceUntilIdle()

            viewModel.state.value.also { state ->
                Assert.assertNull(state.nextUp)
                Assert.assertFalse(state.hasNext)
            }
            verify(exactly = 1) { mockNavigationManager.goBack() }
        }

    @Test
    fun `Intro playback ends always play next`() =
        runTest(testDispatcher) {
            setupPreferences {
                cinemaMode = true
                showNextUpWhen = ShowNextUpWhen.NEXT_UP_NEVER
                autoPlayNext = false
            }
            setupForPlaylist()
            coEvery { mockUserLibraryApi.getItem(movie.id) } returns successResponse(movie)
            coEvery { mockUserLibraryApi.getIntros(movie.id, any()) } returns
                successQueryResult(listOf(intro))

            val viewModel = createViewModel(Destination.PlaybackList(playlist.id))

            viewModel.state.value.also { state ->
                Assert.assertEquals(
                    listOf(PlaylistItem.Intro(BaseItem(intro))) + playlistItems.items,
                    state.playlist.items,
                )
                Assert.assertEquals(0, state.playlistIndex)
                Assert.assertNull(state.nextUp)
            }

            viewModel.onPlaybackStateChanged(Player.STATE_ENDED)
            testScheduler.advanceUntilIdle()

            viewModel.state.value.also { state ->
                Assert.assertEquals(1, state.playlistIndex)
                Assert.assertNull(state.nextUp)
            }
            verify(exactly = 0) { mockNavigationManager.goBack() }

            val mediaItems = mutableListOf<MediaItem>()
            verify(exactly = 2) {
                mockPlayer.setMediaItem(withArg { mediaItems.add(it) }, any<Long>())
            }
            Assert.assertEquals(2, mediaItems.size)
            Assert.assertEquals(intro.id.toString(), mediaItems[0].mediaId)
            Assert.assertEquals(movie.id.toString(), mediaItems[1].mediaId)
        }

    @Test
    fun playItemInPlaylist() =
        runTest(testDispatcher) {
            setupForPlaylist()
            setupPreferences {
            }

            val viewModel = createViewModel(Destination.PlaybackList(playlist.id))

            viewModel.state.value.also { state ->
                Assert.assertEquals(playlistItems.items, state.playlist.items)
                Assert.assertEquals(0, state.playlistIndex)
                Assert.assertNull(state.nextUp)
            }

            viewModel.playItemInPlaylist(BaseItem(movie3))
            testScheduler.advanceUntilIdle()

            viewModel.state.value.also { state ->
                Assert.assertEquals(2, state.playlistIndex)
                Assert.assertNull(state.nextUp)
            }
            val mediaItem = slot<MediaItem>()
            val mediaItem2 = slot<MediaItem>()
            verifyOrder {
                mockPlayer.setMediaItem(capture(mediaItem), any<Long>())
                mockPlayer.setMediaItem(capture(mediaItem2), any<Long>())
            }
            Assert.assertEquals(movie.id.toString(), mediaItem.captured.mediaId)
            Assert.assertEquals(movie3.id.toString(), mediaItem2.captured.mediaId)
        }

    @Test
    fun `Media segments auto skip`() =
        runTest(testDispatcher, timeout = 8.seconds) {
            setupForPlaylist()
            setupPreferences {
                skipIntros = SkipSegmentBehavior.AUTO_SKIP
            }

            coEvery { mockMediaSegmentsApi.getItemSegments(movie.id) } returns
                successResponse(MediaSegmentDtoQueryResult(emptyList(), 0, 0))
            coEvery { mockMediaSegmentsApi.getItemSegments(movie2.id) } returns
                successResponse(segments)

            val viewModel = createViewModel(Destination.PlaybackList(playlist.id))

            viewModel.state.value.also { state ->
                Assert.assertNull(state.currentSegment)
            }

            every { mockPlayer.currentPosition } returns 5.5.seconds.inWholeMilliseconds

            try {
                // TODO better testability
                // This will start an infinite loop
                viewModel.playNextUp()
                testScheduler.advanceTimeBy(1500.milliseconds)

                viewModel.state.value.also { state ->
                    Assert.assertEquals(introSegmentMovie2, state.currentSegment?.segment)
                    Assert.assertTrue(state.currentSegment?.interacted == true)
                }
                verify(exactly = 1) {
                    // Verify only skipping a single time
                    mockPlayer.seekTo(introSegmentMovie2.endTicks.ticks.inWholeMilliseconds + 1)
                }
            } finally {
                viewModel.segmentJob?.cancel()
            }
        }

    @Test
    fun `Media segment ask to skip`() =
        runTest(testDispatcher, timeout = 8.seconds) {
            setupForPlaylist()
            setupPreferences {
                skipIntros = SkipSegmentBehavior.ASK_TO_SKIP
            }

            coEvery { mockMediaSegmentsApi.getItemSegments(movie.id) } returns
                successResponse(MediaSegmentDtoQueryResult(emptyList(), 0, 0))
            coEvery { mockMediaSegmentsApi.getItemSegments(movie2.id) } returns
                successResponse(segments)

            val viewModel = createViewModel(Destination.PlaybackList(playlist.id))

            viewModel.state.value.also { state ->
                Assert.assertNull(state.currentSegment)
            }

            every { mockPlayer.currentPosition } returns 5.5.seconds.inWholeMilliseconds

            try {
                // TODO better testability
                // This will start an infinite loop
                viewModel.playNextUp()
                testScheduler.advanceTimeBy(1000.milliseconds)

                viewModel.state.value.also { state ->
                    Assert.assertEquals(introSegmentMovie2, state.currentSegment?.segment)
                    Assert.assertTrue(state.currentSegment?.interacted == false)
                }
                verify(exactly = 0) { mockPlayer.seekTo(any()) }
            } finally {
                viewModel.segmentJob?.cancel()
            }
        }

    @Test
    fun `Media segment ask to skip and clicked`() =
        runTest(testDispatcher, timeout = 8.seconds) {
            setupForPlaylist()
            setupPreferences {
                skipIntros = SkipSegmentBehavior.ASK_TO_SKIP
            }

            coEvery { mockMediaSegmentsApi.getItemSegments(movie.id) } returns
                successResponse(MediaSegmentDtoQueryResult(emptyList(), 0, 0))
            coEvery { mockMediaSegmentsApi.getItemSegments(movie2.id) } returns
                successResponse(segments)

            val viewModel = createViewModel(Destination.PlaybackList(playlist.id))

            viewModel.state.value.also { state ->
                Assert.assertNull(state.currentSegment)
            }

            every { mockPlayer.currentPosition } returns 5.5.seconds.inWholeMilliseconds

            try {
                // TODO better testability
                // This will start an infinite loop
                viewModel.playNextUp()
                testScheduler.advanceTimeBy(1000.milliseconds)

                viewModel.state.value.also { state ->
                    Assert.assertEquals(introSegmentMovie2, state.currentSegment?.segment)
                    Assert.assertTrue(state.currentSegment?.interacted == false)
                }
                verify(exactly = 0) { mockPlayer.seekTo(any()) }

                viewModel.updateSegment(introSegmentMovie2.id, false)
                every { mockPlayer.currentPosition } returns introSegmentMovie2.endTicks.ticks.inWholeMilliseconds + 1
                testScheduler.advanceTimeBy(10.milliseconds)
                viewModel.state.value.also { state ->
                    Assert.assertNull(state.currentSegment)
                }
                verify(exactly = 1) {
                    // Verify only skipping a single time
                    mockPlayer.seekTo(introSegmentMovie2.endTicks.ticks.inWholeMilliseconds + 1)
                }
            } finally {
                viewModel.segmentJob?.cancel()
            }
        }

    @Test
    fun `Media segment ask to skip and dismissed`() =
        runTest(testDispatcher, timeout = 8.seconds) {
            setupForPlaylist()
            setupPreferences {
                skipIntros = SkipSegmentBehavior.ASK_TO_SKIP
            }

            coEvery { mockMediaSegmentsApi.getItemSegments(movie.id) } returns
                successResponse(MediaSegmentDtoQueryResult(emptyList(), 0, 0))
            coEvery { mockMediaSegmentsApi.getItemSegments(movie2.id) } returns
                successResponse(segments)

            val viewModel = createViewModel(Destination.PlaybackList(playlist.id))

            viewModel.state.value.also { state ->
                Assert.assertNull(state.currentSegment)
            }

            every { mockPlayer.currentPosition } returns 5.5.seconds.inWholeMilliseconds

            try {
                // TODO better testability
                // This will start an infinite loop
                viewModel.playNextUp()
                testScheduler.advanceTimeBy(1000.milliseconds)

                viewModel.state.value.also { state ->
                    Assert.assertEquals(introSegmentMovie2, state.currentSegment?.segment)
                    Assert.assertTrue(state.currentSegment?.interacted == false)
                }

                viewModel.updateSegment(introSegmentMovie2.id, true)
                testScheduler.advanceTimeBy(10.milliseconds)
                viewModel.state.value.also { state ->
                    Assert.assertEquals(introSegmentMovie2, state.currentSegment?.segment)
                    Assert.assertTrue(state.currentSegment?.interacted == true)
                }

                // Never skipped
                verify(exactly = 0) { mockPlayer.seekTo(any()) }
            } finally {
                viewModel.segmentJob?.cancel()
            }
        }

    @Test
    fun `Media segment ignore`() =
        runTest(testDispatcher, timeout = 8.seconds) {
            setupForPlaylist()
            setupPreferences {
                skipIntros = SkipSegmentBehavior.IGNORE
            }

            coEvery { mockMediaSegmentsApi.getItemSegments(movie.id) } returns
                successResponse(MediaSegmentDtoQueryResult(emptyList(), 0, 0))
            coEvery { mockMediaSegmentsApi.getItemSegments(movie2.id) } returns
                successResponse(segments)

            val viewModel = createViewModel(Destination.PlaybackList(playlist.id))

            viewModel.state.value.also { state ->
                Assert.assertNull(state.currentSegment)
            }

            every { mockPlayer.currentPosition } returns 5.5.seconds.inWholeMilliseconds

            try {
                // TODO better testability
                // This will start an infinite loop
                viewModel.playNextUp()
                testScheduler.advanceTimeBy(1000.milliseconds)

                viewModel.state.value.also { state ->
                    Assert.assertNull(state.currentSegment)
                }
                verify(exactly = 0) { mockPlayer.seekTo(any()) }
            } finally {
                viewModel.segmentJob?.cancel()
            }
        }

    @Test
    fun `Media segment not at current position`() =
        runTest(testDispatcher, timeout = 8.seconds) {
            setupForPlaylist()
            setupPreferences {
                skipIntros = SkipSegmentBehavior.AUTO_SKIP
            }

            coEvery { mockMediaSegmentsApi.getItemSegments(movie.id) } returns
                successResponse(MediaSegmentDtoQueryResult(emptyList(), 0, 0))
            coEvery { mockMediaSegmentsApi.getItemSegments(movie2.id) } returns
                successResponse(segments)

            val viewModel = createViewModel(Destination.PlaybackList(playlist.id))

            viewModel.state.value.also { state ->
                Assert.assertNull(state.currentSegment)
            }

            every { mockPlayer.currentPosition } returns 2.5.seconds.inWholeMilliseconds

            try {
                // TODO better testability
                // This will start an infinite loop
                viewModel.playNextUp()
                testScheduler.advanceTimeBy(1000.milliseconds)

                viewModel.state.value.also { state ->
                    Assert.assertNull(state.currentSegment)
                }
                verify(exactly = 0) { mockPlayer.seekTo(any()) }
            } finally {
                viewModel.segmentJob?.cancel()
            }
        }
}
