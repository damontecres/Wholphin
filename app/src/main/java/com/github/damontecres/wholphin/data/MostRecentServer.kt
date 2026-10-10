package com.github.damontecres.wholphin.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.github.damontecres.wholphin.data.MostRecentServer.None
import com.github.damontecres.wholphin.data.MostRecentServer.Server
import com.github.damontecres.wholphin.data.MostRecentServer.ServerAndUser
import com.github.damontecres.wholphin.data.model.JellyfinServer
import com.github.damontecres.wholphin.services.SetupDestination
import com.github.damontecres.wholphin.ui.isNotNullOrBlank
import com.github.damontecres.wholphin.ui.toServerString
import org.jellyfin.sdk.model.serializer.toUUIDOrNull
import java.util.UUID

/**
 * Represents the status of the most recently used server
 *
 * This can be read/written to shared preferences
 */
sealed interface MostRecentServer {
    /**
     * There is no recently used server
     */
    data object None : MostRecentServer

    /**
     * The most recently used server without a recent user
     */
    data class Server(
        val serverId: UUID,
        val serverUrl: String,
    ) : MostRecentServer

    /**
     * Most recently used server and user
     */
    data class ServerAndUser(
        val serverId: UUID,
        val serverUrl: String,
        val userId: UUID,
        val accessToken: String,
    ) : MostRecentServer {
        override fun toString(): String = "ServerAndUser(serverId=$serverId, serverUrl=$serverUrl, userId=$userId)"
    }

    companion object {
        fun from(current: CurrentUser?): MostRecentServer =
            if (current != null && current.user.accessToken.isNotNullOrBlank()) {
                ServerAndUser(current.server.id, current.server.url, current.user.id, current.user.accessToken)
            } else if (current != null) {
                Server(current.server.id, current.server.url)
            } else {
                None
            }
    }
}

interface MostRecentServerProvider {
    /**
     * Get the [MostRecentServer] from the shared preferences in [context]
     */
    fun get(): MostRecentServer

    /**
     * Save the [CurrentUser] as the most recent server into the shared preferences in [context]
     */
    suspend fun save(current: CurrentUser)

    /**
     * Clear the most recent server and user
     */
    suspend fun clear()

    /**
     * CLear the most recent user, leaving the server
     */
    suspend fun clearUser()
}

/**
 * A [MostRecentServerProvider] that stores in [SharedPreferences]
 */
class MostRecentServerSharedPreferences(
    context: Context,
) : MostRecentServerProvider {
    private val prefs = getServerSharedPreferences(context)

    /**
     * Get the [MostRecentServer] from the shared preferences in [context]
     */
    override fun get(): MostRecentServer {
        val serverId = prefs.getString(SERVER_ID_KEY, null)?.toUUIDOrNull()
        val serverUrl = prefs.getString(SERVER_URL_KEY, null)
        val userId = prefs.getString(USER_ID_KEY, null)?.toUUIDOrNull()
        val accessToken = prefs.getString(USER_TOKEN_KEY, null)
        return if (serverId != null && serverUrl.isNotNullOrBlank() && userId != null && accessToken.isNotNullOrBlank()) {
            ServerAndUser(serverId, serverUrl, userId, accessToken)
        } else if (serverId != null && serverUrl.isNotNullOrBlank()) {
            Server(serverId, serverUrl)
        } else {
            None
        }
    }

    /**
     * Save the [CurrentUser] as the most recent server into the shared preferences in [context]
     */
    override suspend fun save(current: CurrentUser) {
        prefs.edit(true) {
            putString(SERVER_ID_KEY, current.server.id.toServerString())
            putString(SERVER_URL_KEY, current.server.url)
            putString(USER_ID_KEY, current.user.id.toServerString())
            putString(USER_TOKEN_KEY, current.user.accessToken)
        }
    }

    override suspend fun clear() {
        prefs.edit(true) {
            remove(SERVER_ID_KEY)
            remove(SERVER_URL_KEY)
            remove(USER_ID_KEY)
            remove(USER_TOKEN_KEY)
        }
    }

    override suspend fun clearUser() {
        prefs.edit(true) {
            remove(USER_ID_KEY)
            remove(USER_TOKEN_KEY)
        }
    }

    private fun getServerSharedPreferences(context: Context): SharedPreferences =
        context.getSharedPreferences(
            "${context.packageName}_server",
            Context.MODE_PRIVATE,
        )

    companion object {
        private const val SERVER_ID_KEY = "current.server.id"
        private const val SERVER_URL_KEY = "current.server.url"
        private const val USER_ID_KEY = "current.user.id"
        private const val USER_TOKEN_KEY = "current.user.accessToken"
    }
}

/**
 * The result of restoring a session
 */
sealed interface RestoredSession {
    val destination: SetupDestination

    /**
     * Session could not be restored
     */
    data object None : RestoredSession {
        override val destination: SetupDestination
            get() = SetupDestination.ServerList
    }

    /**
     * Only the server should be restored, so a user still need to be chosen
     */
    data class ServerOnly(
        val server: JellyfinServer,
    ) : RestoredSession {
        override val destination: SetupDestination
            get() = SetupDestination.UserList(server)
    }

    /**
     * Session was restored successfully
     */
    data class Success(
        val currentUser: CurrentUser,
    ) : RestoredSession {
        override val destination: SetupDestination
            get() = SetupDestination.AppContent(currentUser)
    }
}
