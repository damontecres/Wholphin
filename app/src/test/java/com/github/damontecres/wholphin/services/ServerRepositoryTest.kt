package com.github.damontecres.wholphin.services

import android.content.Context
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import com.github.damontecres.wholphin.data.CurrentUser
import com.github.damontecres.wholphin.data.JellyfinServerDao
import com.github.damontecres.wholphin.data.MostRecentServer
import com.github.damontecres.wholphin.data.MostRecentServerProvider
import com.github.damontecres.wholphin.data.RestoredSession
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.data.model.JellyfinServer
import com.github.damontecres.wholphin.data.model.JellyfinServerUsers
import com.github.damontecres.wholphin.data.model.JellyfinUser
import com.github.damontecres.wholphin.preferences.AppPreferences
import com.github.damontecres.wholphin.preferences.AppPreferencesSerializer
import com.github.damontecres.wholphin.test.nonBlankString
import com.github.damontecres.wholphin.ui.isNotNullOrBlank
import com.github.damontecres.wholphin.ui.successResponse
import com.github.damontecres.wholphin.ui.toServerString
import com.github.damontecres.wholphin.util.WholphinDispatchers
import com.github.damontecres.wholphin.util.configure
import com.github.damontecres.wholphin.util.reset
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.jellyfin.sdk.Jellyfin
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.Response
import org.jellyfin.sdk.api.client.extensions.systemApi
import org.jellyfin.sdk.api.client.extensions.userApi
import org.jellyfin.sdk.api.operations.SystemApi
import org.jellyfin.sdk.api.operations.UserApi
import org.jellyfin.sdk.model.ClientInfo
import org.jellyfin.sdk.model.DeviceInfo
import org.jellyfin.sdk.model.UUID
import org.jellyfin.sdk.model.api.AuthenticationResult
import org.jellyfin.sdk.model.api.PublicSystemInfo
import org.jellyfin.sdk.model.api.UserDto
import org.junit.After
import org.junit.Assert
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ServerRepositoryTest {
    private val testDispatcher = StandardTestDispatcher()

    @get:Rule
    val temporaryFolder: TemporaryFolder =
        TemporaryFolder
            .builder()
            .assureDeletion()
            .build()

    private val mockContext = mockk<Context>(relaxed = true)
    private val mockJellyfin = mockk<Jellyfin>()
    private val mockJellyfinServerDao = mockk<JellyfinServerDao>()
    private val mockApiClient = mockk<ApiClient>()
    private val dataStore =
        DataStoreFactory.create(
            serializer = AppPreferencesSerializer(),
            produceFile = { temporaryFolder.newFile("test_datastore.pb") },
            scope = CoroutineScope(testDispatcher),
            corruptionHandler =
                ReplaceFileCorruptionHandler(
                    produceNewData = { AppPreferences.getDefaultInstance() },
                ),
        )

    private val mockUserApi = mockk<UserApi>()
    private val mockSystemApi = mockk<SystemApi>()
    private val mockMostRecentServerProvider = mockk<MostRecentServerProvider>()

    @OptIn(ExperimentalCoroutinesApi::class)
    @Before
    fun setup() {
        WholphinDispatchers.configure(testDispatcher)

        every { mockApiClient.userApi } returns mockUserApi
        every { mockApiClient.systemApi } returns mockSystemApi
        coEvery { mockUserApi.getCurrentUser() } returns successResponse(userDto)
        coEvery { mockSystemApi.getPublicSystemInfo() } returns
            Response(
                PublicSystemInfo(
                    id = serverId.toServerString(),
                    serverName = "test server",
                    version = "10.11.11",
                ),
                200,
                emptyMap(),
            )
        coEvery { mockJellyfinServerDao.addOrUpdateUser(user) } returns user
        coEvery { mockJellyfinServerDao.getServer(serverId) } returns
            JellyfinServerUsers(server, listOf(user))
        coEvery { mockJellyfinServerDao.addOrUpdateServer(server) } just Runs
        every { mockApiClient.clientInfo } returns ClientInfo("Wholphin test", "0.0.1")
        every { mockApiClient.deviceInfo } returns DeviceInfo("Wholphin test ID", "Wholphin test device")
        every { mockApiClient.update(any(), any(), any(), any()) } just Runs
        coEvery { mockMostRecentServerProvider.save(any()) } just Runs
        coEvery { mockMostRecentServerProvider.clear() } just Runs
        coEvery { mockMostRecentServerProvider.clearUser() } just Runs
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @After
    fun tearDown() {
        WholphinDispatchers.reset()
    }

    private fun create() =
        ServerRepository(
            mockContext,
            mockJellyfin,
            mockJellyfinServerDao,
            mockApiClient,
            testDispatcher,
            mockMostRecentServerProvider,
        )

    private val serverId = UUID.randomUUID()
    private val userId = UUID.randomUUID()
    private val server = JellyfinServer(serverId, "test server", "http://localhost:8096", "10.11.11")
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

    private fun setUpCurrentUser(
        serverRepository: ServerRepository,
        currentUser: JellyfinUser = user,
    ) {
        every { mockApiClient.baseUrl } returns server.url
        every { mockApiClient.accessToken } returns user.accessToken
        Assert.assertNull(serverRepository.currentUser)
        (serverRepository.current as MutableStateFlow).update {
            CurrentUser(server, currentUser)
        }
        Assert.assertNotNull(serverRepository.currentUser)
    }

    @Test
    fun `Test overriding state`() {
        val serverRepository = create()
        Assert.assertNull(serverRepository.currentServer)
        Assert.assertNull(serverRepository.currentUser)
        setUpCurrentUser(serverRepository, currentUser = user)
        Assert.assertEquals(serverId, serverRepository.currentServer?.id)
        Assert.assertEquals(userId, serverRepository.currentUser?.id)
    }

    @Test
    fun `Test changeUser`() =
        runTest {
            every { mockJellyfinServerDao.addOrUpdateServer(any()) } just Runs
            every { mockJellyfinServerDao.addOrUpdateUser(user) } returns user

            val serverRepository = create()
            Assert.assertNull(serverRepository.currentUser)

            serverRepository.changeUser(server, user)
            verify(exactly = 1) {
                mockApiClient.update(
                    baseUrl = server.url,
                    accessToken = user.accessToken,
                    clientInfo = any(),
                    deviceInfo = any(),
                )
            }
            verify(exactly = 1) { mockJellyfinServerDao.addOrUpdateServer(server) }
            verify(exactly = 1) { mockJellyfinServerDao.addOrUpdateUser(user) }

            Assert.assertEquals(server, serverRepository.currentServer)
            Assert.assertEquals(user, serverRepository.currentUser)
            Assert.assertEquals(userDto, serverRepository.currentUserDto)

            coVerify(exactly = 1) { mockMostRecentServerProvider.save(CurrentUser(server, user)) }
            verify(exactly = 1) { mockJellyfinServerDao.addOrUpdateUser(user) }
        }

    @Test
    fun `Test restoreUser via changeUser`() =
        runTest {
            val user = user.copy(pin = null)
            every { mockJellyfinServerDao.addOrUpdateServer(any()) } just Runs
            every { mockJellyfinServerDao.addOrUpdateUser(user) } returns user
            every { mockJellyfinServerDao.getServer(serverId) } returns JellyfinServerUsers(server, listOf(user))

            val serverRepository = create()
            Assert.assertNull(serverRepository.currentUser)
            val result = serverRepository.tryRestoreSession(server.id, user.id)
            assertTrue(result != null)
            assertEquals(serverId, result?.server?.id)
            assertEquals(user.id, result?.user?.id)

            verify(exactly = 1) {
                mockApiClient.update(
                    baseUrl = server.url,
                    accessToken = user.accessToken,
                    clientInfo = any(),
                    deviceInfo = any(),
                )
            }
            Assert.assertEquals(server, serverRepository.currentServer)
            Assert.assertEquals(user, serverRepository.currentUser)
            Assert.assertEquals(userDto, serverRepository.currentUserDto)
        }

    @Test
    fun `Test remove user`() =
        runTest {
            coEvery { mockJellyfinServerDao.deleteUser(any(), any()) } just Runs

            val serverRepository = create()
            Assert.assertNull(serverRepository.currentUser)

            serverRepository.removeUser(user)
            verify(exactly = 1) { mockJellyfinServerDao.deleteUser(serverId, userId) }
        }

    @Test
    fun `Test remove different user than current`() =
        runTest {
            coEvery { mockJellyfinServerDao.deleteUser(any(), any()) } just Runs

            val otherUser =
                JellyfinUser(
                    rowId = 2,
                    id = UUID.randomUUID(),
                    name = "other",
                    serverId = serverId,
                    accessToken = "other token",
                )
            val serverRepository = create()
            setUpCurrentUser(serverRepository, currentUser = otherUser)
            Assert.assertEquals(otherUser, serverRepository.currentUser)

            serverRepository.removeUser(user)
            verify(exactly = 0) { mockApiClient.update(accessToken = null) }
            verify(exactly = 1) { mockJellyfinServerDao.deleteUser(serverId, userId) }
            Assert.assertEquals(otherUser, serverRepository.currentUser)
        }

    @Test
    fun `Test remove current user`() =
        runTest {
            coEvery { mockJellyfinServerDao.deleteUser(any(), any()) } just Runs
            val serverRepository = create()
            setUpCurrentUser(serverRepository, currentUser = user)

            serverRepository.removeUser(user)

            Assert.assertNull(serverRepository.current.value)
            verify {
                mockApiClient.update(
                    accessToken = null,
                    baseUrl = nonBlankString(),
                    clientInfo = any(),
                    deviceInfo = any(),
                )
            }
            verify(exactly = 1) { mockJellyfinServerDao.deleteUser(serverId, userId) }
            coVerify { mockMostRecentServerProvider.clearUser() }
        }

    @Test
    fun `Test remove different server than current`() =
        runTest {
            coEvery { mockJellyfinServerDao.deleteServer(any()) } just Runs
            val serverRepository = create()
            setUpCurrentUser(serverRepository, currentUser = user)

            val toRemove = JellyfinServer(UUID.randomUUID(), null, "http://jellyfin.local", null)
            serverRepository.removeServer(toRemove)

            Assert.assertEquals(server, serverRepository.currentServer)
            verify(exactly = 0) {
                mockApiClient.update(
                    accessToken = any(),
                    baseUrl = any(),
                    clientInfo = any(),
                    deviceInfo = any(),
                )
            }
            verify(exactly = 1) { mockJellyfinServerDao.deleteServer(toRemove.id) }
        }

    @Test
    fun `Test remove current server`() =
        runTest {
            coEvery { mockJellyfinServerDao.deleteServer(any()) } just Runs
            val serverRepository = create()
            setUpCurrentUser(serverRepository, currentUser = user)

            serverRepository.removeServer(server)

            Assert.assertNull(serverRepository.current.value)
            verify {
                mockApiClient.update(
                    accessToken = null,
                    baseUrl = null,
                    clientInfo = any(),
                    deviceInfo = any(),
                )
            }
            verify(exactly = 1) { mockJellyfinServerDao.deleteServer(serverId) }
            coVerify { mockMostRecentServerProvider.clear() }
        }

    @Test
    fun `Test changeUser via auth`() =
        runTest {
            every { mockJellyfinServerDao.addOrUpdateServer(any()) } just Runs
            every { mockJellyfinServerDao.addOrUpdateUser(any()) } returns user

            val authResult =
                AuthenticationResult(
                    user = userDto,
                    serverId = serverId.toServerString(),
                    accessToken = user.accessToken,
                )
            val serverRepository = create()
            serverRepository.changeUser(server.url, authResult, null)

            verify(exactly = 1) {
                mockApiClient.update(
                    baseUrl = server.url,
                    accessToken = user.accessToken,
                    clientInfo = any(),
                    deviceInfo = any(),
                )
            }
            verify(exactly = 1) { mockJellyfinServerDao.addOrUpdateServer(server) }
            val savedUser = slot<JellyfinUser>()
            verify(exactly = 1) { mockJellyfinServerDao.addOrUpdateUser(capture(savedUser)) }
            // Newly created user, so rowId should be 0
            Assert.assertEquals(0, savedUser.captured.rowId)
            Assert.assertEquals(user.id, savedUser.captured.id)

            Assert.assertEquals(server, serverRepository.currentServer)
            Assert.assertEquals(user, serverRepository.currentUser)
            Assert.assertEquals(userDto, serverRepository.currentUserDto)

            coVerify(exactly = 1) { mockMostRecentServerProvider.save(CurrentUser(server, user)) }
        }

    @Test
    fun `Test changeUser via auth for existing`() =
        runTest {
            every { mockJellyfinServerDao.addOrUpdateServer(any()) } just Runs
            every { mockJellyfinServerDao.addOrUpdateUser(any()) } returns user

            val authResult =
                AuthenticationResult(
                    user = userDto,
                    serverId = serverId.toServerString(),
                    accessToken = user.accessToken,
                )
            val serverRepository = create()
            serverRepository.changeUser(server.url, authResult, user)

            verify(exactly = 1) {
                mockApiClient.update(
                    baseUrl = server.url,
                    accessToken = user.accessToken,
                    clientInfo = any(),
                    deviceInfo = any(),
                )
            }
            verify(exactly = 1) { mockJellyfinServerDao.addOrUpdateServer(server) }
            val savedUser = slot<JellyfinUser>()
            verify(exactly = 1) { mockJellyfinServerDao.addOrUpdateUser(capture(savedUser)) }
            // Existing user, so rowId should not change
            Assert.assertEquals(user.rowId, savedUser.captured.rowId)
            Assert.assertEquals(user.id, savedUser.captured.id)
            // PIN should be removed when re-authing existing user
            Assert.assertNull(savedUser.captured.pin)

            Assert.assertEquals(server, serverRepository.currentServer)
            Assert.assertEquals(user, serverRepository.currentUser)
            Assert.assertEquals(userDto, serverRepository.currentUserDto)

            coVerify(exactly = 1) { mockMostRecentServerProvider.save(CurrentUser(server, user)) }
        }

    @Test
    fun `Test changeUser via invalid auth`() {
        Assert.assertThrows(IllegalArgumentException::class.java) {
            runTest {
                val authResult =
                    AuthenticationResult(
                        user = userDto,
                        serverId = serverId.toServerString(),
                        accessToken = null,
                    )
                create().changeUser(server.url, authResult, user)
            }
        }
        Assert.assertThrows(IllegalArgumentException::class.java) {
            runTest {
                val authResult =
                    AuthenticationResult(
                        user = userDto,
                        serverId = null,
                        accessToken = user.accessToken,
                    )
                create().changeUser(server.url, authResult, user)
            }
        }
        Assert.assertThrows(IllegalArgumentException::class.java) {
            runTest {
                val authResult =
                    AuthenticationResult(
                        user = userDto,
                        serverId = "invalid-uuid",
                        accessToken = user.accessToken,
                    )
                create().changeUser(server.url, authResult, user)
            }
        }
        Assert.assertThrows(IllegalArgumentException::class.java) {
            runTest {
                val authResult =
                    AuthenticationResult(
                        user = null,
                        serverId = "invalid-uuid",
                        accessToken = user.accessToken,
                    )
                create().changeUser(server.url, authResult, user)
            }
        }
    }

    @Test
    fun `Test restoreLastSession, success`() =
        runTest {
            val user = user.copy(pin = null)
            coEvery { mockJellyfinServerDao.addOrUpdateUser(user) } returns user
            coEvery { mockJellyfinServerDao.getServer(serverId) } returns
                JellyfinServerUsers(server, listOf(user))
            every { mockMostRecentServerProvider.get() } returns
                MostRecentServer.from(CurrentUser(server, user))

            val result = create().restoreLastSession()
            assertTrue(result is RestoredSession.Success)
            val current = (result as RestoredSession.Success).currentUser
            assertEquals(server, current.server)
            assertEquals(user, current.user)

            verify(exactly = 1) {
                mockApiClient.update(
                    baseUrl = server.url,
                    accessToken = user.accessToken,
                    clientInfo = any(),
                    deviceInfo = any(),
                )
            }
            coVerify { mockMostRecentServerProvider.save(any()) }
        }

    @Test
    fun `Test restoreLastSession, user protected`() =
        runTest {
            every { mockMostRecentServerProvider.get() } returns
                MostRecentServer.ServerAndUser(serverId, server.url, userId, "token")

            val result = create().restoreLastSession()
            assertTrue(result is RestoredSession.ServerOnly)
            val current = (result as RestoredSession.ServerOnly)
            assertEquals(server, current.server)
        }

    @Test
    fun `Test restoreLastSession, server only`() =
        runTest {
            every { mockMostRecentServerProvider.get() } returns
                MostRecentServer.Server(server.id, server.url)

            val result = create().restoreLastSession()
            assertTrue(result is RestoredSession.ServerOnly)
            val current = (result as RestoredSession.ServerOnly)
            assertEquals(server, current.server)
        }

    @Test
    fun `Test restoreLastSession, nothing to restore`() =
        runTest {
            every { mockMostRecentServerProvider.get() } returns MostRecentServer.None

            val result = create().restoreLastSession()
            assertTrue(result is RestoredSession.None)
        }
}

class TestMostRecentServerProvider : MostRecentServerProvider {
    var mostRecentServer: MostRecentServer = MostRecentServer.None

    override fun get(): MostRecentServer = mostRecentServer

    override suspend fun save(current: CurrentUser) {
        mostRecentServer =
            if (current.user.accessToken.isNotNullOrBlank()) {
                MostRecentServer.ServerAndUser(
                    current.server.id,
                    current.server.url,
                    current.user.id,
                    current.user.accessToken,
                )
            } else {
                MostRecentServer.Server(
                    current.server.id,
                    current.server.url,
                )
            }
    }

    override suspend fun clear() {
        mostRecentServer = MostRecentServer.None
    }

    override suspend fun clearUser() {
        when (val s = mostRecentServer) {
            MostRecentServer.None -> Unit
            is MostRecentServer.Server -> Unit
            is MostRecentServer.ServerAndUser -> MostRecentServer.Server(s.serverId, s.serverUrl)
        }
    }
}
