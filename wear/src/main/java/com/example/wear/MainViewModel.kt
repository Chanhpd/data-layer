package com.example.wear

import android.annotation.SuppressLint
import android.app.Application
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.CapabilityInfo
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable

class MainViewModel(
    application: Application
) :
    AndroidViewModel(application),
    DataClient.OnDataChangedListener,
    MessageClient.OnMessageReceivedListener,
    CapabilityClient.OnCapabilityChangedListener {

    private val _events = mutableStateListOf<Event>()

    companion object {
        private const val TAG = "MainViewModel"
    }

    /**
     * The list of events from the clients.
     */
    val events: List<Event> = _events

    /**
     * The currently received text message from phone app.
     */
    var receivedText by mutableStateOf<String?>(null)
        private set

    /**
     * Status of received file transfers
     */
    var receivedFileStatus by mutableStateOf<String?>(null)
        private set

    /**
     * Status of watch face installation
     */
    var watchFaceInstallStatus by mutableStateOf<String?>(null)
        private set

    @SuppressLint("VisibleForTests")
    override fun onDataChanged(dataEvents: DataEventBuffer) {
        Log.d(TAG, "onDataChanged: Processing ${dataEvents.count} events")

        // Add all events to the event log
        _events.addAll(
            dataEvents.map { dataEvent ->
                val title = when (dataEvent.type) {
                    DataEvent.TYPE_CHANGED -> "Data Item Changed"
                    DataEvent.TYPE_DELETED -> "Data Item Deleted"
                    else -> "Data Item Unknown"
                }
                Event(
                    title = title,
                    text = dataEvent.dataItem.toString()
                )
            }
        )

        // Do additional work for specific events
        dataEvents.forEach { dataEvent ->
            when (dataEvent.type) {
                DataEvent.TYPE_CHANGED -> {
                    when (dataEvent.dataItem.uri.path) {
                        DataLayerListenerService.TEXT_PATH -> {
                            val dataMap = DataMapItem.fromDataItem(dataEvent.dataItem).dataMap
                            val textMessage = dataMap.getString("message")
                            receivedText = textMessage
                            Log.d(TAG, "Received text message: $textMessage")
                        }
                        DataLayerListenerService.FILE_PATH -> {
                            val dataMap = DataMapItem.fromDataItem(dataEvent.dataItem).dataMap
                            val fileName = dataMap.getString("fileName")
                            val fileSize = dataMap.getLong("fileSize")
                            receivedFileStatus = "Received file: $fileName (${fileSize} bytes)"
                            Log.d(TAG, "Received file: $fileName, size: $fileSize")
                        }
                    }
                }
            }
        }
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        _events.add(
            Event(
                title = "Message Received",
                text = messageEvent.toString()
            )
        )
        Log.d(TAG, "Message received: ${messageEvent.path}")
    }

    override fun onCapabilityChanged(capabilityInfo: CapabilityInfo) {
        _events.add(
            Event(
                title = "Capability Changed",
                text = capabilityInfo.toString()
            )
        )
        Log.d(TAG, "Capability changed: $capabilityInfo")
    }

    /**
     * Update watch face installation status
     */
    fun updateWatchFaceInstallStatus(status: String) {
        watchFaceInstallStatus = status
        Log.d(TAG, "Watch face install status updated: $status")
    }

    /**
     * Factory for creating MainViewModel instances
     */
    class Factory(private val application: Application) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
            if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
                return MainViewModel(application) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}

/**
 * A data holder describing a client event.
 */
data class Event(
    val title: String,
    val text: String
)
