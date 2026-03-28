package com.github.damontecres.wholphin.services

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import org.json.JSONObject
import timber.log.Timber
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.net.URL
import java.util.UUID
import kotlin.coroutines.CoroutineContext

/**
 * Discovers Jellyfin servers on the local network via UDP broadcast (port 7359).
 *
 * Uses the sender's actual IP from the UDP packet instead of the server-reported
 * address, which avoids the common localhost/127.0.0.1 issue.
 */
class LocalServerDiscovery(
    private val dispatcher: CoroutineContext,
) {
    data class Result(
        val id: UUID,
        val name: String,
        val address: String,
    )

    fun discover(timeoutMs: Int = 3000): Flow<Result> =
        flow {
            val seen = mutableSetOf<String>()

            DatagramSocket().use { socket ->
                socket.broadcast = true
                socket.soTimeout = timeoutMs

                socket.send(
                    DatagramPacket(
                        BROADCAST_MSG,
                        BROADCAST_MSG.size,
                        InetAddress.getByName("255.255.255.255"),
                        PORT,
                    ),
                )

                val buf = ByteArray(4096)
                val deadline = System.currentTimeMillis() + timeoutMs

                while (System.currentTimeMillis() < deadline) {
                    try {
                        val pkt = DatagramPacket(buf, buf.size)
                        socket.receive(pkt)
                        val result = parse(pkt) ?: continue
                        if (seen.add(result.address)) emit(result)
                    } catch (_: SocketTimeoutException) {
                        break
                    }
                }
            }
        }.flowOn(dispatcher)

    private fun parse(pkt: DatagramPacket): Result? =
        try {
            val json = JSONObject(String(pkt.data, 0, pkt.length).trim())
            val senderIp = pkt.address.hostAddress ?: throw IllegalStateException("No sender IP")
            val port = json.optString("Address").extractPort()

            Result(
                id =
                    try {
                        UUID.fromString(json.optString("Id"))
                    } catch (_: Exception) {
                        UUID.randomUUID()
                    },
                name = json.optString("Name", "Jellyfin Server"),
                address = "http://$senderIp:$port",
            )
        } catch (e: Exception) {
            Timber.d(e, "Failed to parse discovery response")
            null
        }

    private fun String.extractPort(): Int =
        try {
            URL(this).port.takeIf { it > 0 } ?: DEFAULT_PORT
        } catch (_: Exception) {
            DEFAULT_PORT
        }

    companion object {
        private const val PORT = 7359
        private const val DEFAULT_PORT = 8096
        private val BROADCAST_MSG = "Who is JellyfinServer?".toByteArray()
    }
}
