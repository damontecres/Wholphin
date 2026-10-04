package com.github.damontecres.wholphin.services

import android.app.SearchManager
import android.content.Intent
import com.github.damontecres.wholphin.data.CurrentUser
import com.github.damontecres.wholphin.data.JellyfinServerDao
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.preferences.AppPreferences
import com.github.damontecres.wholphin.preferences.UserPreferences
import com.github.damontecres.wholphin.preferences.update
import com.github.damontecres.wholphin.test.assertIs
import com.github.damontecres.wholphin.test.currentUser
import com.github.damontecres.wholphin.test.movie
import com.github.damontecres.wholphin.test.server
import com.github.damontecres.wholphin.test.user
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.ui.successResponse
import com.github.damontecres.wholphin.ui.toServerString
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import org.jellyfin.sdk.api.operations.UserLibraryApi
import org.jellyfin.sdk.model.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [30])
class IntentServiceTest {
    private val api: ApiClient = mockk()
    private val serverRepository: ServerRepository = mockk()
    private val serverDao: JellyfinServerDao = mockk()
    private val userPreferencesService: UserPreferencesService = mockk()
    private val userLibraryApi: UserLibraryApi = mockk()

    lateinit var intentService: IntentService

    private val serverId = UUID.randomUUID()
    private val userId = UUID.randomUUID()
    private val currentUser = currentUser(serverId, userId)
    private val itemId = UUID.randomUUID()

    private val protectedCurrentUser =
        CurrentUser(
            server(serverId),
            user(serverId, userId).copy(pin = "1234"),
        )

    @Before
    fun setup() {
        intentService = IntentService(api, serverRepository, userPreferencesService)
        every { api.userLibraryApi } returns userLibraryApi
        coEvery { userLibraryApi.getItem(itemId) } returns successResponse(movie(itemId))
        every { serverRepository.serverDao } returns serverDao
    }

    private fun setupPreferences(block: AppPreferences.Builder.() -> Unit) {
        every { userPreferencesService.flow } returns
            flow {
                emit(
                    UserPreferences(
                        AppPreferences.getDefaultInstance().update(block),
                        null,
                    ),
                )
            }
    }

    private fun setupAutoSignInUnprotected() {
        setupPreferences {
            signInAutomatically = true
            currentServerId = serverId.toServerString()
            currentUserId = userId.toServerString()
        }
        every { serverRepository.current } returns MutableStateFlow<CurrentUser?>(currentUser)
        coEvery { serverRepository.restoreSession(serverId, userId) } returns currentUser
    }

    @Test
    fun `Test auto sign in with unprotected profile`() =
        runTest {
            setupAutoSignInUnprotected()

            val intent = Intent()
            val result = intentService.prepare(intent)
            assertNull(result)

            coVerify { serverRepository.restoreSession(serverId, userId) }
        }

    @Test
    fun `Test auto sign disabled in with unprotected profile`() =
        runTest {
            setupPreferences {
                signInAutomatically = false
                currentServerId = serverId.toServerString()
                currentUserId = userId.toServerString()
            }
            every { serverRepository.current } returns MutableStateFlow<CurrentUser?>(currentUser)
            coEvery { serverRepository.restoreSession(serverId, userId) } returns currentUser

            val intent = Intent()
            val result = intentService.prepare(intent)
            assertTrue(result is IntentResult.Error)

            coVerify(exactly = 0) { serverRepository.restoreSession(serverId, userId) }
        }

    @Test
    fun `Test auto sign in, hot load, with protected profile`() =
        runTest {
            setupPreferences {
                signInAutomatically = true
                currentServerId = serverId.toServerString()
                currentUserId = userId.toServerString()
            }
            every { serverRepository.current } returns MutableStateFlow<CurrentUser?>(protectedCurrentUser)
            coEvery { serverRepository.restoreSession(serverId, userId) } returns protectedCurrentUser

            val intent = Intent()
            val result = intentService.prepare(intent)
            assertTrue(result is IntentResult.Error)

            coVerify(exactly = 0) { serverRepository.restoreSession(serverId, userId) }
        }

    @Test
    fun `Test auto sign in, cold load, with protected profile`() =
        runTest {
            setupPreferences {
                signInAutomatically = true
                currentServerId = serverId.toServerString()
                currentUserId = userId.toServerString()
            }
            every { serverRepository.current } returns MutableStateFlow<CurrentUser?>(null)
            coEvery { serverRepository.restoreSession(serverId, userId) } returns protectedCurrentUser

            val intent = Intent()
            val result = intentService.prepare(intent)
            assertTrue(result is IntentResult.Error)

            coVerify(exactly = 1) { serverRepository.restoreSession(serverId, userId) }
        }

    @Test
    fun `Test auto sign in, no current user`() =
        runTest {
            setupPreferences {
                signInAutomatically = true
                currentServerId = ""
                currentUserId = ""
            }
            every { serverRepository.current } returns MutableStateFlow<CurrentUser?>(null)
//            coEvery { serverRepository.restoreSession(serverId, userId) } returns protectedCurrentUser

            val intent = Intent()
            val result = intentService.prepare(intent)
            assertTrue(result is IntentResult.Error)

            coVerify(exactly = 0) { serverRepository.restoreSession(serverId, userId) }
        }

