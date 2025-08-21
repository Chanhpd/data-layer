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
import com.google.android.gms.wearable.Asset
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.io.ByteArrayOutputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TextSenderScreen(
    modifier: Modifier = Modifier,
    onSendText: ((String) -> Unit)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var textMessage by remember { mutableStateOf("") }
    var sendStatus by remember { mutableStateOf("") }
    var connectionStatus by remember { mutableStateOf("Checking connection...") }
    var connectedNodes by remember { mutableStateOf(emptyList<String>()) }
    var isDataLayerAvailable by remember { mutableStateOf(false) }
    var selectedWffFile by remember { mutableStateOf("") }

    val dataClient = remember { Wearable.getDataClient(context) }

    // Available files (.wff and .apk)
    val availableFiles = listOf(
        "watch_face_1.wff",
        "watchface2.wff",
        "weather.apk"
    )

    // Check connection status when screen loads
    LaunchedEffect(Unit) {
        scope.launch {
            try {
                val nodeClient = Wearable.getNodeClient(context)

                // Check if DataLayer API is available
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
                    nodes.isEmpty() -> "No watch connected"
                    else -> "Connected to ${nodes.size} watch(es)"
                }

            } catch (e: Exception) {
                connectionStatus = "Error checking connection: ${e.message}"
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        Text(
            text = "Send Data to Watch",
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

        // Text Message Section
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "Send Text Message",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

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
                                sendStatus = "Sending text..."
                                sendTextToWatch(dataClient, textMessage)
                                onSendText?.invoke(textMessage)
                                sendStatus = "Text sent successfully!\nTimestamp: ${System.currentTimeMillis()}"
                            } catch (e: Exception) {
                                sendStatus = "Failed to send text: ${e.message}"
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = textMessage.isNotBlank() && connectedNodes.isNotEmpty()
                ) {
                    Text("Send Text to Watch")
                }
            }
        }

        // File Transfer Section
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "Send Files (.wff/.apk)",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                // File selection dropdown
                var expanded by remember { mutableStateOf(false) }

                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = !expanded },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                ) {
                    OutlinedTextField(
                        value = selectedWffFile.ifEmpty { "Select file" },
                        onValueChange = { },
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth()
                    )

                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        availableFiles.forEach { fileName ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(fileName)
                                        Text(
                                            text = if (fileName.endsWith(".wff")) "Watch Face" else "Android App",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                },
                                onClick = {
                                    selectedWffFile = fileName
                                    expanded = false
                                }
                            )
                        }
                    }
                }

                Button(
                    onClick = {
                        scope.launch {
                            try {
                                sendStatus = "Sending file $selectedWffFile..."
                                sendFileToWatch(context, dataClient, selectedWffFile)
                                sendStatus = "File $selectedWffFile sent successfully!\nTimestamp: ${System.currentTimeMillis()}"
                            } catch (e: Exception) {
                                sendStatus = "Failed to send file: ${e.message}"
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = selectedWffFile.isNotEmpty() && connectedNodes.isNotEmpty()
                ) {
                    Text("Send File to Watch")
                }
            }
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
                            "• Text path: /text\n" +
                            "• File path: /file\n" +
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

private suspend fun sendFileToWatch(context: android.content.Context, dataClient: DataClient, fileName: String) {
    try {
        // Read file from raw resources
        val resourceId = when (fileName) {
            "watch_face_1.wff" -> context.resources.getIdentifier("watch_face_1", "raw", context.packageName)
            "watchface2.wff" -> context.resources.getIdentifier("watchface2", "raw", context.packageName)
            "weather.apk" -> context.resources.getIdentifier("weather", "raw", context.packageName)
            else -> throw IllegalArgumentException("Unknown file: $fileName")
        }

        if (resourceId == 0) {
            throw IllegalArgumentException("File not found in resources: $fileName")
        }

        val inputStream = context.resources.openRawResource(resourceId)
        val byteArrayOutputStream = ByteArrayOutputStream()
        inputStream.copyTo(byteArrayOutputStream)
        val fileBytes = byteArrayOutputStream.toByteArray()

        inputStream.close()
        byteArrayOutputStream.close()

        // Create Asset from file bytes
        val asset = Asset.createFromBytes(fileBytes)

        // Determine file type and use appropriate path
        val dataPath = if (fileName.endsWith(".apk")) "/apk" else "/file"
        val fileType = if (fileName.endsWith(".apk")) "apk" else if (fileName.endsWith(".wff")) "wff" else "unknown"

        // Create data item request
        val putDataReq = PutDataMapRequest.create(dataPath).apply {
            dataMap.putAsset("file_data", asset)
            dataMap.putString("fileName", fileName)
            dataMap.putString("fileType", fileType)
            dataMap.putLong("fileSize", fileBytes.size.toLong())
            dataMap.putLong("timestamp", System.currentTimeMillis())
        }

        // Send to watch
        val putDataTask = dataClient.putDataItem(putDataReq.asPutDataRequest())
        putDataTask.await()

    } catch (e: Exception) {
        throw Exception("Failed to send file $fileName: ${e.message}", e)
    }
}
