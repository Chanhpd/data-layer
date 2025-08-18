package com.example.testdatalayer

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.example.testdatalayer.ui.theme.TestDataLayerTheme
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class MainActivity : ComponentActivity() {
    private val dataClient by lazy { Wearable.getDataClient(this) }

    @SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TestDataLayerTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) {
                    TextSenderScreen()
                }
            }
        }
    }

    companion object {
        private const val TAG = "MainActivity"
        private const val TEXT_PATH = "/text"
        private const val TIME_KEY = "time"
        private const val TEXT_KEY = "message"
    }
}