    @Test
    fun `Test user specified with unprotected profile`() =
        runTest {
            setupPreferences {
                signInAutomatically = true
                currentServerId = ""
                currentUserId = ""
            }
            every { serverRepository.current } returns MutableStateFlow<CurrentUser?>(null)
            coEvery { serverRepository.serverDao.getUser(serverId, userId) } returns currentUser.user
            coEvery { serverRepository.restoreSession(serverId, userId) } returns currentUser

            val intent =
                Intent().apply {
                    putExtra(IntentService.INTENT_SERVER_ID, serverId.toServerString())
                    putExtra(IntentService.INTENT_USER_ID, userId.toServerString())
                }
            val result = intentService.prepare(intent)
            assertNull(result)

            coVerify(exactly = 1) { serverRepository.restoreSession(serverId, userId) }
        }

    @Test
    fun `Test user specified with protected profile`() =
        runTest {
            setupPreferences {
                signInAutomatically = true
                currentServerId = ""
                currentUserId = ""
            }
            every { serverRepository.current } returns MutableStateFlow<CurrentUser?>(null)
            coEvery { serverRepository.serverDao.getUser(serverId, userId) } returns protectedCurrentUser.user
            coEvery { serverRepository.restoreSession(serverId, userId) } returns protectedCurrentUser

            val intent =
                Intent().apply {
                    putExtra(IntentService.INTENT_SERVER_ID, serverId.toServerString())
                    putExtra(IntentService.INTENT_USER_ID, userId.toServerString())
                }
            val result = intentService.prepare(intent)
            assertTrue(result is IntentResult.Error)

            coVerify(exactly = 0) { serverRepository.restoreSession(serverId, userId) }
        }

    @Test
    fun `Test user specified does not exist`() =
        runTest {
            setupPreferences {
                signInAutomatically = true
                currentServerId = ""
                currentUserId = ""
            }
            every { serverRepository.current } returns MutableStateFlow<CurrentUser?>(null)
            coEvery { serverRepository.serverDao.getUser(serverId, userId) } returns null
            coEvery { serverRepository.restoreSession(serverId, userId) } returns null

            val intent =
                Intent().apply {
                    putExtra(IntentService.INTENT_SERVER_ID, serverId.toServerString())
                    putExtra(IntentService.INTENT_USER_ID, userId.toServerString())
                }
            val result = intentService.prepare(intent)
            assertTrue(result is IntentResult.Error)

            coVerify(exactly = 0) { serverRepository.restoreSession(serverId, userId) }
        }

    @Test
    fun `Test parseIntent with view item`() =
        runTest {
            setupAutoSignInUnprotected()

            val intent =
                Intent().apply {
                    action = Intent.ACTION_VIEW
                    putExtra("itemId", itemId.toString())
                }
            val result = intentService.parseIntent(intent)
            assertIs<IntentResult.Target>(result)

            val destinations = result.destinations
            assertEquals(1, destinations.size)
            assertTrue(result.addHomeToBackStack)
            val first = destinations.first()
            assertIs<Destination.MediaItem>(first)

            assertEquals(itemId, first.itemId)

            coVerify { userLibraryApi.getItem(itemId) }
        }

    @Test
    fun `Test parseIntent with view and no itemId`() =
        runTest {
            setupAutoSignInUnprotected()

            val intent =
                Intent().apply {
                    action = Intent.ACTION_VIEW
                }
            val result = intentService.parseIntent(intent)
            assertIs<IntentResult.Target>(result)

            val destinations = result.destinations
            assertEquals(0, destinations.size)
            assertTrue(result.addHomeToBackStack)

            coVerify(exactly = 0) { userLibraryApi.getItem(itemId) }
        }

    @Test
    fun `Test parseIntent with view and switch user`() =
        runTest {
            setupAutoSignInUnprotected()

            val newUserId = UUID.randomUUID()
            val newServerId = UUID.randomUUID()
            val newCurrentUser = currentUser(newServerId, newUserId)

            every { serverRepository.current } returns MutableStateFlow<CurrentUser?>(currentUser)
            coEvery {
                serverRepository.restoreSession(
                    newServerId,
                    newUserId,
                )
            } returns newCurrentUser
            every { serverDao.getUser(newServerId, newUserId) } returns newCurrentUser.user

            val intent =
                Intent().apply {
                    action = Intent.ACTION_VIEW
                    putExtra("userId", newUserId.toString())
                    putExtra("serverId", newServerId.toString())
                }
            val result = intentService.parseIntent(intent)
            assertIs<IntentResult.Target>(result)

            val destinations = result.destinations
            assertEquals(0, destinations.size)
            assertTrue(result.addHomeToBackStack)

            coVerify(exactly = 0) { serverRepository.restoreSession(serverId, userId) }
            verify { serverDao.getUser(newServerId, newUserId) }
            coVerify { serverRepository.restoreSession(newServerId, newUserId) }
            coVerify(exactly = 0) { userLibraryApi.getItem(itemId) }
        }

