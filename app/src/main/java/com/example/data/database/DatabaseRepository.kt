package com.example.data.database

import kotlinx.coroutines.flow.Flow

class DatabaseRepository(
    private val contactHistoryDao: ContactHistoryDao,
    private val messageTemplateDao: MessageTemplateDao
) {
    val allHistory: Flow<List<ContactHistory>> = contactHistoryDao.getAllHistory()
    val allTemplates: Flow<List<MessageTemplate>> = messageTemplateDao.getAllTemplates()

    suspend fun insertHistory(item: ContactHistory) {
        contactHistoryDao.insertHistory(item)
    }

    suspend fun deleteHistoryById(id: Int) {
        contactHistoryDao.deleteHistoryById(id)
    }

    suspend fun clearAllHistory() {
        contactHistoryDao.clearAllHistory()
    }

    suspend fun insertTemplate(template: MessageTemplate) {
        messageTemplateDao.insertTemplate(template)
    }

    suspend fun deleteTemplateById(id: Int) {
        messageTemplateDao.deleteTemplateById(id)
    }
}
