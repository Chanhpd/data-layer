package com.example.testdatalayer

import android.annotation.SuppressLint
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import com.example.testdatalayer.ui.theme.TestDataLayerTheme

class MainActivity : ComponentActivity() {

    @SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TestDataLayerTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    TextSenderScreen(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                        onSendText = { text ->
                            // Log or handle text sending if needed
                        }
                    )
                }
            }
        }
    }
}
