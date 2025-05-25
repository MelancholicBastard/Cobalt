package com.melancholicbastard.cobalt.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase


@Database(entities = [VoiceNote::class], version = 1, exportSchema = false)
abstract class VoiceNoteDB : RoomDatabase() {
    abstract fun voiceNoteDAO(): VoiceNoteDAO

    companion object {
        private const val DATABASE_NAME = "voice_note_database"

        @Volatile
        private var DB: VoiceNoteDB? = null

        fun getDB(context: Context): VoiceNoteDB {
            return DB ?: synchronized(this) {       // synchronized гарантия выполнения только для одного потока
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    VoiceNoteDB::class.java,
                    DATABASE_NAME
                ).build()
                DB = instance
                instance
            }
        }
    }
}