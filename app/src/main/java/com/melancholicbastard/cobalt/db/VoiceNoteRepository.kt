package com.melancholicbastard.cobalt.db

import android.icu.util.Calendar
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.asFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File

class VoiceNoteRepository(
    private val dao: VoiceNoteDAO
) {
    // Получить все записи (опционально)
    val allNotes: LiveData<List<VoiceNote>> = dao.getAll()

    // Получить записи за дату
    fun getNotesForDate(date: Long): LiveData<List<VoiceNote>> {
        val startOfDay = getStartOfDay(date)
        val endOfDay = startOfDay + 24 * 60 * 60 * 1000L
        Log.d("VoiceNoteRepository", "Запрос записей с $startOfDay по $endOfDay")
        return dao.getByDate(startOfDay, endOfDay)
    }

    // Получить записи по индексу
    suspend fun getNoteById(id: Long): Flow<VoiceNote?> {
        return withContext(Dispatchers.IO) {
            val result = dao.getById(id).asFlow()
            Log.d("VoiceNoteRepository", "getById($id) вернул: $result")
            result
        }
    }

    // Поиск по подстроке
    fun searchNotes(query: String): LiveData<List<VoiceNote>> {
        return dao.searchNotes("%$query%")
    }

    // Сохранить новую запись
    suspend fun insert(note: VoiceNote) {
        withContext(Dispatchers.IO) {       // чтобы не захламлять основной поток
            dao.insert(note)
            Log.d("VoiceNoteRepository", "Запись добавлена в БД: ${note.title}")
        }
    }

    // Обновить существующую запись
    suspend fun update(note: VoiceNote) {
        withContext(Dispatchers.IO) {
            dao.update(note)
        }
    }

    // Удалить запись + файл
    suspend fun deleteNoteById(id: Long) {
        withContext(Dispatchers.IO) {
            val note = dao.getById(id).value
            note?.audioPath?.let { path ->
                val file = File(path)
                if (file.exists()) {
                    file.delete()
                }
            }
            dao.deleteById(id)
        }
    }

    suspend fun deleteNotesByIds(ids: List<Long>) {
        withContext(Dispatchers.IO) {
            val notesToDelete = dao.getByIds(ids).asFlow().first()
            notesToDelete.forEach { note ->
                File(note.audioPath!!).takeIf { it.exists() }?.delete()
            }
            dao.deleteNotes(notesToDelete)
        }
    }

    private fun getStartOfDay(timestamp: Long): Long {
        val calendar = Calendar.getInstance().apply {
            timeInMillis = timestamp
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return calendar.timeInMillis
    }
}