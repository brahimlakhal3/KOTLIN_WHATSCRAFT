package com.brahim.whatscraft.data

import com.brahim.whatscraft.data.model.AcceptResponse
import com.brahim.whatscraft.data.model.ApiErrorDto
import com.brahim.whatscraft.data.model.AuthResponse
import com.brahim.whatscraft.data.model.ChangePasswordRequest
import com.brahim.whatscraft.data.model.ConversationDto
import com.brahim.whatscraft.data.model.DeleteAccountRequest
import com.brahim.whatscraft.data.model.DeviceDto
import com.brahim.whatscraft.data.model.DeviceRegistration
import com.brahim.whatscraft.data.model.FriendGraphDto
import com.brahim.whatscraft.data.model.FriendRequest
import com.brahim.whatscraft.data.model.FriendRequestResponse
import com.brahim.whatscraft.data.model.LoginRequest
import com.brahim.whatscraft.data.model.MeResponse
import com.brahim.whatscraft.data.model.MessagesPageDto
import com.brahim.whatscraft.data.model.MuteRequest
import com.brahim.whatscraft.data.model.OpenConversationRequest
import com.brahim.whatscraft.data.model.PatchMeRequest
import com.brahim.whatscraft.data.model.PushTokenRequest
import com.brahim.whatscraft.data.model.ReadRequest
import com.brahim.whatscraft.data.model.RecoverRequest
import com.brahim.whatscraft.data.model.RecoveryCodeResponse
import com.brahim.whatscraft.data.model.RefreshRequest
import com.brahim.whatscraft.data.model.RegisterDeviceRequest
import com.brahim.whatscraft.data.model.RegisterRequest
import com.brahim.whatscraft.data.model.SendMessageRequest
import com.brahim.whatscraft.data.model.SendMessageResponse
import com.brahim.whatscraft.data.model.UserLookupDto
import com.brahim.whatscraft.data.model.WhatsCraftJson
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Supplies access tokens and can silently renew them when they expire. */
interface AuthProvider {
    suspend fun accessToken(): String?

    /**
     * Renews the token pair when [usedToken] is null or is still the current
     * access token. When another caller already renewed it, there is nothing
     * to do and the call simply succeeds.
     *
     * @return true when a usable access token is available.
     */
    suspend fun refresh(usedToken: String? = null): Boolean

    /**
     * A token fit for a long-lived link (the websocket): renewed shortly before
     * it expires so a reconnect never knocks on the relay with a dead token.
     */
    suspend fun freshAccessToken(): String? = accessToken()
}

/**
 * Thin, typed client for the relay's REST API.
 *
 * Bodies are encoded explicitly with their own serializers (no reflection), and
 * every authenticated call retries itself once after renewing an expired access
 * token, so callers never think about token lifetimes.
 */
