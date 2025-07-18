package com.melancholicbastard.cobalt

import android.content.pm.ActivityInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.melancholicbastard.cobalt.data.VoskModelManager
import com.melancholicbastard.cobalt.navigation.AppNavigation
import com.melancholicbastard.cobalt.ui.theme.CobaltTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        VoskModelManager.initialize(applicationContext)
        enableEdgeToEdge()
        setContent {
            CobaltTheme {
                AppNavigation()
//                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
//                    Greeting(
//                        name = "Android",
//                        modifier = Modifier.padding(innerPadding)
//                    )
//                }
            }
        }
    }
    override fun onDestroy() {
        if (isFinishing) {
            VoskModelManager.release()
        }
        super.onDestroy()
    }
}
