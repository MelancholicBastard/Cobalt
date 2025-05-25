package com.melancholicbastard.cobalt.ui.theme.screens

import android.Manifest
import android.app.Application
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.melancholicbastard.cobalt.data.RecordViewModel
import com.melancholicbastard.cobalt.data.RecordViewModelFactory
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.max
import androidx.navigation.NavController
import com.melancholicbastard.cobalt.navigation.Screen


@Composable
    fun RecordScreen(
    navController: NavController,
    viewModel: RecordViewModel = viewModel(
        factory = RecordViewModelFactory(
            LocalContext.current.applicationContext as Application
        )
    )
) {
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.startRecording()
        }
    }

    // Обработка необходимости запроса разрешения
    LaunchedEffect(viewModel.permissionNeeded.value) {
        if (viewModel.permissionNeeded.value) {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            viewModel.permissionNeeded.value = false
        }
        viewModel.recordSavedEvent.collect {
            // Переход на HistoryScreen
            navController.navigate(Screen.History.route) {
                popUpTo(Screen.Record.route) { inclusive = true }
                launchSingleTop = true
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        when (viewModel.recordingState) {
            RecordViewModel.RecordingState.IDLE -> IdleState(viewModel)
            RecordViewModel.RecordingState.RECORDING -> RecordingState(viewModel)
            RecordViewModel.RecordingState.PAUSED -> PausedState(viewModel)
            RecordViewModel.RecordingState.STOPPED -> StoppedState(viewModel)
            RecordViewModel.RecordingState.LOADING -> LoadingState()
        }
    }
}

@Composable
fun LoadingState() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator()
        Text("Обработка...")
    }
}

@Composable
private fun IdleState(viewModel: RecordViewModel) {
    // Начальное состояние - кнопка записи
    Button(
        onClick = { viewModel.startRecording() },
        modifier = Modifier.size(100.dp),
        shape = CircleShape
    ) {
        Icon(
            imageVector = Icons.Default.Call,
            contentDescription = "Record",
            modifier = Modifier.size(48.dp)
        )
    }
    Text("Нажмите для начала записи")
}

@Composable
private fun RecordingState(viewModel: RecordViewModel) {
    // Состояние записи - таймер и кнопки управления
    RecordingTimer(viewModel.elapsedTime)

    Row(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Кнопка паузы
        IconButton(
            onClick = { viewModel.pauseRecording() },
            modifier = Modifier.size(60.dp)
        ) {
            Icon(
                imageVector = Icons.Default.List,
                contentDescription = "Pause",
                modifier = Modifier.size(36.dp)
            )
        }

        // Кнопка отмены
        IconButton(
            onClick = { viewModel.cancelRecording() },
            modifier = Modifier.size(60.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Cancel",
                modifier = Modifier.size(36.dp)
            )
        }
    }
}

