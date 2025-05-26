package com.melancholicbastard.cobalt.data

import android.app.Application
import android.icu.util.Calendar
import android.media.MediaPlayer
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.asFlow
import androidx.lifecycle.viewModelScope
import com.melancholicbastard.cobalt.db.VoiceNote
import com.melancholicbastard.cobalt.db.VoiceNoteDB
import com.melancholicbastard.cobalt.db.VoiceNoteRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class HistoryViewModelFactory(
    private val application: Application
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(HistoryViewModel::class.java))
            return HistoryViewModel(application) as T
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

class HistoryViewModel(application: Application) : BasePlaybackViewModel(application) {

    // Репозиторий
    private val repository: VoiceNoteRepository = VoiceNoteRepository(
        VoiceNoteDB.getDB(application).voiceNoteDAO()
    )

    // Состояние экрана
    sealed class HistoryScreenState {
        data object Search : HistoryScreenState()
        data class Edit(val noteId: Long) : HistoryScreenState()
        data object DeleteConfirm : HistoryScreenState()
    }

    private val _screenState = MutableStateFlow<HistoryScreenState>(HistoryScreenState.Search)
    val screenState: StateFlow<HistoryScreenState> = _screenState.asStateFlow()

    fun enterEditMode(noteId: Long) {
        _screenState.value = HistoryScreenState.Edit(noteId)
    }

    fun exitEditMode() {
        Log.d("HistoryViewModel", "exitEditMode()")
        _screenState.value = HistoryScreenState.Search
        resetEditState()
        stopPlayback()
    }

    // Календарь и фильтрация по дате
    private val _selectedDate = MutableStateFlow(System.currentTimeMillis())
    val selectedDate = _selectedDate.asStateFlow()

    fun updateSelectedDate(date: Long) {
        _selectedDate.value = date
    }

    // Поиск по подстроке
    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    // Результаты поиска/фильтрации
    private var _notes = MutableStateFlow<List<VoiceNote>>(emptyList())
    val notes = _notes.asStateFlow()

    // Редактирование заметки
    private val _currentNote = MutableStateFlow<VoiceNote?>(null)
    val currentNote = _currentNote.asStateFlow()

    private val _editingTitle = MutableStateFlow("")
    val editingTitle = _editingTitle.asStateFlow()

    private val _editingTranscript = MutableStateFlow("")
    val editingTranscript = _editingTranscript.asStateFlow()

    // Состояние множественного выбора
    private val _selectedNoteIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedNoteIds: StateFlow<Set<Long>> = _selectedNoteIds.asStateFlow()

    fun toggleSelection(noteId: Long) {
        val current = _selectedNoteIds.value.toMutableSet().apply {
            if (noteId in this) remove(noteId) else add(noteId)
        }
        _selectedNoteIds.value = current
    }

    private fun clearSelection() {
        _selectedNoteIds.value = emptySet()
    }

    fun deleteSelectedNotes() {
        val ids = _selectedNoteIds.value.toList()
        if (ids.isEmpty()) return
        Log.d("HistoryViewModel", "Попытка удаления записей с ID: $ids")
        viewModelScope.launch {
            repository.deleteNotesByIds(ids)
            clearSelection()
        }
    }

    init {
        Log.d("HistoryViewModel", "Инициализация: загрузка записей за сегодня")
        loadNotesForDate(System.currentTimeMillis())
    }

    // Загрузка записей по дате
    fun loadNotesForDate(date: Long) {
        Log.d("HistoryViewModel", "loadNotesForDate($date)")
        viewModelScope.launch {
            repository.getNotesForDate(date)
                .asFlow()
                .collect { list ->
                Log.d("HistoryViewModel", "Получен список: ${list.size} записей")
                _notes.emit(list)
            }
        }
    }
    // Поиск записей по подстроке
    fun searchNotes(query: String) {
        viewModelScope.launch {
            repository.searchNotes(query)
                .asFlow()
                .collect { list ->
                    Log.d("HistoryViewModel", "Результат поиска: ${list.size} записей")
                    _notes.emit(list)
                }
        }
    }

    // Обновление заголовка
    fun updateNoteTitle(title: String) {
        _editingTitle.value = title
    }

    // Обновление текста транскрипции
    fun updateTranscribedText(text: String) {
        _editingTranscript.value = text
    }

    fun enterDeleteConfirmMode() {
        _screenState.value = HistoryScreenState.DeleteConfirm
    }

    fun exitSelectionMode() {
        clearSelection()
        _screenState.value = HistoryScreenState.Search
    }

    // Сохранить текущую запись
    fun saveCurrentNote() {
        Log.d("HistoryViewModel", "Попытка сохранения записи")
        viewModelScope.launch {
            val currentNote = _currentNote.value ?: run {
                Log.e("HistoryViewModel", "saveCurrentNote: currentNote равен null")
                return@launch
            }
            val updatedNote = currentNote.copy(
                title = _editingTitle.value,
                transcript = _editingTranscript.value
            )
            Log.d("HistoryViewModel", "Сохранение изменённой записи: $updatedNote")
            withContext(Dispatchers.IO) {
                repository.update(updatedNote)
            }
            exitEditMode()
        }
    }

    // Загрузить запись по ID (вызывается при входе в режим редактирования)
    fun loadNoteById(noteId: Long) {
        Log.d("HistoryViewModel", "Загрузка записи с ID: $noteId")
        viewModelScope.launch(Dispatchers.IO) {
            repository.getNoteById(noteId)
                .collect { note ->
                    Log.d("HistoryViewModel", "Полученная запись: $note")
                    if (note != null) {
                        _currentNote.emit(note)
                        _editingTitle.emit(note.title)
                        _editingTranscript.emit(note.transcript)

                        val file = File(note.audioPath!!)
                        if (file.exists()) {
                            val duration = getAudioDurationFrom(file) // Получаем длительность до запуска
                            _playbackDuration.emit(duration)
                        }
                    } else {
                        Log.e("HistoryViewModel", "Запись с ID $noteId не найдена")
                    }
                }
        }
    }

    // Воспроизведение аудио
    override fun playRecording() {
        val noteId = _currentNote.value?.id ?: return
        Log.d("HistoryViewModel", "Попытка воспроизведения для ID: $noteId")
        viewModelScope.launch(Dispatchers.IO) {
            repository.getNoteById(noteId).collect { note ->
                note?.let {
                    val file = File(it.audioPath!!)
                    if (file.exists()) {
                        setDataSource(file)
                    } else {
                        Log.e("HistoryViewModel", "Файл не найден: ${file.path}")
                    }
                }
            }
        }
    }

    fun resetEditState() {
        _editingTitle.value = _currentNote.value?.title ?: ""
        _editingTranscript.value = _currentNote.value?.transcript ?: ""
    }

    // Удаление записи
    fun deleteNoteById(id: Long) {
        Log.d("HistoryViewModel", "deleteNoteById($id)")
        viewModelScope.launch {
            repository.deleteNoteById(id)
        }
    }
}