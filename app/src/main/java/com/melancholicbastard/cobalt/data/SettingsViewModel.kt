package com.melancholicbastard.cobalt.data

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider


class SettingsViewModelFactory(
    private val application: Application
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SettingsViewModel::class.java)) {
            return SettingsViewModel(
                sharedPreferencesHelper = SharedPreferencesHelper(application)
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

class SharedPreferencesHelper(context: Context) {
    private val preferences = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)

    var useServerModel: Boolean
        get() = preferences.getBoolean("use_server_model", false)
        set(value) = preferences.edit().putBoolean("use_server_model", value).apply()
}

class NetworkChecker(private val context: Context) {
    fun isInternetAvailable(): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = connectivityManager.activeNetwork ?: return false
            val activeNetwork = connectivityManager.getNetworkCapabilities(network) ?: return false

            return when {
                activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> true
                activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> true
                activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> true
                else -> false
            }
        } else {
            // Для старых версий Android (до API 23)
            val networkInfo = connectivityManager.activeNetworkInfo
            return networkInfo != null && networkInfo.isConnected
        }
    }
}

class SettingsViewModel(
    private val sharedPreferencesHelper: SharedPreferencesHelper
) : ViewModel() {

    val useServerModel = MutableLiveData(sharedPreferencesHelper.useServerModel)

    fun toggleUseServer() {
        val newValue = !(useServerModel.value ?: false)
        sharedPreferencesHelper.useServerModel = newValue
        useServerModel.value = newValue
    }
}