@Composable
private fun PausedState(viewModel: RecordViewModel) {
    // Состояние паузы - кнопки возобновления/отмены
    RecordingTimer(viewModel.elapsedTime)

    Row(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Кнопка возобновления
        IconButton(
            onClick = { viewModel.resumeRecording() },
            modifier = Modifier.size(60.dp)
        ) {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = "Resume",
                modifier = Modifier.size(36.dp)
            )
        }

        // Кнопка остановки с сохранением
        IconButton(
            onClick = { viewModel.stopRecording() },
            modifier = Modifier.size(60.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Done,
                contentDescription = "Stop",
                modifier = Modifier.size(36.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StoppedState(viewModel: RecordViewModel) {
    val playbackPosition by viewModel.playbackPosition.collectAsState()
    val playbackDuration by remember { derivedStateOf { viewModel.playbackDuration.value } }
    var totalDuration by remember { mutableLongStateOf(0L) }
    var recordingTitle by remember { mutableStateOf(TextFieldValue("")) }

    val colors = TextFieldDefaults.textFieldColors(
        focusedIndicatorColor = Color.Transparent,
        unfocusedIndicatorColor = Color.Transparent,
        disabledIndicatorColor = Color.Transparent,
        containerColor = MaterialTheme.colorScheme.surfaceBright
    )
    val shape = RoundedCornerShape(
        topStart = 8.dp,
        topEnd = 8.dp,
        bottomStart = 8.dp,
        bottomEnd = 8.dp
    )

    // Обновляем длительность при каждом изменении playbackDuration
    LaunchedEffect(playbackDuration) {
        totalDuration = playbackDuration
    }

    OutlinedTextField(
        value = recordingTitle,
        onValueChange = { newText ->
            if (newText.text.length <= 50 && !newText.text.contains("\n")) {
                recordingTitle = newText
            } },
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .border(3.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp)),
        singleLine = true,
        placeholder = {
            if (recordingTitle.text.isBlank()) {
                Text(text = "Новая запись")
            } },
        textStyle = MaterialTheme.typography.titleMedium,
        colors = colors,
        shape = shape,)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .border(3.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
            .padding(16.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Время: ${formatPlayingTimer(playbackPosition)} / ${formatPlayingTimer(totalDuration)}",
                modifier = Modifier.padding(bottom = 8.dp)
            )
            // Только если аудио готово к воспроизведению
            if (totalDuration > 0) {
                val progress by remember {
                    derivedStateOf {
                        playbackPosition / totalDuration.toFloat()
                    }
                }
                Slider(
                    value = progress,
                    onValueChange = { newProgress ->
                        val newPosition = (newProgress * totalDuration).toLong()
                        viewModel.seekTo(newPosition) },
                    valueRange = 0f..1f,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                )
            } else {
                Text(text = "Файл не готов к воспроизведению")
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.padding(8.dp)
            ) {
                // 1. Перемотка -5 сек
                IconButton(onClick = { viewModel.fastBackward() }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Назад")
                }

                // 2. Воспроизвести
                IconButton(onClick = { viewModel.togglePlayback() }) {
                    Icon(
                        imageVector = if (viewModel.isPlaying.value) Icons.Default.List else Icons.Default.PlayArrow,
                        contentDescription = if (viewModel.isPlaying.value) "Пауза" else "Проиграть"
                    )
                }

                // 3. Перемотка +5 сек
                IconButton(onClick = { viewModel.fastForward() }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Вперёд")
                }
            }
        }
    }

    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(16.dp)) {
        Button(
            onClick = {
            viewModel.stopPlayback()
            viewModel.cancelRecording() }
        ) {
            Text("Новая запись")
        }

        Button(
            onClick = {
            val title = if (recordingTitle.text.isBlank()) "Новая запись" else recordingTitle.text
            viewModel.saveToDatabase( title ) },
            enabled = !viewModel.isPlaying.value
        ) {
            Text("Сохранить запись")
        }
    }
        // Редактор транскрипции
        TranscribedTextEditor(viewModel = viewModel, colors, shape)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TranscribedTextEditor(
    viewModel: RecordViewModel,
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
    val textFromViewModel by viewModel.textFromAudioFile.collectAsState()
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

@Composable
private fun RecordingTimer(milliseconds: Long) {
    val formattedTime = remember(milliseconds) {
        val hours = milliseconds / (1000 * 60 * 60)
        val minutes = (milliseconds / (1000 * 60)) % 60
        val seconds = (milliseconds / 1000) % 60
        val millis = (milliseconds % 1000) / 10 // Показываем только 2 цифры миллисекунд

        if (hours > 0) {    // Если время записи доходит до часу
            String.format("%02d:%02d:%02d:%02d", hours, minutes, seconds, millis)
        } else {
            String.format("%02d:%02d:%02d", minutes, seconds, millis)
        }
    }

    Text(
        text = formattedTime,
        style = MaterialTheme.typography.displayMedium,
        modifier = Modifier.padding(vertical = 24.dp)
    )
}

fun formatPlayingTimer(millis: Long): String {
    val minutes = millis / 1000 / 60
    val seconds = (millis / 1000) % 60
    return String.format("%02d:%02d", minutes, seconds)
}

