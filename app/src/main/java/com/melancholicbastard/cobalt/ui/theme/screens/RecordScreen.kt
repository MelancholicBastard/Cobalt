package com.melancholicbastard.cobalt.ui.theme.screens

import android.Manifest
import android.app.Application
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.melancholicbastard.cobalt.data.RecordViewModel
import com.melancholicbastard.cobalt.data.RecordViewModelFactory



@Composable
fun RecordScreen(
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
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        when (viewModel.recordingState) {
            RecordViewModel.RecordingState.IDLE -> {
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

            RecordViewModel.RecordingState.RECORDING -> {
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

            RecordViewModel.RecordingState.PAUSED -> {
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

            RecordViewModel.RecordingState.STOPPED -> {
                // Состояние после остановки - плеер
                Text("Запись завершена", style = MaterialTheme.typography.titleMedium)

                Button(
                    onClick = { viewModel.playRecording() },
                    enabled = !viewModel.isPlaying
                ) {
                    Text("Проиграть запись")
                }

                Button(
                    onClick = { viewModel.cancelRecording() },
                    modifier = Modifier.padding(top = 16.dp)
                ) {
                    Text("Начать новую запись")
                }
            }
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


