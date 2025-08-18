package com.example.wear.watchface

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.util.zip.ZipInputStream
import java.io.FileOutputStream
import java.io.InputStream

data class WatchFaceManifest(
    val name: String,
    val id: String,
    val version: String,
    val author: String? = null,
    val description: String? = null,
    val previewImage: String? = null
)

data class WatchFaceResources(
    val manifest: WatchFaceManifest,
    val previewBitmap: Bitmap? = null,
    val watchfaceXml: String? = null,
    val resourcesDir: File,
    val imagesDir: File,
    val fontsDir: File
)

class WatchFaceParser {

    companion object {
        private const val TAG = "WatchFaceParser"
        private const val MANIFEST_FILE = "manifest.json"
        private const val PREVIEW_FILE = "preview.png"
        private const val WATCHFACE_XML = "watchface.xml"
        private const val RESOURCES_DIR = "resources"
        private const val IMAGES_DIR = "images"
        private const val FONTS_DIR = "fonts"
    }

    suspend fun parseWatchFaceFile(context: Context, wffFile: File): WatchFaceResources? {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Starting to parse watch face file: ${wffFile.name}")

                // Create extraction directory
                val extractDir = File(context.filesDir, "extracted_watchfaces/${wffFile.nameWithoutExtension}")
                if (extractDir.exists()) {
                    extractDir.deleteRecursively()
                }
                extractDir.mkdirs()

                // Extract ZIP file
                val extractedFiles = extractZipFile(wffFile, extractDir)
                Log.d(TAG, "Extracted ${extractedFiles.size} files")

                // Parse manifest.json
                val manifestFile = File(extractDir, MANIFEST_FILE)
                if (!manifestFile.exists()) {
                    Log.e(TAG, "manifest.json not found in watch face file")
                    return@withContext null
                }

                val manifest = parseManifest(manifestFile)
                Log.d(TAG, "Parsed manifest: ${manifest.name} v${manifest.version}")

                // Load preview image
                val previewFile = File(extractDir, PREVIEW_FILE)
                val previewBitmap = if (previewFile.exists()) {
                    BitmapFactory.decodeFile(previewFile.absolutePath)
                } else {
                    Log.w(TAG, "Preview image not found")
                    null
                }

                // Read watchface.xml
                val watchfaceXmlFile = File(extractDir, WATCHFACE_XML)
                val watchfaceXml = if (watchfaceXmlFile.exists()) {
                    watchfaceXmlFile.readText()
                } else {
                    Log.w(TAG, "watchface.xml not found")
                    null
                }

                // Setup resource directories
                val resourcesDir = File(extractDir, RESOURCES_DIR)
                val imagesDir = File(resourcesDir, IMAGES_DIR)
                val fontsDir = File(resourcesDir, FONTS_DIR)

                // Create directories if they don't exist
                resourcesDir.mkdirs()
                imagesDir.mkdirs()
                fontsDir.mkdirs()

                Log.d(TAG, "Successfully parsed watch face: ${manifest.name}")

                WatchFaceResources(
                    manifest = manifest,
                    previewBitmap = previewBitmap,
                    watchfaceXml = watchfaceXml,
                    resourcesDir = resourcesDir,
                    imagesDir = imagesDir,
                    fontsDir = fontsDir
                )

            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse watch face file", e)
                null
            }
        }
    }

    private fun extractZipFile(zipFile: File, extractDir: File): List<String> {
        val extractedFiles = mutableListOf<String>()

        ZipInputStream(zipFile.inputStream()).use { zipInputStream ->
            var entry = zipInputStream.nextEntry

            while (entry != null) {
                val entryFile = File(extractDir, entry.name)

                if (entry.isDirectory) {
                    entryFile.mkdirs()
                } else {
                    // Create parent directories if needed
                    entryFile.parentFile?.mkdirs()

                    // Extract file
                    FileOutputStream(entryFile).use { outputStream ->
                        zipInputStream.copyTo(outputStream)
                    }

                    extractedFiles.add(entry.name)
                    Log.d(TAG, "Extracted: ${entry.name}")
                }

                zipInputStream.closeEntry()
                entry = zipInputStream.nextEntry
            }
        }

        return extractedFiles
    }

    private fun parseManifest(manifestFile: File): WatchFaceManifest {
        val jsonString = manifestFile.readText()
        val jsonObject = JSONObject(jsonString)

        return WatchFaceManifest(
            name = jsonObject.getString("name"),
            id = jsonObject.getString("id"),
            version = jsonObject.getString("version"),
            author = jsonObject.optString("author", null),
            description = jsonObject.optString("description", null),
            previewImage = jsonObject.optString("previewImage", null)
        )
    }
}