    @Test
    fun `Test parseIntent with view, itemId, and switch user`() =
        runTest {
            setupAutoSignInUnprotected()

            val newUserId = UUID.randomUUID()
            val newServerId = UUID.randomUUID()
            val newCurrentUser = currentUser(newServerId, newUserId)

            every { serverRepository.current } returns MutableStateFlow<CurrentUser?>(currentUser)
            coEvery {
                serverRepository.restoreSession(
                    newServerId,
                    newUserId,
                )
            } returns newCurrentUser
            every { serverDao.getUser(newServerId, newUserId) } returns newCurrentUser.user

            val intent =
                Intent().apply {
                    action = Intent.ACTION_VIEW
                    putExtra("userId", newUserId.toString())
                    putExtra("serverId", newServerId.toString())
                    putExtra("itemId", itemId.toString())
                }
            val result = intentService.parseIntent(intent)
            assertIs<IntentResult.Target>(result)

            val destinations = result.destinations
            assertEquals(1, destinations.size)
            assertTrue(result.addHomeToBackStack)
            val first = destinations.first()
            assertIs<Destination.MediaItem>(first)

            assertEquals(itemId, first.itemId)

            coVerify(exactly = 0) { serverRepository.restoreSession(serverId, userId) }
            verify { serverDao.getUser(newServerId, newUserId) }
            coVerify { serverRepository.restoreSession(newServerId, newUserId) }
            coVerify { userLibraryApi.getItem(itemId) }
        }

    @Test
    fun `Test parseIntent with search`() =
        runTest {
            setupAutoSignInUnprotected()

            val intent =
                Intent().apply {
                    action = Intent.ACTION_SEARCH
                }
            val result = intentService.parseIntent(intent)
            assertIs<IntentResult.Target>(result)

            val destinations = result.destinations
            assertEquals(1, destinations.size)
            assertTrue(result.addHomeToBackStack)
            val first = destinations.first()
            assertIs<Destination.Search>(first)

            assertEquals("", first.query)
        }

    @Test
    fun `Test parseIntent with search query`() =
        runTest {
            setupAutoSignInUnprotected()

            val query = "query123"
            val intent =
                Intent().apply {
                    action = Intent.ACTION_SEARCH
                    putExtra(SearchManager.QUERY, query)
                }
            val result = intentService.parseIntent(intent)
            assertIs<IntentResult.Target>(result)

            val destinations = result.destinations
            assertEquals(1, destinations.size)
            assertTrue(result.addHomeToBackStack)
            val first = destinations.first()
            assertIs<Destination.Search>(first)

            assertEquals(query, first.query)
        }

    @Test
    fun `Test parseIntent with play but no itemId`() =
        runTest {
            setupAutoSignInUnprotected()

            val intent =
                Intent().apply {
                    action = IntentService.ACTION_PLAYBACK
                }
            val result = intentService.parseIntent(intent)
            assertIs<IntentResult.Error>(result)
        }

    @Test
    fun `Test parseIntent with play itemId`() =
        runTest {
            setupAutoSignInUnprotected()

            val intent =
                Intent().apply {
                    action = IntentService.ACTION_PLAYBACK
                    putExtra("itemId", itemId.toString())
                }
            val result = intentService.parseIntent(intent)
            assertIs<IntentResult.Target>(result)

            val destinations = result.destinations
            assertEquals(2, destinations.size)
            assertTrue(result.addHomeToBackStack)
            val first = destinations.first()
            val second = destinations[1]
            assertIs<Destination.MediaItem>(first)
            assertIs<Destination.Playback>(second)

            assertEquals(itemId, first.itemId)
            assertEquals(itemId, second.itemId)

            coVerify { userLibraryApi.getItem(itemId) }
        }

    @Test
    fun `Test parseIntent with play itemId and params`() =
        runTest {
            setupAutoSignInUnprotected()

            val position = 4_000L
            val shuffle = true
            val intent =
                Intent().apply {
                    action = IntentService.ACTION_PLAYBACK
                    putExtra("itemId", itemId.toString())
                    putExtra("position", position)
                    putExtra("shuffle", shuffle)
                }
            val result = intentService.parseIntent(intent)
            assertIs<IntentResult.Target>(result)

            val destinations = result.destinations
            assertEquals(2, destinations.size)
            assertTrue(result.addHomeToBackStack)
            val first = destinations.first()
            val second = destinations[1]
            assertIs<Destination.MediaItem>(first)
            assertIs<Destination.Playback>(second)

            assertEquals(itemId, first.itemId)
            assertEquals(itemId, second.itemId)
            assertEquals(shuffle, second.shuffle)
            assertEquals(position, second.positionMs)

            coVerify { userLibraryApi.getItem(itemId) }
        }
}
