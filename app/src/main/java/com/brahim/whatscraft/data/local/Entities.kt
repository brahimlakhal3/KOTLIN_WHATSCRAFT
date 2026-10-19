package com.brahim.whatscraft.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Local cache. Everything here can be rebuilt from the relay: the tables only
 * hold metadata plus *sealed* text (see [com.brahim.whatscraft.crypto.SecretVault]),
 * so a stolen database file reveals no message content.
 */

@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey val id: String,
    val kind: String,
    val title: String?,
    val createdAt: String,
    val lastReadSeq: Long,
    val muted: Boolean,
    val unread: Int,
    val membersJson: String,
    val peerId: String?,
    val peerUsername: String?,
    val peerDisplayName: String?,
    val peerShade: Int,
    val peerOnline: Boolean,
    val lastMessageId: String?,
    val lastMessageSenderId: String?,
    val lastMessageSealed: String?,
    val lastMessageAt: String?,
    val lastMessageSeq: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "messages",
    indices = [Index(value = ["conversationId", "seq"]), Index(value = ["status"])],
)
data class MessageEntity(
    @PrimaryKey val id: String,
    val conversationId: String,
    val senderId: String,
    val senderDeviceId: String?,
    val seq: Long,
    val kind: String,
    /** The ciphertext exactly as it travelled (kept for retries and audits). */
    val bodyJson: String?,
    /** The plaintext, sealed with the Keystore key. */
    val sealedText: String?,
    val createdAt: String,
    val status: String,
    val mine: Boolean,
)

@Entity(tableName = "contacts")
data class ContactEntity(
    @PrimaryKey val id: String,
    val username: String,
    val displayName: String,
    val about: String,
    val shade: Int,
    val lastSeen: String?,
    val online: Boolean,
    /** friends | pending_incoming | pending_outgoing | none */
    val relationship: String,
    val requestId: String?,
    /** Cached JSON of this contact's device public keys. */
    val deviceKeysJson: String?,
    /** Sixty-digit fingerprint of this contact's identity key (trust on first use). */
    val fingerprint: String?,
    /** Set when the fingerprint changed - the app then shows a loud warning. */
    val keyChanged: Boolean,
    val updatedAt: Long,
)
