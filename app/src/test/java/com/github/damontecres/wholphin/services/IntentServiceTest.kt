package com.github.damontecres.wholphin.services

import android.content.Intent
import com.github.damontecres.wholphin.data.CurrentUser
import com.github.damontecres.wholphin.data.RestoredSession
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.preferences.AppPreferences
import com.github.damontecres.wholphin.preferences.UserPreferences
import com.github.damontecres.wholphin.preferences.update
import com.github.damontecres.wholphin.test.currentUser
import com.github.damontecres.wholphin.test.server
import com.github.damontecres.wholphin.test.user
import com.github.damontecres.wholphin.ui.toServerString
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.model.UUID
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
    private val userPreferencesService: UserPreferencesService = mockk()

    lateinit var intentService: IntentService

    private val serverId = UUID.randomUUID()
    private val userId = UUID.randomUUID()
    private val currentUser = currentUser(serverId, userId)

    private val protectedCurrentUser =
        CurrentUser(
            server(serverId),
            user(serverId, userId).copy(pin = "1234"),
        )

    @Before
    fun setup() {
        intentService = IntentService(api, serverRepository, userPreferencesService)
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

    @Test
    fun `Test auto sign in with unprotected profile`() =
        runTest {
            setupPreferences {
                signInAutomatically = true
            }
            every { serverRepository.current } returns MutableStateFlow<CurrentUser?>(currentUser)
            coEvery { serverRepository.restoreLastSession() } returns
                RestoredSession.Success(currentUser)
            coEvery { serverRepository.tryChangeUser(serverId, userId) } returns currentUser

            val intent = Intent()
            val result = intentService.prepare(intent)
            assertNull(result)

            coVerify { serverRepository.restoreLastSession() }
        }

    @Test
    fun `Test auto sign disabled in with unprotected profile`() =
        runTest {
            setupPreferences {
                signInAutomatically = false
            }
            every { serverRepository.current } returns MutableStateFlow<CurrentUser?>(currentUser)
            coEvery { serverRepository.restoreLastSession() } returns
                RestoredSession.Success(currentUser)
            coEvery { serverRepository.tryChangeUser(serverId, userId) } returns currentUser

            val intent = Intent()
            val result = intentService.prepare(intent)
            assertTrue(result is IntentResult.Error)

            coVerify(exactly = 0) { serverRepository.tryChangeUser(serverId, userId) }
        }

    @Test
    fun `Test auto sign in, hot load, with protected profile`() =
        runTest {
            setupPreferences {
                signInAutomatically = true
            }
            every { serverRepository.current } returns MutableStateFlow<CurrentUser?>(protectedCurrentUser)
            coEvery { serverRepository.tryChangeUser(serverId, userId) } returns null
            coEvery { serverRepository.restoreLastSession() } returns
                RestoredSession.ServerOnly(protectedCurrentUser.server)

            val intent = Intent()
            val result = intentService.prepare(intent)
            assertTrue(result is IntentResult.Error)

            coVerify(exactly = 0) { serverRepository.tryChangeUser(serverId, userId) }
        }

    @Test
    fun `Test auto sign in, cold load, with protected profile`() =
        runTest {
            setupPreferences {
                signInAutomatically = true
            }
            every { serverRepository.current } returns MutableStateFlow<CurrentUser?>(null)
            coEvery { serverRepository.tryChangeUser(serverId, userId) } returns null
            coEvery { serverRepository.restoreLastSession() } returns
                RestoredSession.ServerOnly(protectedCurrentUser.server)

            val intent = Intent()
            val result = intentService.prepare(intent)
            assertTrue(result is IntentResult.Error)

            coVerify(exactly = 1) { serverRepository.restoreLastSession() }
        }

    @Test
    fun `Test auto sign in, no current user`() =
        runTest {
            setupPreferences {
                signInAutomatically = true
            }
            every { serverRepository.current } returns MutableStateFlow<CurrentUser?>(null)
            coEvery { serverRepository.restoreLastSession() } returns RestoredSession.None
//            coEvery { serverRepository.restoreSession(serverId, userId) } returns protectedCurrentUser

            val intent = Intent()
            val result = intentService.prepare(intent)
            assertTrue(result is IntentResult.Error)

            coVerify(exactly = 0) { serverRepository.tryChangeUser(serverId, userId) }
        }

    @Test
    fun `Test user specified with unprotected profile`() =
        runTest {
            setupPreferences {
                signInAutomatically = true
            }
            every { serverRepository.current } returns MutableStateFlow<CurrentUser?>(null)
            coEvery { serverRepository.serverDao.getUser(serverId, userId) } returns currentUser.user
            coEvery { serverRepository.tryChangeUser(serverId, userId) } returns currentUser
            coEvery { serverRepository.restoreLastSession() } returns
                RestoredSession.Success(currentUser)

            val intent =
                Intent().apply {
                    putExtra(IntentService.INTENT_SERVER_ID, serverId.toServerString())
                    putExtra(IntentService.INTENT_USER_ID, userId.toServerString())
                }
            val result = intentService.prepare(intent)
            assertNull(result)

            coVerify(exactly = 1) { serverRepository.tryChangeUser(serverId, userId) }
        }

    @Test
    fun `Test user specified with protected profile`() =
        runTest {
            setupPreferences {
                signInAutomatically = true
            }
            every { serverRepository.current } returns MutableStateFlow<CurrentUser?>(null)
            coEvery { serverRepository.serverDao.getUser(serverId, userId) } returns protectedCurrentUser.user
            coEvery { serverRepository.tryChangeUser(serverId, userId) } returns null
//            coEvery { serverRepository.restoreLastSession() } returns
//                RestoredSession.ServerOnly(protectedCurrentUser.server)

            val intent =
                Intent().apply {
                    putExtra(IntentService.INTENT_SERVER_ID, serverId.toServerString())
                    putExtra(IntentService.INTENT_USER_ID, userId.toServerString())
                }
            val result = intentService.prepare(intent)
            assertTrue(result is IntentResult.Error)

            coVerify(exactly = 1) { serverRepository.tryChangeUser(serverId, userId) }
        }

    @Test
    fun `Test user specified does not exist`() =
        runTest {
            setupPreferences {
                signInAutomatically = true
            }
            every { serverRepository.current } returns MutableStateFlow<CurrentUser?>(null)
            coEvery { serverRepository.serverDao.getUser(serverId, userId) } returns null
            coEvery { serverRepository.tryChangeUser(serverId, userId) } returns null

            val intent =
                Intent().apply {
                    putExtra(IntentService.INTENT_SERVER_ID, serverId.toServerString())
                    putExtra(IntentService.INTENT_USER_ID, userId.toServerString())
                }
            val result = intentService.prepare(intent)
            assertTrue(result is IntentResult.Error)

            coVerify(exactly = 1) { serverRepository.tryChangeUser(serverId, userId) }
        }
}
