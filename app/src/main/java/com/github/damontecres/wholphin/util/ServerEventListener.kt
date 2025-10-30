package com.github.damontecres.wholphin.util

import android.content.Context
import android.widget.Toast
import com.github.damontecres.wholphin.data.model.JellyfinServer
import com.github.damontecres.wholphin.data.model.JellyfinUser
import com.github.damontecres.wholphin.hilt.IoCoroutineScope
import com.github.damontecres.wholphin.ui.launchIO
import com.github.damontecres.wholphin.ui.showToast
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.sessionApi
import org.jellyfin.sdk.api.sockets.subscribe
import org.jellyfin.sdk.model.api.GeneralCommandMessage
import org.jellyfin.sdk.model.api.GeneralCommandType
import org.jellyfin.sdk.model.api.MediaType
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ServerEventListener
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val api: ApiClient,
        @param:IoCoroutineScope private val scope: CoroutineScope,
    ) {
        private var listenJob: Job? = null

        fun init(
            server: JellyfinServer?,
            user: JellyfinUser?,
        ) {
            if (server != null && user != null && api.baseUrl != null && api.accessToken != null) {
                scope.launchIO {
                    api.sessionApi.postCapabilities(
                        playableMediaTypes = listOf(MediaType.VIDEO),
                        supportedCommands =
                            listOf(
                                GeneralCommandType.DISPLAY_MESSAGE,
                                GeneralCommandType.SEND_STRING,
                            ),
                        supportsMediaControl = true,
                    )
                    setupListeners()
                }
            }
        }

        fun setupListeners() {
            Timber.v("Subscribing to WebSocket")
            listenJob?.cancel()
            listenJob =
                api.webSocket
                    .subscribe<GeneralCommandMessage>()
                    .onEach { message ->
                        if (message.data?.name in
                            setOf(
                                GeneralCommandType.DISPLAY_MESSAGE,
                                GeneralCommandType.SEND_STRING,
                            )
                        ) {
                            val header = message.data?.arguments["Header"]
                            val text =
                                message.data?.arguments["Text"] ?: message.data?.arguments["String"]
                            val toast =
                                listOfNotNull(header, text)
                                    .joinToString("\n")
                            showToast(context, toast, Toast.LENGTH_LONG)
                        }
                    }.launchIn(scope)
        }
    }
