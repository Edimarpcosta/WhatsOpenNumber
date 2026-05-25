package com.example.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ContactHistoryDao {
    @Query("SELECT * FROM contact_history ORDER BY timestamp DESC")
    fun getAllHistory(): Flow<List<ContactHistory>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(item: ContactHistory)

    @Query("DELETE FROM contact_history WHERE id = :id")
    suspend fun deleteHistoryById(id: Int)

    @Query("DELETE FROM contact_history")
    suspend fun clearAllHistory()
}

@Dao
interface MessageTemplateDao {
    @Query("SELECT * FROM message_template")
    fun getAllTemplates(): Flow<List<MessageTemplate>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTemplate(template: MessageTemplate)

    @Query("DELETE FROM message_template WHERE id = :id")
    suspend fun deleteTemplateById(id: Int)
}
