package com.example.wear

import android.annotation.SuppressLint
import android.util.Log
import com.google.android.gms.wearable.Asset
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.io.File
import java.io.FileOutputStream

class DataLayerListenerService : WearableListenerService() {

    companion object {
        const val TEXT_PATH = "/text"
        const val IMAGE_PATH = "/image"
        const val FILE_PATH = "/file"
        const val IMAGE_KEY = "photo"
        const val FILE_KEY = "file_data"
        private const val TAG = "DataLayerService"
        private const val DATA_ITEM_RECEIVED_PATH = "/data-item-received"
    }

    private val messageClient by lazy { Wearable.getMessageClient(this) }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        Log.d(TAG, "onDataChanged: Processing ${dataEvents.count} events")

        // Forward events to MainViewModel through the application's viewmodel instance
        val application = application as? MainApplication
        application?.getMainViewModel()?.onDataChanged(dataEvents)

        // Process each data event
        dataEvents.forEach { dataEvent ->
            Log.d(TAG, "Processing event: ${dataEvent.dataItem.uri}")

            when (dataEvent.type) {
                DataEvent.TYPE_CHANGED -> {
                    val uri = dataEvent.dataItem.uri
                    when (uri.path) {
                        TEXT_PATH -> {
                            val dataMap = DataMapItem.fromDataItem(dataEvent.dataItem).dataMap
                            val message = dataMap.getString("message")
                            Log.d(TAG, "Received text message: $message")

                            // Send acknowledgment back to phone
                            scope.launch {
                                try {
                                    val nodeId = uri.host!!
                                    val payload = "Text received: $message".toByteArray()
                                    messageClient.sendMessage(
                                        nodeId,
                                        DATA_ITEM_RECEIVED_PATH,
                                        payload
                                    ).await()
                                    Log.d(TAG, "Text acknowledgment sent successfully")
                                } catch (cancellationException: CancellationException) {
                                    throw cancellationException
                                } catch (exception: Exception) {
                                    Log.e(TAG, "Failed to send text acknowledgment", exception)
                                }
                            }
                        }

                        FILE_PATH -> {
                            val dataMap = DataMapItem.fromDataItem(dataEvent.dataItem).dataMap
                            val fileName = dataMap.getString("fileName")
                            val asset = dataMap.getAsset(FILE_KEY)

                            Log.d(TAG, "Received file: $fileName")

                            // Handle file asset
                            scope.launch {
                                try {
                                    if (asset != null && fileName != null) {
                                        saveAssetToFile(asset, fileName)

                                        // Send acknowledgment back to phone
                                        val nodeId = uri.host!!
                                        val payload = "File received: $fileName".toByteArray()
                                        messageClient.sendMessage(
                                            nodeId,
                                            DATA_ITEM_RECEIVED_PATH,
                                            payload
                                        ).await()
                                        Log.d(TAG, "File acknowledgment sent successfully")
                                    }
                                } catch (cancellationException: CancellationException) {
                                    throw cancellationException
                                } catch (exception: Exception) {
                                    Log.e(TAG, "Failed to process file or send acknowledgment", exception)
                                }
                            }
                        }

                        IMAGE_PATH -> {
                            Log.d(TAG, "Received image data")
                            // Image handling can be added here if needed
                        }
                    }
                }
                DataEvent.TYPE_DELETED -> {
                    Log.d(TAG, "Data item deleted: ${dataEvent.dataItem.uri}")
                }
            }
        }
    }

    private suspend fun saveAssetToFile(asset: Asset, fileName: String) {
        try {
            val inputStream = Wearable.getDataClient(this)
                .getFdForAsset(asset)
                .await()
                .inputStream

            val filesDir = File(filesDir, "received_files")
            if (!filesDir.exists()) {
                filesDir.mkdirs()
            }

            val file = File(filesDir, fileName)
            FileOutputStream(file).use { outputStream ->
                inputStream.copyTo(outputStream)
            }

            Log.d(TAG, "File saved successfully: ${file.absolutePath}")
        } catch (exception: Exception) {
            Log.e(TAG, "Failed to save file: $fileName", exception)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.coroutineContext.cancel()
    }
}
