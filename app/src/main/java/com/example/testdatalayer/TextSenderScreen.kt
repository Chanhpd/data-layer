package com.example.testdatalayer

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataItem
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TextSenderScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var textMessage by remember { mutableStateOf("") }
    var sendStatus by remember { mutableStateOf("") }
    var connectionStatus by remember { mutableStateOf("Checking connection...") }
    var connectedNodes by remember { mutableStateOf(emptyList<String>()) }
    var isDataLayerAvailable by remember { mutableStateOf(false) }

    val dataClient = remember { Wearable.getDataClient(context) }

    // Check connection status when screen loads
    LaunchedEffect(Unit) {
        scope.launch {
            try {
                val nodeClient = Wearable.getNodeClient(context)

                // Check if DataLayer API is available
                isDataLayerAvailable = try {
                    val googleApiAvailability = GoogleApiAvailability.getInstance()
                    val result = googleApiAvailability.isGooglePlayServicesAvailable(context)
                    result == ConnectionResult.SUCCESS    } catch (e: Exception) {
                    false
                }

                // Get connected nodes
                val nodes = nodeClient.connectedNodes.await()
                connectedNodes = nodes.map { "${it.displayName} (${it.id})" }

                connectionStatus = when {
                    !isDataLayerAvailable -> "Google Play Services not available"
                    nodes.isEmpty() -> "No watch connected"
                    else -> "Connected to ${nodes.size} watch(es)"
                }

            } catch (e: Exception) {
                connectionStatus = "Error checking connection: ${e.message}"
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        Text(
            text = "Send Text to Watch",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        // Connection Status Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "Connection Status",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Text(
                    text = connectionStatus,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (connectedNodes.isNotEmpty()) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.error
                    }
                )

                if (connectedNodes.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Connected devices:",
                        style = MaterialTheme.typography.labelMedium
                    )
                    connectedNodes.forEach { node ->
                        Text(
                            text = "• $node",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                Text(
                    text = "DataLayer Available: ${if (isDataLayerAvailable) "✓" else "✗"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isDataLayerAvailable) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.error
                    }
                )
            }
        }

        OutlinedTextField(
            value = textMessage,
            onValueChange = { textMessage = it },
            label = { Text("Enter message") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text)
        )

        Button(
            onClick = {
                scope.launch {
                    try {
                        sendStatus = "Sending..."
                        sendTextToWatch(dataClient, textMessage)
                        sendStatus = "Message sent successfully!\nTimestamp: ${System.currentTimeMillis()}"
                    } catch (e: Exception) {
                        sendStatus = "Failed to send: ${e.message}"
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = textMessage.isNotBlank() && connectedNodes.isNotEmpty()
        ) {
            Text("Send to Watch")
        }

        // Refresh connection button
        OutlinedButton(
            onClick = {
                scope.launch {
                    connectionStatus = "Checking connection..."
                    try {
                        val nodeClient = Wearable.getNodeClient(context)
                        val nodes = nodeClient.connectedNodes.await()
                        connectedNodes = nodes.map { "${it.displayName} (${it.id})" }

                        connectionStatus = when {
                            !isDataLayerAvailable -> "Google Play Services not available"
                            nodes.isEmpty() -> "No watch connected"
                            else -> "Connected to ${nodes.size} watch(es)"
                        }
                    } catch (e: Exception) {
                        connectionStatus = "Error: ${e.message}"
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
        ) {
            Text("Refresh Connection")
        }

        if (sendStatus.isNotEmpty()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp)
            ) {
                Text(
                    text = sendStatus,
                    modifier = Modifier.padding(16.dp),
                    color = if (sendStatus.startsWith("Failed")) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "Debug Information:",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Text(
                    text = "• Make sure watch is paired and connected\n" +
                            "• Both apps should be installed\n" +
                            "• DataLayer path: /text\n" +
                            "• Message key: 'message'\n" +
                            "• Check logcat for detailed logs",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

private suspend fun sendTextToWatch(dataClient: DataClient, message: String) {
    val putDataReq = PutDataMapRequest.create("/text").apply {
        dataMap.putString("message", message)
        dataMap.putLong("timestamp", System.currentTimeMillis())
    }

    val putDataTask = dataClient.putDataItem(putDataReq.asPutDataRequest())
    putDataTask.await()
}
