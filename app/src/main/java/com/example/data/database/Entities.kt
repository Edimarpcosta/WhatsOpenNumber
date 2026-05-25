package com.example.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "contact_history")
data class ContactHistory(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val phoneNumber: String,
    val contactName: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val actionType: String // "WHATSAPP" or "CONTACT"
)

@Entity(tableName = "message_template")
data class MessageTemplate(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val content: String
)
