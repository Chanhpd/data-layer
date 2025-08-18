package com.example.wear

import android.annotation.SuppressLint
import android.util.Log
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

class DataLayerListenerService : WearableListenerService() {

    companion object {
        const val TEXT_PATH = "/text"
        const val IMAGE_PATH = "/image"
        const val IMAGE_KEY = "photo"
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
                                    Log.d(TAG, "Acknowledgment sent successfully")
                                } catch (cancellationException: CancellationException) {
                                    throw cancellationException
                                } catch (exception: Exception) {
                                    Log.e(TAG, "Failed to send acknowledgment", exception)
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

    override fun onDestroy() {
        super.onDestroy()
        scope.coroutineContext.cancel() // Cancel the coroutine scope to clean up resources
    }
}
