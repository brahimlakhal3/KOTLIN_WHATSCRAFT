package com.brahim.whatscraft.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * The only thing cached on the phone. Messages and conversations live in the
 * cloud and are fetched again whenever a chat is opened: nothing readable or
 * unreadable about a message is written to disk by this app.
 */
@Entity(tableName = "contacts")
data class ContactEntity(
    @PrimaryKey val id: String,
    val username: String,
    val displayName: String,
    val about: String,
    val shade: Int,
    /** friends | pending_incoming | pending_outgoing | none */
    val relationship: String,
    val requestId: String?,
    val updatedAt: Long,
)
