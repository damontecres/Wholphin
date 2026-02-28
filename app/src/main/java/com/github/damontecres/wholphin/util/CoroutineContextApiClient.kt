package com.github.damontecres.wholphin.util

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.ApiClientFactory
import org.jellyfin.sdk.api.client.HttpClientOptions
import org.jellyfin.sdk.api.client.HttpMethod
import org.jellyfin.sdk.api.client.RawResponse
import org.jellyfin.sdk.api.okhttp.OkHttpFactory
import org.jellyfin.sdk.api.sockets.SocketApi
import org.jellyfin.sdk.api.sockets.SocketConnection
import org.jellyfin.sdk.api.sockets.SocketConnectionFactory
import org.jellyfin.sdk.model.ClientInfo
import org.jellyfin.sdk.model.DeviceInfo
import kotlin.coroutines.CoroutineContext

/**
 * Wraps [ApiClient.request] with the given [CoroutineContext]
 */
class CoroutineContextApiClient(
    private val client: ApiClient,
    private val coroutineContext: CoroutineContext = Dispatchers.IO,
) : ApiClient() {
    override val baseUrl: String?
        get() = client.baseUrl
    override val accessToken: String?
        get() = client.accessToken
    override val clientInfo: ClientInfo
        get() = client.clientInfo
    override val deviceInfo: DeviceInfo
        get() = client.deviceInfo
    override val httpClientOptions: HttpClientOptions
        get() = client.httpClientOptions
    override val webSocket: SocketApi
        get() = client.webSocket

    override fun update(
        baseUrl: String?,
        accessToken: String?,
        clientInfo: ClientInfo,
        deviceInfo: DeviceInfo,
    ) {
        client.update(baseUrl, accessToken, clientInfo, deviceInfo)
    }

    override suspend fun request(
        method: HttpMethod,
        pathTemplate: String,
        pathParameters: Map<String, Any?>,
        queryParameters: Map<String, Any?>,
        requestBody: Any?,
    ): RawResponse =
        withContext(coroutineContext) {
            client.request(
                method,
                pathTemplate,
                pathParameters,
                queryParameters,
                requestBody,
            )
        }
}

class CoroutineContextApiClientFactory(
    private val factory: OkHttpFactory,
    private val coroutineContext: CoroutineContext = Dispatchers.IO,
) : ApiClientFactory,
    SocketConnectionFactory {
    override fun create(
        baseUrl: String?,
        accessToken: String?,
        clientInfo: ClientInfo,
        deviceInfo: DeviceInfo,
        httpClientOptions: HttpClientOptions,
        socketConnectionFactory: SocketConnectionFactory,
    ): ApiClient =
        CoroutineContextApiClient(
            factory.create(baseUrl, accessToken, clientInfo, deviceInfo, httpClientOptions, socketConnectionFactory),
            coroutineContext,
        )

    override fun create(
        clientOptions: HttpClientOptions,
        scope: CoroutineScope,
    ): SocketConnection = factory.create(clientOptions, scope)
}
