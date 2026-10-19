package com.brahim.whatscraft.data

import com.brahim.whatscraft.data.model.MessageBodyDto
import com.brahim.whatscraft.data.model.ServerFrame
import com.brahim.whatscraft.data.model.WhatsCraftJson
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

/**
 * The realtime link to the relay.
 *
 * One connection is kept alive for the life of the process: it authenticates
 * with a short-lived access token, emits every incoming frame, and reconnects
 * with exponential backoff. When the relay closes the socket with 4001 the
 * access token has expired and the next attempt asks for a fresh one.
 */
class SocketClient(
    private val http: OkHttpClient,
    private val json: Json = WhatsCraftJson,
) {

    sealed interface State {
        data object Idle : State
        data object Connecting : State
        data object Connected : State
        data class Reconnecting(val attempt: Int) : State
        data class Degraded(val reason: String) : State
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    private val _frames = MutableSharedFlow<ServerFrame>(
        extraBufferCapacity = 128,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val frames: SharedFlow<ServerFrame> = _frames.asSharedFlow()

    @Volatile
    private var socket: WebSocket? = null

    private var connectionLoop: Job? = null

    val isConnected: Boolean get() = _state.value is State.Connected

    /**
     * Starts (or restarts) the connection loop. [urlProvider] and [tokenProvider]
     * are called on every attempt, so a rotated token or a new server URL is
     * picked up without restarting anything.
     */
    fun start(
        scope: CoroutineScope,
        urlProvider: suspend () -> String?,
        tokenProvider: suspend () -> String?,
    ) {
        stop()
        connectionLoop = scope.launch {
            var attempt = 0
            while (isActive) {
                val url = urlProvider()
                val token = tokenProvider()
                if (url.isNullOrBlank()) {
                    _state.value = State.Degraded("No server configured")
                    delay(2_000)
                    continue
                }
                if (token.isNullOrBlank()) {
                    _state.value = State.Degraded("Not signed in")
                    delay(2_000)
                    continue
                }

                _state.value = if (attempt == 0) State.Connecting else State.Reconnecting(attempt)
                val outcome = runConnection(url, token)
                if (!isActive) break

                attempt = when {
                    outcome.ready -> 1
                    else -> (attempt + 1).coerceAtMost(MAX_BACKOFF_STEP)
                }
                _state.value = State.Reconnecting(attempt)
                delay(backoffMillis(attempt))
            }
        }
    }

    fun stop() {
        connectionLoop?.cancel()
        connectionLoop = null
        socket?.close(1000, "client_disconnect")
        socket = null
        _state.value = State.Idle
    }

    /** Drops the current socket and reconnects immediately with a fresh token. */
    fun reconnectNow() {
        socket?.close(1000, "reconnect")
    }

    // -------------------------------------------------------------- outgoing --

    fun sendMessage(
        id: String,
        conversationId: String,
        body: MessageBodyDto,
        clientId: String,
        kind: String = "text",
    ): Boolean = send(
        buildJsonObject {
            put("t", "send")
            put("id", id)
            put("clientId", clientId)
            put("conversationId", conversationId)
            put("kind", kind)
            put("body", json.encodeToJsonElement(MessageBodyDto.serializer(), body))
        },
    )

    fun sendTyping(conversationId: String, start: Boolean): Boolean = send(
        buildJsonObject {
            put("t", "typing")
            put("conversationId", conversationId)
            put("state", if (start) "start" else "stop")
        },
    )

    fun sendRead(conversationId: String, seq: Long): Boolean = send(
        buildJsonObject {
            put("t", "read")
            put("conversationId", conversationId)
            put("seq", seq)
        },
    )

    fun ping(): Boolean = send(buildJsonObject { put("t", "ping") })

    fun send(frame: kotlinx.serialization.json.JsonObject): Boolean =
        socket?.send(frame.toString()) ?: false

    // --------------------------------------------------------------- private --

    private data class Outcome(val ready: Boolean)

    private suspend fun runConnection(url: String, token: String): Outcome {
        val ready = CompletableDeferred<Boolean>()
        val closed = CompletableDeferred<Int>()

        val listener = object : WebSocketListener() {

            override fun onOpen(webSocket: WebSocket, response: Response) {
                webSocket.send(
                    buildJsonObject {
                        put("t", "auth")
                        put("token", token)
                    }.toString(),
                )
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                val frame = parse(text) ?: return
                if (frame is ServerFrame.Ready) ready.complete(true)
                if (frame is ServerFrame.Error && !ready.isCompleted) ready.complete(false)
                _frames.tryEmit(frame)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(1000, null)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (!ready.isCompleted) ready.complete(false)
                closed.complete(code)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (!ready.isCompleted) ready.complete(false)
                closed.complete(-1)
            }
        }

        val webSocket = http.newWebSocket(Request.Builder().url(url).build(), listener)
        socket = webSocket

        try {
            val handshake = withTimeoutOrNull(HANDSHAKE_TIMEOUT_MS) { ready.await() } ?: false
            if (!handshake) {
                webSocket.close(1000, "handshake_failed")
                return Outcome(ready = false)
            }

            _state.value = State.Connected
            // Stay here until the connection drops.
            withTimeoutOrNull(Long.MAX_VALUE) { closed.await() }
            return Outcome(ready = true)
        } finally {
            if (socket === webSocket) socket = null
            runCatching { webSocket.cancel() }
        }
    }

    private fun parse(text: String): ServerFrame? = runCatching {
        json.decodeFromString(ServerFrame.serializer(), text)
    }.getOrNull()

    private fun backoffMillis(attempt: Int): Long =
        (1_000L shl (attempt - 1).coerceIn(0, 5)).coerceAtMost(30_000L)

    private companion object {
        const val HANDSHAKE_TIMEOUT_MS = 20_000L
        const val MAX_BACKOFF_STEP = 6
    }
}
