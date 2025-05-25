package com.melancholicbastard.cobalt.ui.theme.screens

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import com.melancholicbastard.cobalt.data.HistoryViewModel
import com.melancholicbastard.cobalt.data.RecordViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditModeScreen(viewModel: HistoryViewModel, noteId: Long) {
    // Состояния из ViewModel через StateFlow
    val currentNote by viewModel.currentNote.collectAsState()
    val editingTitle by viewModel.editingTitle.collectAsState()
    val editingTranscript by viewModel.editingTranscript.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val playbackPosition by viewModel.playbackPosition.collectAsState()
    val playbackDuration by viewModel.playbackDuration.collectAsState()

    // Загружаем заметку при первом входе в режим
    LaunchedEffect(noteId) {
        viewModel.loadNoteById(noteId)
    }

    // Редактирование заголовка
    when {
        currentNote != null -> {
            val note = currentNote!!
            Log.d("EditModeScreen", "Запись найдена: ${note.title}")
            // Редактирование заголовка
            var title by remember { mutableStateOf(editingTitle) }
            Column(modifier = Modifier.padding(16.dp)) {

                TextField(
                    value = title,
                    onValueChange = {
                        title = it
                        viewModel.updateNoteTitle(it)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 1,
                    placeholder = { Text("Введите заголовок") }
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Плеер
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Воспроизведение: ${formatPlayingTimer(playbackPosition)} / ${
                                formatPlayingTimer(
                                    playbackDuration
                                )
                            }",
                            modifier = Modifier.padding(bottom = 8.dp)
                        )

                        if (playbackDuration > 0) {
                            val progress by remember {
                                derivedStateOf {
                                    playbackPosition / playbackDuration.toFloat()
                                }
                            }

                            Slider(
                                value = progress,
                                onValueChange = { newProgress ->
                                    val newPosition = (newProgress * playbackDuration).toLong()
                                    viewModel.seekTo(newPosition)
                                },
                                valueRange = 0f..1f,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp)
                            )
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.padding(8.dp)
                        ) {
                            IconButton(onClick = { viewModel.fastBackward() }) {
                                Icon(
                                    Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                                    contentDescription = "Назад"
                                )
                            }

                            IconButton(onClick = {
                                if (isPlaying) viewModel.pausePlayback()
                                else viewModel.playRecording(noteId)
                            }) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Default.List else Icons.Default.PlayArrow,
                                    contentDescription = if (isPlaying) "Пауза" else "Проиграть"
                                )
                            }

                            IconButton(onClick = { viewModel.fastForward() }) {
                                Icon(
                                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    contentDescription = "Вперед"
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Редактор транскрипции
                TranscribedTextEditor(viewModel = viewModel)

                Spacer(modifier = Modifier.height(16.dp))

                // Кнопки сохранения и выхода
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Button(
                        onClick = {
                            viewModel.saveCurrentNote()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Сохранить изменения")
                    }

                    Button(
                        onClick = { viewModel.exitEditMode() },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Закрыть")
                    }
                }
            }
        }

        else -> {
            Box(modifier = Modifier.fillMaxSize()) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TranscribedTextEditor(
    viewModel: HistoryViewModel,
    colors: TextFieldColors = TextFieldDefaults.textFieldColors(
        focusedIndicatorColor = Color.Transparent,
        unfocusedIndicatorColor = Color.Transparent,
        disabledIndicatorColor = Color.Transparent,
        containerColor = MaterialTheme.colorScheme.surfaceBright
    ),
    shape: RoundedCornerShape = RoundedCornerShape(
        topStart = 8.dp,
        topEnd = 8.dp,
        bottomStart = 8.dp,
        bottomEnd = 8.dp
    )
) {
    val textFromViewModel by viewModel.editingTranscript.collectAsState()
    var textFieldValue by remember { mutableStateOf(TextFieldValue(textFromViewModel)) }
    val scrollState = rememberScrollState()
    val keyboardController = LocalSoftwareKeyboardController.current
    val imeInsets = WindowInsets.ime.asPaddingValues()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = max(imeInsets.calculateBottomPadding() - 75.dp, 0.dp)),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 16.dp)
        ) {
            Text("Распознанный текст", style = MaterialTheme.typography.titleSmall)

            IconButton(
                onClick = { viewModel.updateTranscribedText(textFieldValue.text) },
                enabled = textFromViewModel != textFieldValue.text,
                modifier = Modifier.size(60.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Done,
                    contentDescription = "Save",
                    modifier = Modifier.size(36.dp)
                )
            }
            IconButton(
                onClick = { textFieldValue = TextFieldValue(textFromViewModel) },
                enabled = textFromViewModel != textFieldValue.text,
                modifier = Modifier.size(60.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "ReturnBack",
                    modifier = Modifier.size(36.dp)
                )
            }
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .background(MaterialTheme.colorScheme.surfaceBright)
                .border(3.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                .verticalScroll(scrollState)
        ) {
            OutlinedTextField(
                value = textFieldValue,
                onValueChange = { textFieldValue = it },
                modifier = Modifier
                    .fillMaxSize()
                    .onFocusChanged { focusState ->
                        if (focusState.isFocused) {
                            keyboardController?.show() // Показываем клавиатуру
                        }
                    }
                    .background(MaterialTheme.colorScheme.surfaceBright),
                maxLines = Int.MAX_VALUE,
                textStyle = MaterialTheme.typography.bodyLarge,
                colors = colors,
                shape = shape,
                singleLine = false
            )
        }
    }
}
