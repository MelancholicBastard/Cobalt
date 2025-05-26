package com.melancholicbastard.cobalt.ui.theme.screens

import android.app.Application
import android.util.Log
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.melancholicbastard.cobalt.data.HistoryViewModel
import com.melancholicbastard.cobalt.data.HistoryViewModelFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

@Composable
fun HistoryScreen(
    navController: NavController,
    viewModel: HistoryViewModel = viewModel(
        factory = HistoryViewModelFactory(
            LocalContext.current.applicationContext as Application)
    )
) {
    val screenState by viewModel.screenState.collectAsState()
    Log.d("HistoryScreen", "Текущее состояние: $screenState")
    // Обработка кнопки "Назад"
    val backDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher

    DisposableEffect(Unit) {
        val callback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                Log.d("HistoryScreen", "Нажатие 'назад', текущее состояние: $screenState")
                when (screenState) {
                    is HistoryViewModel.HistoryScreenState.Edit -> {
                        viewModel.exitEditMode()
                    }
                    HistoryViewModel.HistoryScreenState.DeleteConfirm -> {
                        viewModel.exitSelectionMode()
                    }
                    HistoryViewModel.HistoryScreenState.Search -> {
                        // Выход с экрана
                        navController.popBackStack()
                    }
                }
            }
        }
        backDispatcher?.addCallback(callback)
        onDispose {
            Log.d("HistoryScreen", "DisposableEffect.onDispose()")
            callback.remove()
        }
    }


    when (screenState) {
        is HistoryViewModel.HistoryScreenState.Search -> {
            SearchModeScreen(viewModel)
        }
        is HistoryViewModel.HistoryScreenState.Edit -> {
            val noteId = (screenState as HistoryViewModel.HistoryScreenState.Edit).noteId
            EditModeScreen(viewModel, noteId)
        }

        is HistoryViewModel.HistoryScreenState.DeleteConfirm -> {
            SearchModeScreen(viewModel)
        }
    }
}

