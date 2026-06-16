package com.github.damontecres.wholphin.services

import android.content.Context
import android.content.SharedPreferences
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import com.github.damontecres.wholphin.data.CurrentUser
import com.github.damontecres.wholphin.data.JellyfinServerDao
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.data.model.JellyfinServer
import com.github.damontecres.wholphin.data.model.JellyfinServerUsers
import com.github.damontecres.wholphin.data.model.JellyfinUser
import com.github.damontecres.wholphin.preferences.AppPreferences
import com.github.damontecres.wholphin.preferences.AppPreferencesSerializer
import com.github.damontecres.wholphin.ui.toServerString
import com.github.damontecres.wholphin.util.GetItemsRequestHandler
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkObject
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
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
import org.jellyfin.sdk.model.api.PublicSystemInfo
import org.jellyfin.sdk.model.api.UserDto
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class TestServerRepository {
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
    private val mockSharedPreferences = mockk<SharedPreferences>(relaxed = true)

    @OptIn(ExperimentalCoroutinesApi::class)
    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        mockkStatic(Dispatchers::class)
        every { Dispatchers.IO } returns testDispatcher
        every { Dispatchers.Default } returns testDispatcher

        every { mockContext.getSharedPreferences(any(), any()) } returns mockSharedPreferences
        every { mockApiClient.userApi } returns mockUserApi
        every { mockApiClient.systemApi } returns mockSystemApi
        coEvery { mockUserApi.getCurrentUser() } returns Response(userDto, 200, emptyMap())
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
        every { mockApiClient.clientInfo } returns ClientInfo("Wholphin test", "0.0.1")
        every { mockApiClient.deviceInfo } returns DeviceInfo("Wholphin test ID", "Wholphin test device")
        every { mockApiClient.update(any(), any(), any(), any()) } just Runs
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkObject(GetItemsRequestHandler)
    }

    private fun create() =
        ServerRepository(
            mockContext,
            mockJellyfin,
            mockJellyfinServerDao,
            mockApiClient,
            dataStore,
            testDispatcher,
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
        )
    private val userDto =
        UserDto(
            id = userId,
            name = "test-user",
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

            val appPreferences = dataStore.data.first()
            Assert.assertEquals(serverId.toServerString(), appPreferences.currentServerId)
            Assert.assertEquals(userId.toServerString(), appPreferences.currentUserId)

            verify(exactly = 1) { mockSharedPreferences.edit() }
            verify(exactly = 1) { mockJellyfinServerDao.addOrUpdateUser(user) }
        }

    @Test
    fun `Test restoreUser via changeUser`() =
        runTest {
            every { mockJellyfinServerDao.addOrUpdateServer(any()) } just Runs
            every { mockJellyfinServerDao.addOrUpdateUser(user) } returns user
            every { mockJellyfinServerDao.getServer(serverId) } returns JellyfinServerUsers(server, listOf(user))

            val serverRepository = create()
            Assert.assertNull(serverRepository.currentUser)
            serverRepository.restoreSession(server.id, user.id)

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
    fun `Test restoreUser via shortcut`() =
        runTest {
            every { mockJellyfinServerDao.getServer(serverId) } returns JellyfinServerUsers(server, listOf(user))

            val serverRepository = create()
            setUpCurrentUser(serverRepository)

            serverRepository.restoreSession(server.id, user.id)

            verify(exactly = 1) {
                mockApiClient.update(
                    baseUrl = server.url,
                    accessToken = user.accessToken,
                    clientInfo = any(),
                    deviceInfo = any(),
                )
            }
            verify(exactly = 0) { mockJellyfinServerDao.addOrUpdateServer(any()) }
            verify(exactly = 0) { mockJellyfinServerDao.addOrUpdateUser(any()) }

            Assert.assertEquals(server, serverRepository.currentServer)
            Assert.assertEquals(user, serverRepository.currentUser)

            // TODO fix this, need to check userDto too
//            Assert.assertEquals(userDto, serverRepository.currentUserDto)
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
    fun `Test remove user that's not current`() =
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
            verify { mockApiClient.update(accessToken = null, baseUrl = any(), clientInfo = any(), deviceInfo = any()) }
            verify(exactly = 1) { mockJellyfinServerDao.deleteUser(serverId, userId) }
        }
}
