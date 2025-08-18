package com.example.wear

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import com.example.wear.watchface.WatchFaceManager
import com.example.wear.watchface.WatchFaceInstallResult
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
    private val watchFaceManager by lazy { WatchFaceManager(this) }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    override fun onCreate() {
        super.onCreate()
        // Register for watch face change broadcasts
        val filter = IntentFilter("com.example.wear.WATCH_FACE_CHANGED")

        // Fix for Android 13+ security requirement
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(
                watchFaceChangeReceiver,
                filter,
                Context.RECEIVER_NOT_EXPORTED
            )
        } else {
            registerReceiver(watchFaceChangeReceiver, filter)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.coroutineContext.cancel()
        try {
            unregisterReceiver(watchFaceChangeReceiver)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to unregister receiver", e)
        }
    }

    private val watchFaceChangeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val watchFaceId = intent?.getStringExtra("watch_face_id")
            Log.d(TAG, "Watch face changed to: $watchFaceId")
            // Handle watch face change if needed
        }
    }

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
                                        val savedFile = saveAssetToFile(asset, fileName)

                                        // If it's a .wff file, install and apply it as watch face
                                        if (fileName.endsWith(".wff") && savedFile != null) {
                                            installAndApplyWatchFace(savedFile, fileName)
                                        }

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

    private suspend fun saveAssetToFile(asset: Asset, fileName: String): File? {
        return try {
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
            file
        } catch (exception: Exception) {
            Log.e(TAG, "Failed to save file: $fileName", exception)
            null
        }
    }

    private suspend fun installAndApplyWatchFace(file: File, fileName: String) {
        try {
            Log.d(TAG, "Installing and applying watch face: $fileName")

            // Update status
            val application = application as? MainApplication
            application?.getMainViewModel()?.updateWatchFaceInstallStatus("Installing $fileName...")

            // Install the watch face using WatchFaceManager
            val installResult = watchFaceManager.installWatchFace(file)

            when (installResult) {
                is WatchFaceInstallResult.Success -> {
                    val manifest = installResult.manifest
                    Log.d(TAG, "Watch face installed successfully: ${manifest.name}")

                    // Apply the newly installed watch face
                    val applySuccess = watchFaceManager.applyWatchFace(manifest.id)

                    if (applySuccess) {
                        application?.getMainViewModel()?.updateWatchFaceInstallStatus(
                            "Successfully installed and applied: ${manifest.name}"
                        )
                        Log.d(TAG, "Watch face applied successfully: ${manifest.name}")

                        // Send broadcast to notify watch face service to reload
                        sendWatchFaceChangeBroadcast(manifest.id)
                    } else {
                        application?.getMainViewModel()?.updateWatchFaceInstallStatus(
                            "Installed ${manifest.name} but failed to apply it"
                        )
                        Log.w(TAG, "Failed to apply watch face: ${manifest.name}")
                    }
                }

                is WatchFaceInstallResult.Error -> {
                    val errorMessage = "Failed to install $fileName: ${installResult.message}"
                    application?.getMainViewModel()?.updateWatchFaceInstallStatus(errorMessage)
                    Log.e(TAG, errorMessage)
                }
            }

        } catch (exception: Exception) {
            Log.e(TAG, "Failed to install and apply watch face: $fileName", exception)

            // Update the application's MainViewModel with error status
            val application = application as? MainApplication
            application?.getMainViewModel()?.updateWatchFaceInstallStatus(
                "Failed to install $fileName: ${exception.message}"
            )
        }
    }

    private fun sendWatchFaceChangeBroadcast(watchFaceId: String) {
        try {
            val intent = Intent("com.example.wear.WATCH_FACE_CHANGED").setPackage(/* TODO: provide the application ID. For example: */
                packageName
            ).apply {
                putExtra("watch_face_id", watchFaceId)
            }
            sendBroadcast(intent)
            Log.d(TAG, "Sent watch face change broadcast for: $watchFaceId")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send watch face change broadcast", e)
        }
    }
}
