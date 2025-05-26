package com.melancholicbastard.cobalt.ui.theme.screens

import android.app.Activity
import android.icu.text.SimpleDateFormat
import android.util.Log
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.*
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.material.datepicker.MaterialDatePicker

import com.melancholicbastard.cobalt.data.HistoryViewModel
import com.melancholicbastard.cobalt.db.VoiceNote
import java.util.Calendar
import java.util.Date
import androidx.compose.material3.DatePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import java.util.Locale


@Composable
fun SearchModeScreen(viewModel: HistoryViewModel) {
    val notes by viewModel.notes.collectAsState()
    val mode by viewModel.screenState.collectAsState()
    val selectedIds by viewModel.selectedNoteIds.collectAsState()

    LaunchedEffect(selectedIds.size) {
        Log.d("SearchModeScreen", "Состояние экрана изменено: $mode")
        if (selectedIds.isEmpty()) {
            viewModel.exitSelectionMode()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Шапка с кнопками
        when (mode) {
            is HistoryViewModel.HistoryScreenState.Search -> {
                // Стандартный интерфейс поиска и даты
                val selectedDate by viewModel.selectedDate.collectAsState()
                val searchQuery by viewModel.searchQuery.collectAsState()

                Column(modifier = Modifier.padding(16.dp)) {
                    // Поисковое поле
                    TextField(
                        value = searchQuery,
                        onValueChange = { query ->
                            viewModel.updateSearchQuery(query)
                            if (query.isNotBlank()) {
                                viewModel.searchNotes(query)
                            } else {
                                viewModel.loadNotesForDate(selectedDate)
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Поиск") }
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Строка выбора даты
                    DateSelectionRow(
                        selectedDate = selectedDate,
                        onDateSelected = { date ->
                            viewModel.updateSelectedDate(date)
                            viewModel.loadNotesForDate(date)
                        }
                    )
                }
            }

            HistoryViewModel.HistoryScreenState.DeleteConfirm -> {
                // Шапка с подтверждением
                SelectionTopBar(
                    count = selectedIds.size,
                    onConfirm = {
                        viewModel.deleteSelectedNotes()
                    },
                    onCancel = {
                        viewModel.exitSelectionMode()
                    }
                )
            }

            is HistoryViewModel.HistoryScreenState.Edit -> {
                // Режим редактирования — не относится к удалению
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Список записей
        LazyColumn(
            modifier = Modifier
                .fillMaxHeight()
                .weight(1f)
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 8.dp),
            contentPadding = PaddingValues(horizontal = 8.dp)
        ) {
            items(notes.size) { index ->
                val note = notes[index]
                val isSelected = selectedIds.contains(note.id)

                NoteCard(
                    note = note,
                    isSelected = isSelected,
                    onClick = {
                        if (mode is HistoryViewModel.HistoryScreenState.DeleteConfirm) {
                            viewModel.toggleSelection(note.id)
                        } else {
                            viewModel.enterEditMode(note.id)
                        }
                    },
                    onLongClick = {
                        viewModel.toggleSelection(note.id)
                        viewModel.enterDeleteConfirmMode()
                    },
                    onDelete = {
                        viewModel.deleteNoteById(note.id)
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateSelectionRow(
    selectedDate: Long,
    onDateSelected: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    // Состояние DatePicker (устанавливаем начальную дату, если она передана)
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = selectedDate
    )
    val dayOfWeek = remember(selectedDate) {
        formatDayOfWeek(selectedDate)
    }
    val formattedDate = remember(selectedDate) {
        formatDate(selectedDate)
    }

    var showDatePicker by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(8.dp)
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
            .clickable { showDatePicker = true },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "$dayOfWeek, $formattedDate",
            modifier = Modifier
                .weight(1f)
                .padding(start = 16.dp)
        )

        IconButton(
            onClick = { showDatePicker = true },
            modifier = Modifier.padding(end = 16.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Star,
                contentDescription = "Выбрать дату"
            )
        }
    }

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { date ->
                            onDateSelected(date)
                        }
                        showDatePicker = false
                    }
                ) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Отмена")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectionTopBar(
    count: Int,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    var deleteDialog by remember { mutableStateOf(false) }
    TopAppBar(
        title = { Text("$count выбрано") },
        navigationIcon = {
            IconButton(onClick = onCancel) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Отменить")
            }
        },
        actions = {
            IconButton(onClick = {
                Log.d("EditModeScreen", "Кнопка 'Удалить' нажата")
                deleteDialog = true
            }) {
                Icon(Icons.Default.Delete, contentDescription = "Удалить выбранные")
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            navigationIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            actionIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer
        ),
        modifier = modifier
            .padding(top = 0.dp)
    )
    if (deleteDialog) {
        DeleteConfirmationDialog(
            onConfirm = {
                Log.d("EditModeScreen", "Кнопка 'Удалить' нажата")
                onConfirm()
                deleteDialog = false
            },
            onCancel = {
                deleteDialog = false
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NoteCard(
    note: VoiceNote,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onDelete: () -> Unit
) {
    var deleteDialog by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        border = BorderStroke(3.dp, MaterialTheme.colorScheme.outline)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = formatTime(note.dateCreated),
                    style = MaterialTheme.typography.labelMedium
                )
                Text(
                    text = note.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = note.transcript,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Выбрано",
                    tint = MaterialTheme.colorScheme.primary
                )
            } else {
                IconButton(onClick = { deleteDialog = true }) {
                    Icon(Icons.Default.Delete, contentDescription = "Удалить")
                }
            }
        }
    }
    if (deleteDialog) {
        DeleteConfirmationDialog(
            onConfirm = {
                onDelete()
                deleteDialog = false
            },
            onCancel = {
                deleteDialog = false
            }
        )
    }
}

@Composable
fun DeleteConfirmationDialog(
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Хотите удалить?") },
        text = { Text("Запись будет удалена") },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Удалить")
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) {
                Text("Отмена")
            }
        }
    )
}

private fun formatDayOfWeek(timestamp: Long): String {
    val sdf = SimpleDateFormat("EEE", Locale.getDefault())
    return sdf.format(Date(timestamp)).replaceFirstChar { it.titlecase(Locale.getDefault()) }
}

private fun formatDate(timestamp: Long): String {
    val sdf = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
    return sdf.format(Date(timestamp))
}

private fun formatTime(timestamp: Long): String {
    return SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))
}