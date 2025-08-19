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

                // Find the actual location of manifest.json
                // It could be in root directory or in a subdirectory like "watchface/"
                val manifestFile = findFile(extractDir, MANIFEST_FILE)
                if (manifestFile == null || !manifestFile.exists()) {
                    Log.e(TAG, "manifest.json not found in watch face file")
                    Log.d(TAG, "Extracted files: ${extractedFiles.joinToString(", ")}")
                    return@withContext null
                }

                val manifest = parseManifest(manifestFile)
                Log.d(TAG, "Parsed manifest: ${manifest.name} v${manifest.version}")

                // Find preview image in the same directory as manifest
                val manifestDir = manifestFile.parentFile!!
                val previewFile = File(manifestDir, PREVIEW_FILE)
                val previewBitmap = if (previewFile.exists()) {
                    BitmapFactory.decodeFile(previewFile.absolutePath)
                } else {
                    // Try to find preview.png anywhere in the extracted files
                    val foundPreview = findFile(extractDir, PREVIEW_FILE)
                    if (foundPreview?.exists() == true) {
                        BitmapFactory.decodeFile(foundPreview.absolutePath)
                    } else {
                        Log.w(TAG, "Preview image not found")
                        null
                    }
                }

                // Find watchface.xml in the same directory as manifest
                val watchfaceXmlFile = File(manifestDir, WATCHFACE_XML)
                val watchfaceXml = if (watchfaceXmlFile.exists()) {
                    watchfaceXmlFile.readText()
                } else {
                    // Try to find watchface.xml anywhere in the extracted files
                    val foundXml = findFile(extractDir, WATCHFACE_XML)
                    if (foundXml?.exists() == true) {
                        foundXml.readText()
                    } else {
                        Log.w(TAG, "watchface.xml not found")
                        null
                    }
                }

                // Setup resource directories - look for resources in the same directory as manifest
                val resourcesDir = File(manifestDir, RESOURCES_DIR)
                val imagesDir = File(resourcesDir, IMAGES_DIR)
                val fontsDir = File(resourcesDir, FONTS_DIR)

                // If resources not found in manifest directory, try to find it anywhere
                val actualResourcesDir = if (resourcesDir.exists()) {
                    resourcesDir
                } else {
                    findDirectory(extractDir, RESOURCES_DIR) ?: resourcesDir
                }

                val actualImagesDir = File(actualResourcesDir, IMAGES_DIR)
                val actualFontsDir = File(actualResourcesDir, FONTS_DIR)

                // Create directories if they don't exist
                actualResourcesDir.mkdirs()
                actualImagesDir.mkdirs()
                actualFontsDir.mkdirs()

                Log.d(TAG, "Successfully parsed watch face: ${manifest.name}")
                Log.d(TAG, "Manifest found at: ${manifestFile.absolutePath}")
                Log.d(TAG, "Resources directory: ${actualResourcesDir.absolutePath}")

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

    // Helper function to find a file recursively in a directory
    private fun findFile(directory: File, fileName: String): File? {
        if (!directory.exists() || !directory.isDirectory) return null

        // Check if file exists in current directory
        val directFile = File(directory, fileName)
        if (directFile.exists()) {
            return directFile
        }

        // Search in subdirectories
        directory.listFiles()?.forEach { file ->
            if (file.isDirectory) {
                val found = findFile(file, fileName)
                if (found != null) return found
            }
        }

        return null
    }

    // Helper function to find a directory recursively
    private fun findDirectory(directory: File, dirName: String): File? {
        if (!directory.exists() || !directory.isDirectory) return null

        directory.listFiles()?.forEach { file ->
            if (file.isDirectory) {
                if (file.name == dirName) {
                    return file
                }
                val found = findDirectory(file, dirName)
                if (found != null) return found
            }
        }

        return null
    }
}
