package com.example.wear.installer

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream

class ApkInstaller(private val context: Context) {

    companion object {
        private const val TAG = "ApkInstaller"
        private const val INSTALL_REQUEST_CODE = 1001
    }

    /**
     * Install APK with multiple fallback methods
     */
    suspend fun installApk(apkFile: File): InstallResult {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Starting APK installation: ${apkFile.absolutePath}")

                if (!apkFile.exists()) {
                    return@withContext InstallResult.Error("APK file does not exist")
                }

                // Method 1: Try PackageInstaller (Android 5.0+)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    try {
                        val result = installUsingPackageInstaller(apkFile)
                        if (result is InstallResult.Success) {
                            return@withContext result
                        }
                        Log.w(TAG, "PackageInstaller method failed, trying Intent method")
                    } catch (e: Exception) {
                        Log.w(TAG, "PackageInstaller method failed: ${e.message}")
                    }
                }

                // Method 2: Try Intent-based installation
                val intentResult = installUsingIntent(apkFile)
                if (intentResult is InstallResult.Success) {
                    return@withContext intentResult
                }

                // Method 3: Try silent installation (requires system permissions)
                if (hasSystemPermissions()) {
                    return@withContext installSilently(apkFile)
                }

                return@withContext InstallResult.Error("All installation methods failed")

            } catch (e: Exception) {
                Log.e(TAG, "Failed to install APK", e)
                return@withContext InstallResult.Error("Installation failed: ${e.message}")
            }
        }
    }

    /**
     * Method 1: Use PackageInstaller API (Android 5.0+)
     */
    private suspend fun installUsingPackageInstaller(apkFile: File): InstallResult {
        return try {
            val packageInstaller = context.packageManager.packageInstaller
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)

            // Create installation session
            val sessionId = packageInstaller.createSession(params)
            val session = packageInstaller.openSession(sessionId)

            // Copy APK to session
            val inputStream = FileInputStream(apkFile)
            val outputStream = session.openWrite("app", 0, apkFile.length())

            inputStream.use { input ->
                outputStream.use { output ->
                    input.copyTo(output)
                    session.fsync(outputStream)
                }
            }

            // Create install intent
            val intent = Intent(context, ApkInstallReceiver::class.java)
            val pendingIntent = android.app.PendingIntent.getBroadcast(
                context,
                INSTALL_REQUEST_CODE,
                intent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
            )

            // Commit session
            session.commit(pendingIntent.intentSender)
            session.close()

            Log.d(TAG, "PackageInstaller session created successfully")
            InstallResult.Success("Installation started via PackageInstaller")

        } catch (e: Exception) {
            Log.e(TAG, "PackageInstaller installation failed", e)
            InstallResult.Error("PackageInstaller failed: ${e.message}")
        }
    }

    /**
     * Method 2: Use Intent-based installation
     */
    private fun installUsingIntent(apkFile: File): InstallResult {
        return try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    // For Android 7.0 and above, use FileProvider
                    FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        apkFile
                    )
                } else {
                    // For older Android versions
                    Uri.fromFile(apkFile)
                }

                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            context.startActivity(intent)
            Log.d(TAG, "Intent-based installation started")
            InstallResult.Success("Installation prompt opened")

        } catch (e: Exception) {
            Log.e(TAG, "Intent installation failed", e)
            InstallResult.Error("Intent installation failed: ${e.message}")
        }
    }

    /**
     * Method 3: Silent installation (requires system permissions)
     * Note: This method is disabled as it requires internal Android APIs
     * that are not available in the public SDK
     */
    private fun installSilently(apkFile: File): InstallResult {
        return InstallResult.Error("Silent installation not available - requires system-level access")
    }

    /**
     * Check if app has system-level permissions
     */
    private fun hasSystemPermissions(): Boolean {
        return try {
            val permission = context.checkCallingOrSelfPermission("android.permission.INSTALL_PACKAGES")
            permission == PackageManager.PERMISSION_GRANTED
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Check if APK installation from unknown sources is allowed
     */
    fun canInstallPackages(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true // Before Android 8.0, this was controlled by a system setting
        }
    }

    /**
     * Request permission to install packages (Android 8.0+)
     */
    fun requestInstallPermission(): Intent? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !canInstallPackages()) {
            Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        } else {
            null
        }
    }
}

/**
 * Result of APK installation attempt
 */
sealed class InstallResult {
    data class Success(val message: String) : InstallResult()
    data class Error(val message: String) : InstallResult()
}
