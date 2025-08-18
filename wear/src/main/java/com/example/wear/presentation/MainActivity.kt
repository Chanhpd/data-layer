package com.example.wear.presentation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.wear.compose.material.MaterialTheme
import com.example.wear.MainApplication
import com.example.wear.presentation.theme.TestDataLayerTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Get the MainViewModel from the application
        val mainApplication = application as MainApplication
        val mainViewModel = mainApplication.getMainViewModel()

        setContent {
            WearApp(mainViewModel)
        }
    }
}

@Composable
fun WearAppPreview() {
    TestDataLayerTheme {
        // Preview can't access real ViewModel, so we create a mock one
        // This is just for preview purposes
    }
}
