package com.brahim.whatscraft.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [ConversationEntity::class, MessageEntity::class, ContactEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun conversations(): ConversationDao
    abstract fun messages(): MessageDao
    abstract fun contacts(): ContactDao

    companion object {
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "whatscraft.db")
                // The cache can always be rebuilt from the relay; a schema change
                // simply drops it rather than migrating.
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
    }
}
