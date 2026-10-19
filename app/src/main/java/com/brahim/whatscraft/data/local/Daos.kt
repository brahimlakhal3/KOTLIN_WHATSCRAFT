package com.brahim.whatscraft.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ConversationDao {

    @Query(
        """SELECT * FROM conversations
           ORDER BY CASE WHEN lastMessageAt IS NULL THEN 1 ELSE 0 END, lastMessageAt DESC""",
    )
    fun observeAll(): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversations WHERE id = :id")
    suspend fun byId(id: String): ConversationEntity?

    @Query("SELECT * FROM conversations WHERE peerId = :peerId")
    suspend fun byPeer(peerId: String): ConversationEntity?

    @Query("DELETE FROM conversations WHERE id = :id")
    suspend fun delete(id: String)

    @Query("SELECT * FROM conversations")
    suspend fun all(): List<ConversationEntity>

    @Upsert
    suspend fun upsert(conversations: List<ConversationEntity>)

    @Upsert
    suspend fun upsert(conversation: ConversationEntity)

    @Query("UPDATE conversations SET unread = :unread WHERE id = :id")
    suspend fun setUnread(id: String, unread: Int)

    @Query("UPDATE conversations SET unread = 0, lastReadSeq = :seq WHERE id = :id")
    suspend fun markRead(id: String, seq: Long)

    @Query(
        """UPDATE conversations
           SET lastMessageId = NULL, lastMessageSenderId = NULL, lastMessageSealed = NULL,
               lastMessageAt = NULL, lastMessageSeq = 0, unread = 0
           WHERE id = :id""",
    )
    suspend fun clearLastMessage(id: String)

    @Query(
        """UPDATE conversations
           SET lastMessageId = NULL, lastMessageSenderId = NULL, lastMessageSealed = NULL,
               lastMessageAt = NULL, lastMessageSeq = 0, unread = 0""",
    )
    suspend fun clearAllLastMessages()

    @Query("UPDATE conversations SET muted = :muted WHERE id = :id")
    suspend fun setMuted(id: String, muted: Boolean)

    @Query("UPDATE conversations SET peerOnline = :online WHERE peerId = :peerId")
    suspend fun setPeerOnline(peerId: String, online: Boolean)

    @Query(
        """UPDATE conversations
           SET lastMessageId = :messageId, lastMessageSenderId = :senderId,
               lastMessageSealed = :sealed, lastMessageAt = :at, lastMessageSeq = :seq
           WHERE id = :id""",
    )
    suspend fun setLastMessage(
        id: String,
        messageId: String,
        senderId: String,
        sealed: String?,
        at: String,
        seq: Long,
    )

    @Query("DELETE FROM conversations")
    suspend fun clear()
}

@Dao
interface MessageDao {

    /**
     * Oldest first. Rows still waiting for the relay have `seq = 0`, so they are
     * pushed to the end: a pending message always sits below acknowledged ones.
     */
    @Query(
        """SELECT * FROM messages WHERE conversationId = :conversationId
           ORDER BY CASE WHEN seq = 0 THEN 1 ELSE 0 END, seq ASC, createdAt ASC""",
    )
    fun observe(conversationId: String): Flow<List<MessageEntity>>

    @Query(
        """SELECT * FROM messages WHERE conversationId = :conversationId
           ORDER BY CASE WHEN seq = 0 THEN 1 ELSE 0 END, seq ASC, createdAt ASC""",
    )
    suspend fun all(conversationId: String): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE id = :id")
    suspend fun byId(id: String): MessageEntity?

    @Upsert
    suspend fun upsert(messages: List<MessageEntity>)

    @Upsert
    suspend fun upsert(message: MessageEntity)

    @Query("SELECT MIN(seq) FROM messages WHERE conversationId = :conversationId AND seq > 0")
    suspend fun oldestSeq(conversationId: String): Long?

    @Query("SELECT MAX(seq) FROM messages WHERE conversationId = :conversationId")
    suspend fun newestSeq(conversationId: String): Long?

    /** Newest acknowledged (seq > 0) message, used to seed an empty chat view. */
    @Query(
        """SELECT * FROM messages WHERE conversationId = :conversationId AND seq > 0
           ORDER BY seq DESC LIMIT :limit""",
    )
    suspend fun newest(conversationId: String, limit: Int): List<MessageEntity>

    @Query("SELECT COUNT(*) FROM messages WHERE conversationId = :conversationId")
    suspend fun count(conversationId: String): Int

    @Query("UPDATE messages SET status = :status WHERE id = :id")
    suspend fun setStatus(id: String, status: String)

    @Query("UPDATE messages SET seq = :seq, status = :status, createdAt = :createdAt WHERE id = :id")
    suspend fun confirm(id: String, seq: Long, status: String, createdAt: String)

    /** Everything we sent that the other side has now read. */
    @Query(
        """UPDATE messages SET status = 'read'
           WHERE conversationId = :conversationId AND mine = 1 AND status != 'read'
             AND seq > 0 AND seq <= :seq""",
    )
    suspend fun markReadUpTo(conversationId: String, seq: Long)

    @Query("SELECT * FROM messages WHERE status IN ('sending', 'failed') ORDER BY createdAt ASC")
    suspend fun unsent(): List<MessageEntity>

    @Query("DELETE FROM messages WHERE conversationId = :conversationId")
    suspend fun clearConversation(conversationId: String)

    @Query("DELETE FROM messages")
    suspend fun clear()
}

@Dao
interface ContactDao {

    @Query("SELECT * FROM contacts ORDER BY displayName COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<ContactEntity>>

    @Query("SELECT * FROM contacts WHERE id = :id")
    suspend fun byId(id: String): ContactEntity?

    @Query("SELECT * FROM contacts WHERE username = :username COLLATE NOCASE")
    suspend fun byUsername(username: String): ContactEntity?

    @Query("SELECT * FROM contacts ORDER BY displayName COLLATE NOCASE ASC")
    suspend fun all(): List<ContactEntity>

    @Upsert
    suspend fun upsert(contacts: List<ContactEntity>)

    @Upsert
    suspend fun upsert(contact: ContactEntity)

    @Query("UPDATE contacts SET online = :online, lastSeen = :lastSeen WHERE id = :id")
    suspend fun setPresence(id: String, online: Boolean, lastSeen: String?)

    @Query("UPDATE contacts SET relationship = :relationship, requestId = :requestId WHERE id = :id")
    suspend fun setRelationship(id: String, relationship: String, requestId: String?)

    @Query("UPDATE contacts SET deviceKeysJson = :json WHERE id = :id")
    suspend fun setDeviceKeys(id: String, json: String?)

    @Query("DELETE FROM contacts WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM contacts")
    suspend fun clear()
}
