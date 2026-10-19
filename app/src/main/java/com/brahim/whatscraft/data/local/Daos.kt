package com.brahim.whatscraft.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ContactDao {

    @Query("SELECT * FROM contacts ORDER BY displayName COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<ContactEntity>>

    @Query("SELECT * FROM contacts WHERE id = :id")
    suspend fun byId(id: String): ContactEntity?

    @Query("SELECT * FROM contacts ORDER BY displayName COLLATE NOCASE ASC")
    suspend fun all(): List<ContactEntity>

    @Upsert
    suspend fun upsert(contacts: List<ContactEntity>)

    @Upsert
    suspend fun upsert(contact: ContactEntity)

    @Query("DELETE FROM contacts WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM contacts")
    suspend fun clear()
}
