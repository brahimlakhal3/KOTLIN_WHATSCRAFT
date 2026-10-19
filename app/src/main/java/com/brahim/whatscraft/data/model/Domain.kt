package com.brahim.whatscraft.data.model

/** UI-facing models. The rest of the app never touches the wire or the database directly. */

enum class MessageStatus { Sending, Queued, Sent, Read, Failed, Undecryptable }

data class ChatMessage(
    val id: String,
    val conversationId: String,
    val text: String,
    val mine: Boolean,
    val senderId: String,
    val status: MessageStatus,
    /** Epoch millis. */
    val createdAt: Long,
    val seq: Long,
    /** `text` or `reaction`; reaction rows never reach the thread. */
    val kind: String = "text",
    val reactions: List<Reaction> = emptyList(),
)

/** One emoji on a message, already collapsed per person. */
data class Reaction(
    val emoji: String,
    val count: Int,
    val mine: Boolean,
)

data class ChatSummary(
    val id: String,
    val title: String,
    val subtitle: String?,
    val shade: Int,
    val unread: Int,
    val muted: Boolean,
    val online: Boolean,
    val isGroup: Boolean,
    val peerId: String?,
    val lastMessageAt: Long,
    val lastMessageMine: Boolean,
    val typing: Boolean,
)

enum class Relationship { Friends, PendingIncoming, PendingOutgoing, None, Self }

data class Contact(
    val id: String,
    val username: String,
    val displayName: String,
    val about: String,
    val shade: Int,
    val online: Boolean,
    val lastSeen: Long?,
    val relationship: Relationship,
    val requestId: String?,
    /** Sixty-digit fingerprint of this contact's identity key. */
    val fingerprint: String?,
    /** True when the contact's key material changed since we first saw it. */
    val keyChanged: Boolean,
)
