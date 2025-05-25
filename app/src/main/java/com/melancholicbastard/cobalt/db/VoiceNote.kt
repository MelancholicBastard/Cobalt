package com.melancholicbastard.cobalt.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "voice_notes")
data class VoiceNote(
    @PrimaryKey val id: Long = System.currentTimeMillis(),  // Используем текущее время как ID
    val title: String,
    val dateCreated: Long,                                  // тоже текущее время
    val audioPath: String?,                                 // nullable, чтобы можно было удалить файл
    val transcript: String
)