package com.example.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(entities = [ContactHistory::class, MessageTemplate::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun contactHistoryDao(): ContactHistoryDao
    abstract fun messageTemplateDao(): MessageTemplateDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context, scope: CoroutineScope): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "conversa_direta_database"
                )
                .addCallback(AppDatabaseCallback(scope))
                .build()
                INSTANCE = instance
                instance
            }
        }
    }

    private class AppDatabaseCallback(
        private val scope: CoroutineScope
    ) : RoomDatabase.Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            super.onCreate(db)
            INSTANCE?.let { database ->
                scope.launch(Dispatchers.IO) {
                    val templateDao = database.messageTemplateDao()
                    // Pre-populate useful defaults in Portuguese
                    templateDao.insertTemplate(
                        MessageTemplate(
                            title = "Saudação Geral",
                            content = "Olá! Tudo bem? Gostaria de iniciar nossa conversa por aqui."
                        )
                    )
                    templateDao.insertTemplate(
                        MessageTemplate(
                            title = "Contato Comercial",
                            content = "Olá, gostaria de obter mais informações sobre os produtos/serviços disponiveis."
                        )
                    )
                    templateDao.insertTemplate(
                        MessageTemplate(
                            title = "Agradecimento",
                            content = "Muito obrigado pelo atendimento! Fico no aguardo do retorno."
                        )
                    )
                }
            }
        }
    }
}
