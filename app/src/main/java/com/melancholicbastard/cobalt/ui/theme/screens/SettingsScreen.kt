package com.melancholicbastard.cobalt.ui.theme.screens


import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.melancholicbastard.cobalt.data.SettingsViewModel
import com.melancholicbastard.cobalt.data.SettingsViewModelFactory


@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = viewModel(
    factory = SettingsViewModelFactory(
        LocalContext.current.applicationContext as Application
    ))
) {
    val useServer by viewModel.useServerModel.observeAsState(false)
    Column (
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Настройки", style = MaterialTheme.typography.headlineLarge)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.Start
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Использовать серверное распознавание",
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = useServer,
                    onCheckedChange = {
                        viewModel.toggleUseServer()
                    }
                )
            }
        }
    }
}