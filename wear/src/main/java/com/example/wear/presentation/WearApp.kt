package com.example.wear.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.Card
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.example.wear.MainViewModel
import com.example.wear.presentation.theme.TestDataLayerTheme
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

@Composable
fun WearApp(mainViewModel: MainViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var connectionStatus by remember { mutableStateOf("Checking connection...") }
    var connectedNodes by remember { mutableStateOf(emptyList<String>()) }
    var isDataLayerAvailable by remember { mutableStateOf(false) }

    // Check connection status when app starts
    LaunchedEffect(Unit) {
        scope.launch {
            try {
                val nodeClient = Wearable.getNodeClient(context)

                // Check if Google Play Services is available
                isDataLayerAvailable = try {
                    val googleApiAvailability = GoogleApiAvailability.getInstance()
                    val result = googleApiAvailability.isGooglePlayServicesAvailable(context)
                    result == ConnectionResult.SUCCESS
                } catch (e: Exception) {
                    false
                }

                // Get connected nodes
                val nodes = nodeClient.connectedNodes.await()
                connectedNodes = nodes.map { "${it.displayName} (${it.id})" }

                connectionStatus = when {
                    !isDataLayerAvailable -> "Google Play Services not available"
                    nodes.isEmpty() -> "No connected devices found"
                    else -> "Connected to ${nodes.size} device(s)"
                }

            } catch (e: Exception) {
                connectionStatus = "Error checking connection: ${e.message}"
            }
        }
    }

    TestDataLayerTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colors.background)
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Top,
                modifier = Modifier.fillMaxSize()
            ) {
                // Connection Status Section
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    onClick = { }
                ) {
                    Column(
                        modifier = Modifier.padding(8.dp)
                    ) {
                        Text(
                            text = "Connection Status:",
                            style = MaterialTheme.typography.caption1,
                            color = MaterialTheme.colors.primary
                        )
                        Text(
                            text = connectionStatus,
                            style = MaterialTheme.typography.caption2
                        )

                        if (connectedNodes.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Connected devices:",
                                style = MaterialTheme.typography.caption1,
                                color = MaterialTheme.colors.primary
                            )
                            connectedNodes.forEach { node ->
                                Text(
                                    text = "• $node",
                                    style = MaterialTheme.typography.caption2
                                )
                            }
                        }

                        Text(
                            text = "DataLayer Available: ${if (isDataLayerAvailable) "✓" else "✗"}",
                            style = MaterialTheme.typography.caption2,
                            color = if (isDataLayerAvailable) MaterialTheme.colors.primary else MaterialTheme.colors.error
                        )
                    }
                }

                // Display received text message
                Text(
                    text = "Message from Phone:",
                    style = MaterialTheme.typography.title3,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Text(
                    text = mainViewModel.receivedText ?: "No message received yet",
                    style = MaterialTheme.typography.body1,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                )

                // Display received file status
                if (mainViewModel.receivedFileStatus != null) {
                    Text(
                        text = "File Status:",
                        style = MaterialTheme.typography.title3,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    Text(
                        text = mainViewModel.receivedFileStatus!!,
                        style = MaterialTheme.typography.body1,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Display events list
                Text(
                    text = "Events (${mainViewModel.events.size}):",
                    style = MaterialTheme.typography.title3,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                LazyColumn(
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(mainViewModel.events) { event ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp),
                            onClick = { }
                        ) {
                            Column(
                                modifier = Modifier.padding(8.dp)
                            ) {
                                Text(
                                    text = event.title,
                                    style = MaterialTheme.typography.caption1,
                                    color = MaterialTheme.colors.primary
                                )
                                Text(
                                    text = event.text,
                                    style = MaterialTheme.typography.caption2
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}