class ApiClient(
    private val http: OkHttpClient,
    private val auth: AuthProvider,
    private val json: Json = WhatsCraftJson,
) {

    @Volatile
    var baseUrl: String = ""

    val isConfigured: Boolean get() = baseUrl.isNotBlank()

    fun webSocketUrl(): String {
        val normalized = baseUrl.trim().trimEnd('/')
        val scheme = when {
            normalized.startsWith("https://") -> "wss://" + normalized.removePrefix("https://")
            normalized.startsWith("http://") -> "ws://" + normalized.removePrefix("http://")
            else -> "wss://$normalized"
        }
        return "$scheme/ws"
    }

    // --------------------------------------------------------------- accounts --

    suspend fun health(): Boolean = runCatching {
        execute<JsonElement>("GET", "/v1/health", null, authenticated = false)
    }.isSuccess

    suspend fun register(body: RegisterRequest): AuthResponse =
        execute("POST", "/v1/auth/register", encode(RegisterRequest.serializer(), body), authenticated = false)

    suspend fun login(body: LoginRequest): AuthResponse =
        execute("POST", "/v1/auth/login", encode(LoginRequest.serializer(), body), authenticated = false)

    suspend fun refreshSession(body: RefreshRequest): AuthResponse =
        execute("POST", "/v1/auth/refresh", encode(RefreshRequest.serializer(), body), authenticated = false)

    suspend fun logout(refreshToken: String) {
        execute<Unit>(
            "POST",
            "/v1/auth/logout",
            encode(RefreshRequest.serializer(), RefreshRequest(refreshToken)),
            authenticated = false,
        )
    }

    suspend fun recover(body: RecoverRequest) {
        execute<Unit>(
            "POST",
            "/v1/auth/recover",
            encode(RecoverRequest.serializer(), body),
            authenticated = false,
        )
    }

    suspend fun rotateRecoveryCode(): RecoveryCodeResponse =
        execute("POST", "/v1/auth/recovery-code", null, authenticated = true)

    suspend fun me(): MeResponse = execute("GET", "/v1/me", null, authenticated = true)

    suspend fun patchMe(body: PatchMeRequest): MeResponse =
        execute("PATCH", "/v1/me", encode(PatchMeRequest.serializer(), body), authenticated = true)

    suspend fun changePassword(body: ChangePasswordRequest) {
        execute<Unit>("POST", "/v1/me/password", encode(ChangePasswordRequest.serializer(), body), authenticated = true)
    }

    suspend fun deleteAccount(password: String) {
        execute<Unit>(
            "DELETE",
            "/v1/me",
            encode(DeleteAccountRequest.serializer(), DeleteAccountRequest(password)),
            authenticated = true,
        )
    }

    // ---------------------------------------------------------------- friends --

    suspend fun lookupUser(username: String): UserLookupDto =
        execute("GET", "/v1/users/${username.trim().removePrefix("@").lowercase()}", null, authenticated = true)

    suspend fun friends(): FriendGraphDto = execute("GET", "/v1/friends", null, authenticated = true)

    suspend fun sendFriendRequest(username: String): FriendRequestResponse =
        execute(
            "POST",
            "/v1/friends/requests",
            encode(FriendRequest.serializer(), FriendRequest(username.trim().removePrefix("@"))),
            authenticated = true,
        )

    suspend fun acceptFriendRequest(requestId: String): AcceptResponse =
        execute("POST", "/v1/friends/requests/$requestId/accept", null, authenticated = true)

    suspend fun declineFriendRequest(requestId: String) {
        execute<Unit>("POST", "/v1/friends/requests/$requestId/decline", null, authenticated = true)
    }

    suspend fun removeFriend(userId: String) {
        execute<Unit>("DELETE", "/v1/friends/$userId", null, authenticated = true)
    }

    // ---------------------------------------------------------- conversations --

    suspend fun conversations(): List<ConversationDto> =
        execute<ConversationsEnvelope>("GET", "/v1/conversations", null, authenticated = true).conversations

    suspend fun openConversation(peerId: String): ConversationDto =
        execute<ConversationEnvelope>(
            "POST",
            "/v1/conversations",
            encode(OpenConversationRequest.serializer(), OpenConversationRequest(peerId)),
            authenticated = true,
        ).conversation

    suspend fun messages(conversationId: String, before: Long? = null, limit: Int = 40): MessagesPageDto {
        val query = buildString {
            append("?limit=").append(limit)
            if (before != null) append("&before=").append(before)
        }
        return execute("GET", "/v1/conversations/$conversationId/messages$query", null, authenticated = true)
    }

    suspend fun sendMessage(conversationId: String, body: SendMessageRequest): SendMessageResponse =
        execute(
            "POST",
            "/v1/conversations/$conversationId/messages",
            encode(SendMessageRequest.serializer(), body),
            authenticated = true,
        )

    suspend fun markRead(conversationId: String, seq: Long, receipt: Boolean = true) {
        execute<Unit>(
            "POST",
            "/v1/conversations/$conversationId/read",
            encode(ReadRequest.serializer(), ReadRequest(seq, receipt)),
            authenticated = true,
        )
    }

    suspend fun setMuted(conversationId: String, muted: Boolean) {
        execute<Unit>(
            "POST",
            "/v1/conversations/$conversationId/mute",
            encode(MuteRequest.serializer(), MuteRequest(muted)),
            authenticated = true,
        )
    }

    suspend fun clearConversation(conversationId: String) {
        execute<kotlinx.serialization.json.JsonElement>(
            "POST",
            "/v1/conversations/$conversationId/clear",
            null,
            authenticated = true,
        )
    }

    suspend fun clearAllMessages() {
        execute<kotlinx.serialization.json.JsonElement>(
            "POST",
            "/v1/messages/clear",
            null,
            authenticated = true,
        )
    }

    // ---------------------------------------------------------------- devices --

    suspend fun registerDevice(device: DeviceRegistration): DeviceDto =
        execute<DeviceEnvelope>(
            "POST",
            "/v1/devices",
            encode(RegisterDeviceRequest.serializer(), RegisterDeviceRequest(device)),
            authenticated = true,
        ).device

    suspend fun setPushToken(deviceId: String, token: String?) {
        execute<Unit>(
            "POST",
            "/v1/devices/$deviceId/push",
            encode(PushTokenRequest.serializer(), PushTokenRequest(token)),
            authenticated = true,
        )
    }

    // --------------------------------------------------------------- plumbing --

    private fun <V> encode(serializer: KSerializer<V>, value: V): String =
        json.encodeToString(serializer, value)

    /**
     * Performs one authenticated call, renewing an expired access token at most
     * once per call.
     */
    private suspend inline fun <reified T> execute(
        method: String,
        path: String,
        payload: String? = null,
        authenticated: Boolean = true,
    ): T {
        val base = baseUrl.trim().trimEnd('/')
        check(base.isNotBlank()) { "Server URL is not configured" }

        var renewed = false

        while (true) {
            var token: String? = null
            if (authenticated) {
                token = auth.accessToken() ?: if (auth.refresh()) auth.accessToken() else null
                if (token == null) throw ApiException("unauthorized", "Please sign in again.", 401)
            }

            val request = Request.Builder()
                .url(base + path)
                .apply {
                    val body = payload?.toRequestBody(JSON_MEDIA)
                    when (method) {
                        "GET" -> get()
                        "DELETE" -> delete(body)
                        "PATCH" -> patch(body ?: EMPTY_JSON)
                        "PUT" -> put(body ?: EMPTY_JSON)
                        else -> post(body ?: EMPTY_JSON)
                    }
                    token?.let { header("Authorization", "Bearer $it") }
                    header("Accept", "application/json")
                }
                .build()

            val response = try {
                http.newCall(request).await()
            } catch (error: Exception) {
                throw ApiException.network(error)
            }

            response.use { res ->
                val text = res.body?.string().orEmpty()

                if (res.code == 401 && authenticated && !renewed && auth.refresh(token)) {
                    renewed = true
                    return@use
                }

                if (!res.isSuccessful) {
                    val error = runCatching { json.decodeFromString<ApiErrorDto>(text) }.getOrNull()
                    throw ApiException(
                        code = error?.error ?: "http_${res.code}",
                        message = error?.message ?: defaultMessage(res.code),
                        status = res.code,
                    )
                }

                if (T::class == Unit::class || text.isBlank()) {
                    @Suppress("UNCHECKED_CAST")
                    return Unit as T
                }
                return try {
                    json.decodeFromString<T>(text)
                } catch (error: Exception) {
                    throw ApiException("bad_response", "The server sent something unexpected.", res.code)
                }
            }
        }
    }

    private fun defaultMessage(status: Int): String = when (status) {
        401 -> "Please sign in again."
        403 -> "You don't have access to that."
        404 -> "Not found."
        409 -> "That is already taken."
        429 -> "Too many attempts. Try again in a minute."
        in 500..599 -> "The server had a problem. Try again."
        else -> "Something went wrong."
    }

    @kotlinx.serialization.Serializable
    private data class ConversationsEnvelope(val conversations: List<ConversationDto> = emptyList())

    @kotlinx.serialization.Serializable
    private data class ConversationEnvelope(val conversation: ConversationDto)

    @kotlinx.serialization.Serializable
    private data class DeviceEnvelope(val device: DeviceDto)

    private companion object {
        val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
        val EMPTY_JSON = "{}".toRequestBody(JSON_MEDIA)
    }
}

/** Bridges OkHttp's callback API to coroutines so calls are cancellable. */
internal suspend fun Call.await(): Response = suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation { cancel() }
    enqueue(object : Callback {
        override fun onFailure(call: Call, e: java.io.IOException) {
            if (continuation.isActive) continuation.resumeWithException(e)
        }

        override fun onResponse(call: Call, response: Response) {
            if (continuation.isActive) continuation.resume(response) else response.close()
        }
    })
}
