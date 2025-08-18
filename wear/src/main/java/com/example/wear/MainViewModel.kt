package com.example.wear

import android.annotation.SuppressLint
import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.CapabilityInfo
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.MessageEvent

class MainViewModel(
    application: Application
) :
    AndroidViewModel(application),
    DataClient.OnDataChangedListener,
    MessageClient.OnMessageReceivedListener,
    CapabilityClient.OnCapabilityChangedListener {

    private val _events = mutableStateListOf<Event>()

    /**
     * The list of events from the clients.
     */
    val events: List<Event> = _events

    /**
     * The currently received text message from phone app.
     */
    var receivedText by mutableStateOf<String?>(null)
        private set

    @SuppressLint("VisibleForTests")
    override fun onDataChanged(dataEvents: DataEventBuffer) {
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
        receivedText = messageEvent.toString()
    }

    override fun onCapabilityChanged(capabilityInfo: CapabilityInfo) {
        _events.add(
            Event(
                title = "Capability Changed",
                text = capabilityInfo.toString()
            )
        )
    }
}

/**
 * A data holder describing a client event.
 */
data class Event(
    val title: String,
    val text: String
)
