package com.brahim.whatscraft.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Wire models. Field names match the relay exactly, so the JSON that travels
 * between the app and the server is the same shape seen in the smoke test.
 */

val WhatsCraftJson: Json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    encodeDefaults = true
    classDiscriminator = "t"
}

// ----------------------------------------------------------------- accounts --

@Serializable
data class UserDto(
    val id: String,
    val username: String,
    val displayName: String,
    val about: String = "",
    val shade: Int = 0,
    val lastSeen: String? = null,
)

@Serializable
data class DeviceDto(
    val id: String,
    val name: String = "Phone",
    val platform: String = "android",
    val identityKey: String = "",
    val signingKey: String? = null,
)

@Serializable
data class DeviceRegistration(
    val id: String,
    val name: String,
    val platform: String = "android",
)

@Serializable
data class AuthResponse(
    val user: UserDto,
    val device: DeviceDto? = null,
    val accessToken: String,
    val refreshToken: String,
    val accessExpiresIn: Int = 900,
    val refreshExpiresAt: String? = null,
    /** Present once, at registration and when rotating the code. */
    val recoveryCode: String? = null,
)

@Serializable
data class MeResponse(
    val user: UserDto,
    val settings: Map<String, kotlinx.serialization.json.JsonElement> = emptyMap(),
    val devices: List<DeviceDto> = emptyList(),
)

@Serializable
data class RegisterRequest(
    val username: String,
    val displayName: String,
    val password: String,
    val device: DeviceRegistration,
)

@Serializable
data class LoginRequest(
    val username: String,
    val password: String,
    val device: DeviceRegistration,
)

@Serializable
data class RefreshRequest(val refreshToken: String)

@Serializable
data class RecoverRequest(val username: String, val recoveryCode: String, val newPassword: String)

@Serializable
data class ChangePasswordRequest(val currentPassword: String, val newPassword: String)

@Serializable
data class DeleteAccountRequest(val password: String)

@Serializable
data class RecoveryCodeResponse(val recoveryCode: String)

@Serializable
data class PatchMeRequest(
    val displayName: String? = null,
    val about: String? = null,
    val shade: Int? = null,
    val settings: Map<String, kotlinx.serialization.json.JsonElement>? = null,
)

// ------------------------------------------------------------------ friends --

@Serializable
data class FriendDto(
    val id: String,
    val status: String,
    val createdAt: String,
    val respondedAt: String? = null,
    val user: UserDto,
    val online: Boolean = false,
)

@Serializable
data class FriendGraphDto(
    val friends: List<FriendDto> = emptyList(),
    val incoming: List<FriendDto> = emptyList(),
    val outgoing: List<FriendDto> = emptyList(),
)

@Serializable
data class FriendRequest(val username: String)

@Serializable
data class FriendRequestResponse(val requestId: String, val accepted: Boolean = false)

@Serializable
data class AcceptResponse(val ok: Boolean = false, val conversationId: String? = null)

@Serializable
data class UserLookupDto(
    val user: UserDto,
    /** self | friends | pending_outgoing | pending_incoming | none */
    val relationship: String = "none",
    val devices: List<DeviceDto> = emptyList(),
)

@Serializable
data class DirectoryPersonDto(
    val user: UserDto,
    /** friends | pending_outgoing | pending_incoming | none */
    val relationship: String = "none",
    val requestId: String? = null,
)

@Serializable
data class PeopleEnvelope(val people: List<DirectoryPersonDto> = emptyList())

// ------------------------------------------------------------ conversations --

@Serializable
data class ConversationDto(
    val id: String,
    val kind: String = "direct",
    val title: String? = null,
    val createdBy: String? = null,
    val createdAt: String,
    val lastReadSeq: Long = 0,
    val unread: Int = 0,
    val muted: Boolean = false,
    val members: List<UserDto> = emptyList(),
    val lastMessage: MessageDto? = null,
) {
    /** The other person in a direct chat, or null for a group. */
    fun peer(selfId: String): UserDto? =
        if (kind == "direct") members.firstOrNull { it.id != selfId } else null

    fun displayTitle(selfId: String): String =
        title ?: peer(selfId)?.displayName ?: members.joinToString(", ") { it.displayName }
}

@Serializable
data class MessageBodyDto(
    /** Plain text as stored in the cloud. Older ciphertext bodies have no text. */
    val text: String? = null,
)

@Serializable
data class MessageDto(
    val id: String,
    val conversationId: String,
    val senderId: String,
    val senderDeviceId: String? = null,
    val seq: Long = 0,
    val kind: String = "text",
    val body: MessageBodyDto = MessageBodyDto(),
    val createdAt: String,
    val editedAt: String? = null,
)

@Serializable
data class MessagesPageDto(
    val messages: List<MessageDto> = emptyList(),
    val hasMore: Boolean = false,
)

@Serializable
data class SendMessageRequest(
    val id: String,
    val kind: String = "text",
    val body: MessageBodyDto,
    val clientId: String? = null,
)

@Serializable
data class SendMessageResponse(val message: MessageDto, val duplicate: Boolean = false)

@Serializable
data class OpenConversationRequest(val peerId: String)

@Serializable
data class ReadRequest(val seq: Long)

@Serializable
data class MuteRequest(val muted: Boolean)

@Serializable
data class RegisterDeviceRequest(val device: DeviceRegistration)

@Serializable
data class PushTokenRequest(val token: String?)

@Serializable
data class ApiErrorDto(val error: String? = null, val message: String? = null)

// ------------------------------------------------------- websocket frames ---

@Serializable
sealed class ServerFrame {

    @Serializable
    @SerialName("ready")
    data class Ready(
        val user: UserDto,
        val conversations: List<ConversationDto> = emptyList(),
        val serverTime: String? = null,
    ) : ServerFrame()

    @Serializable
    @SerialName("msg")
    data class IncomingMessage(
        val conversationId: String,
        val message: MessageDto,
    ) : ServerFrame()

    @Serializable
    @SerialName("ack")
    data class Ack(
        val clientId: String? = null,
        val id: String,
        val conversationId: String,
        val seq: Long,
        val createdAt: String,
        val duplicate: Boolean = false,
    ) : ServerFrame()

    @Serializable
    @SerialName("typing")
    data class Typing(
        val conversationId: String,
        val userId: String,
        val state: String,
    ) : ServerFrame()

    @Serializable
    @SerialName("read")
    data class Read(
        val conversationId: String,
        val userId: String,
        val seq: Long,
    ) : ServerFrame()

    @Serializable
    @SerialName("presence")
    data class Presence(
        val userId: String,
        val online: Boolean,
        val lastSeen: String? = null,
    ) : ServerFrame()

    @Serializable
    @SerialName("friend")
    data class Friend(
        val kind: String,
        val user: UserDto? = null,
        val requestId: String? = null,
    ) : ServerFrame()

    @Serializable
    @SerialName("conversation")
    data class ConversationChanged(
        val conversation: ConversationDto,
    ) : ServerFrame()

    /** The relay dropped a chat (a connection was removed on either side). */
    @Serializable
    @SerialName("conversation_removed")
    data class ConversationRemoved(
        val conversationId: String,
    ) : ServerFrame()

    @Serializable
    @SerialName("err")
    data class Error(
        val code: String,
        val message: String,
        val clientId: String? = null,
    ) : ServerFrame()

    @Serializable
    @SerialName("pong")
    data class Pong(val time: Long = 0) : ServerFrame()
}
