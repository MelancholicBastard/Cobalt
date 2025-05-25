package com.melancholicbastard.cobalt.db

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update


@Dao
interface VoiceNoteDAO {

    // Создание новой записи
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(note: VoiceNote)

    // Обновление существующей записи
    @Update
    suspend fun update(note: VoiceNote)

    // Удаление записи по ID
    @Query("DELETE FROM voice_notes WHERE id = :noteId")
    suspend fun deleteById(noteId: Long)

    @Query("SELECT * FROM voice_notes WHERE id = :id")
    fun getById(id: Long): LiveData<VoiceNote?>

    // Получить все записи (опционально)
    @Query("SELECT * FROM voice_notes ORDER BY dateCreated DESC")   // Сортировка ORDER BY по новым
    fun getAll(): LiveData<List<VoiceNote>>                         // LiveData, чтобы автоматически обновлять UI

    // Получить записи за определённую дату (в диапазоне)
    @Query("SELECT * FROM voice_notes WHERE dateCreated >= :dateStart AND dateCreated < :dateEnd ORDER BY dateCreated DESC")
    fun getByDate(dateStart: Long, dateEnd: Long): LiveData<List<VoiceNote>>

    // Поиск по заголовку или транскрипции с поддержкой подстроки
    @Query("SELECT * FROM voice_notes WHERE title LIKE :query OR transcript LIKE :query ORDER BY dateCreated DESC")
    fun searchNotes(query: String): LiveData<List<VoiceNote>>

    @Query("SELECT * FROM voice_notes WHERE id IN (:ids)")
    fun getByIds(ids: List<Long>): LiveData<List<VoiceNote>>

    @Delete
    suspend fun deleteNotes(notes: List<VoiceNote>)
}