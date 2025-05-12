package com.melancholicbastard.cobalt.ui.theme.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

@Composable
fun HistoryScreen(viewModel: HistoryViewModel = viewModel()) {
    val notes by viewModel.notes.collectAsState()
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("История записей", style = MaterialTheme.typography.headlineMedium)

        LazyColumn {
            items(notes) { note ->
                Text("Запись от ${note.date}")
            }
        }
    }
}

class HistoryViewModel : ViewModel() {
    private val _notes = MutableStateFlow(listOf(
        Note(date = "12.05.2023"),
        Note(date = "11.05.2023")
    ))
    val notes: StateFlow<List<Note>> = _notes

    data class Note(val date: String)
}

