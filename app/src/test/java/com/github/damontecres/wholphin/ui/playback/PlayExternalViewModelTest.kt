package com.github.damontecres.wholphin.ui.playback

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import com.github.damontecres.wholphin.data.ItemPlaybackDao
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.data.model.Playlist
import com.github.damontecres.wholphin.preferences.AppPreferences
import com.github.damontecres.wholphin.preferences.UserPreferences
import com.github.damontecres.wholphin.preferences.updatePlaybackPreferences
import com.github.damontecres.wholphin.services.NavigationManager
import com.github.damontecres.wholphin.services.PlaylistCreationResult
import com.github.damontecres.wholphin.services.PlaylistCreator
import com.github.damontecres.wholphin.services.StreamChoiceService
import com.github.damontecres.wholphin.services.UserPreferencesService
import com.github.damontecres.wholphin.test.TestTracks
import com.github.damontecres.wholphin.test.movie
import com.github.damontecres.wholphin.test.playlist
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.ui.preferences.ExternalPlayerApp
import com.github.damontecres.wholphin.ui.preferences.getExternalPlayers
import com.github.damontecres.wholphin.ui.successResponse
import com.github.damontecres.wholphin.util.LoadingState
import com.github.damontecres.wholphin.util.WholphinDispatchers
import com.github.damontecres.wholphin.util.configure
import com.github.damontecres.wholphin.util.reset
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.playStateApi
import org.jellyfin.sdk.api.client.extensions.subtitleApi
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import org.jellyfin.sdk.api.client.extensions.videosApi
import org.jellyfin.sdk.api.operations.PlayStateApi
import org.jellyfin.sdk.api.operations.SubtitleApi
import org.jellyfin.sdk.api.operations.UserLibraryApi
import org.jellyfin.sdk.api.operations.VideosApi
import org.jellyfin.sdk.model.extensions.inWholeTicks
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.time.Duration.Companion.minutes

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [30])
class PlayExternalViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private val context = mockk<Context>()
    private val api = mockk<ApiClient>()
    private val userLibraryApi = mockk<UserLibraryApi>()
    private val videosApi = mockk<VideosApi>()
    private val subtitleApi = mockk<SubtitleApi>()
    private val playStateApi = mockk<PlayStateApi>(relaxed = true)
    private val serverRepository = mockk<ServerRepository>()
    private val itemPlaybackDao = mockk<ItemPlaybackDao>()
    private val playlistCreator = mockk<PlaylistCreator>()
    private val streamChoiceService = mockk<StreamChoiceService>()
    private val navigationManager = mockk<NavigationManager>(relaxed = true)
    private val userPreferencesService = mockk<UserPreferencesService>()

    private val movie = movie()
    private val subtitleUrl = "http://localhost:8096/subtitles/0.srt"
    private val videoUrl = "http://localhost:8096/video/stream"
    private val mediaSource =
        TestTracks
            .Builder()
            .addExternalSubtitle()
            .buildMediaSourceInfo()
            .let { source ->
                source.copy(
                    id = "source-id",
                    runTimeTicks = 90.minutes.inWholeTicks,
                    mediaStreams =
                        source.mediaStreams!!.map {
                            it.copy(codec = "srt", path = "subtitle.srt", displayTitle = "English")
                        },
                )
            }
    private val subtitle = mediaSource.mediaStreams!!.single()
    private val vlc = ComponentName("org.videolan.vlc", "org.videolan.vlc.gui.video.VideoPlayerActivity")

    @OptIn(ExperimentalCoroutinesApi::class)
    @Before
    fun setUp() {
        WholphinDispatchers.configure(testDispatcher)
        mockkStatic(::getExternalPlayers)
        every { getExternalPlayers(context) } returns
            listOf(ExternalPlayerApp("VLC", null, vlc.flattenToString()))
        every { api.userLibraryApi } returns userLibraryApi
        every { api.videosApi } returns videosApi
        every { api.subtitleApi } returns subtitleApi
        every { api.playStateApi } returns playStateApi
        every { serverRepository.currentUser } returns null
        coEvery { userLibraryApi.getItem(movie.id) } returns successResponse(movie)
        coEvery { streamChoiceService.chooseSource(any(), any()) } returns mediaSource
        coEvery { streamChoiceService.getPlaybackLanguageChoice(any()) } returns null
        coEvery { streamChoiceService.chooseSubtitleStream(any(), any(), any(), any(), any(), any()) } returns subtitle
        every {
            videosApi.getVideoStreamUrl(
                itemId = movie.id,
                mediaSourceId = mediaSource.id,
                static = true,
            )
        } returns videoUrl
        every {
            subtitleApi.getSubtitleUrl(
                routeItemId = any(),
                routeMediaSourceId = mediaSource.id!!,
                routeIndex = subtitle.index,
                routeFormat = "srt",
            )
        } returns subtitleUrl
        setupPlayer("")
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @After
    fun tearDown() {
        unmockkStatic(::getExternalPlayers)
        WholphinDispatchers.reset()
    }

    private fun setupPlayer(playerId: String) {
        val preferences =
            AppPreferences.getDefaultInstance().updatePlaybackPreferences {
                externalPlayer = playerId
            }
        coEvery { userPreferencesService.getCurrent() } returns UserPreferences(preferences, null)
    }

    private fun createIntent(destination: Destination): Intent {
        val viewModel =
            PlayExternalViewModel(
                SavedStateHandle(),
                context,
                api,
                serverRepository,
                itemPlaybackDao,
                playlistCreator,
                streamChoiceService,
                navigationManager,
                userPreferencesService,
            )
        viewModel.init(destination)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(LoadingState.Success, viewModel.state.value.loading)
        return viewModel.state.value.intent
    }

    private fun assertStartFromBeginning(intent: Intent) {
        assertEquals(true, intent.extras!!.get("from_start"))
        assertEquals(0, intent.extras!!.get("position"))
    }

    @Test
    fun `Explicit VLC starts from beginning at zero position`() =
        runTest(testDispatcher) {
            setupPlayer(vlc.flattenToString())
            val intent = createIntent(Destination.Playback(movie.id, 0L))
            assertEquals(vlc, intent.component)
            assertStartFromBeginning(intent)
        }

    @Test
    fun `Implicit player starts VLC from beginning at zero position`() =
        runTest(testDispatcher) {
            val intent = createIntent(Destination.Playback(movie.id, 0L))
            assertNull(intent.component)
            assertStartFromBeginning(intent)
        }

    @Test
    fun `Unavailable selected player falls back to implicit start from beginning`() =
        runTest(testDispatcher) {
            setupPlayer("missing.player/missing.player.Activity")
            val intent = createIntent(Destination.Playback(movie.id, 0L))
            assertNull(intent.component)
            assertStartFromBeginning(intent)
        }

    @Test
    fun `Explicit VLC preserves positive resume position`() =
        runTest(testDispatcher) {
            setupPlayer(vlc.flattenToString())
            val intent = createIntent(Destination.Playback(movie.id, 42_000L))
            assertEquals(vlc, intent.component)
            assertEquals(false, intent.extras!!.get("from_start"))
            assertEquals(42_000, intent.extras!!.get("position"))
        }

    @Test
    fun `Implicit player preserves positive resume position`() =
        runTest(testDispatcher) {
            val intent = createIntent(Destination.Playback(movie.id, 42_000L))
            assertNull(intent.component)
            assertEquals(false, intent.extras!!.get("from_start"))
            assertEquals(42_000, intent.extras!!.get("position"))
        }

    @Test
    fun `Playback list starts from beginning`() =
        runTest(testDispatcher) {
            val playlist = playlist()
            coEvery { userLibraryApi.getItem(playlist.id) } returns successResponse(playlist)
            coEvery { playlistCreator.createFrom(any(), any(), any(), any(), any(), any()) } returns
                PlaylistCreationResult.Success(Playlist.fromMedia(listOf(BaseItem(movie))))

            val intent = createIntent(Destination.PlaybackList(playlist.id))
            assertStartFromBeginning(intent)
        }

    @Test
    fun `VLC starts from beginning without subtitles or duration`() =
        runTest(testDispatcher) {
            coEvery { streamChoiceService.chooseSource(any(), any()) } returns
                mediaSource.copy(mediaStreams = emptyList(), runTimeTicks = null)
            coEvery { streamChoiceService.chooseSubtitleStream(any(), any(), any(), any(), any(), any()) } returns null

            val intent = createIntent(Destination.Playback(movie.id, 0L))
            assertStartFromBeginning(intent)
            assertFalse(intent.hasExtra("subtitles_location"))
            assertFalse(intent.hasExtra("extra_duration"))
            assertFalse(intent.hasExtra("subs.enable"))
            assertFalse(intent.hasExtra("forcedsrt"))
        }

    @Test
    fun `Existing player extras and playback-start reporting remain unchanged`() =
        runTest(testDispatcher) {
            val intent = createIntent(Destination.Playback(movie.id, 42_000L))
            val title = "${BaseItem(movie).title} ${BaseItem(movie).subtitleLong}"
            val subtitleUri = Uri.parse(subtitleUrl)
            assertEquals(Intent.ACTION_VIEW, intent.action)
            assertEquals(Uri.parse(videoUrl), intent.data)
            assertEquals("video/*", intent.type)
            assertEquals(title, intent.getStringExtra("title"))
            assertEquals(42_000, intent.extras!!.get("position"))
            assertTrue(intent.getBooleanExtra("return_result", false))
            assertTrue(intent.getBooleanExtra("secure_uri", false))
            assertArrayEquals(arrayOf(subtitleUri), intent.getParcelableArrayExtra("subs"))
            assertArrayEquals(arrayOf("English"), intent.getStringArrayExtra("subs.name"))
            assertArrayEquals(arrayOf(subtitleUri), intent.getParcelableArrayExtra("subs.enable"))
            assertEquals(subtitleUrl, intent.getStringExtra("subtitles_location"))
            assertEquals(90.minutes.inWholeMilliseconds, intent.extras!!.get("extra_duration"))
            assertEquals(42_000, intent.extras!!.get("startfrom"))
            assertFalse(intent.getBooleanExtra("forceresume", true))
            assertEquals(title, intent.getStringExtra("forcename"))
            assertEquals(subtitleUri, intent.getParcelableExtra<Uri>("forcedsrt"))
            coVerify(exactly = 1) { playStateApi.reportPlaybackStart(any()) }
            coVerify(exactly = 0) { playStateApi.reportPlaybackStopped(any()) }
        }
}
