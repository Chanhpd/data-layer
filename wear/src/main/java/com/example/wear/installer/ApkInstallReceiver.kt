package com.example.wear.installer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.util.Log
import com.example.wear.MainApplication

class ApkInstallReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "ApkInstallReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
        val packageName = intent.getStringExtra(PackageInstaller.EXTRA_PACKAGE_NAME)

        Log.d(TAG, "Installation status: $status, message: $message, package: $packageName")

        val application = context.applicationContext as? MainApplication
        val viewModel = application?.getMainViewModel()

        when (status) {
            PackageInstaller.STATUS_SUCCESS -> {
                val successMessage = "APK installed successfully: $packageName"
                Log.i(TAG, successMessage)
                viewModel?.updateApkInstallStatus(successMessage)
            }

            PackageInstaller.STATUS_FAILURE -> {
                val errorMessage = "APK installation failed: $message"
                Log.e(TAG, errorMessage)
                viewModel?.updateApkInstallStatus(errorMessage)
            }

            PackageInstaller.STATUS_FAILURE_ABORTED -> {
                val abortMessage = "APK installation was aborted by user"
                Log.w(TAG, abortMessage)
                viewModel?.updateApkInstallStatus(abortMessage)
            }

            PackageInstaller.STATUS_FAILURE_BLOCKED -> {
                val blockedMessage = "APK installation was blocked: $message"
                Log.w(TAG, blockedMessage)
                viewModel?.updateApkInstallStatus(blockedMessage)
            }

            PackageInstaller.STATUS_FAILURE_CONFLICT -> {
                val conflictMessage = "APK installation conflict: $message"
                Log.w(TAG, conflictMessage)
                viewModel?.updateApkInstallStatus(conflictMessage)
            }

            PackageInstaller.STATUS_FAILURE_INCOMPATIBLE -> {
                val incompatibleMessage = "APK is incompatible: $message"
                Log.w(TAG, incompatibleMessage)
                viewModel?.updateApkInstallStatus(incompatibleMessage)
            }

            PackageInstaller.STATUS_FAILURE_INVALID -> {
                val invalidMessage = "APK is invalid: $message"
                Log.w(TAG, invalidMessage)
                viewModel?.updateApkInstallStatus(invalidMessage)
            }

            PackageInstaller.STATUS_FAILURE_STORAGE -> {
                val storageMessage = "APK installation failed due to storage: $message"
                Log.w(TAG, storageMessage)
                viewModel?.updateApkInstallStatus(storageMessage)
            }

            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val pendingMessage = "APK installation pending user action"
                Log.i(TAG, pendingMessage)
                viewModel?.updateApkInstallStatus(pendingMessage)
            }

            else -> {
                val unknownMessage = "Unknown installation status: $status, message: $message"
                Log.w(TAG, unknownMessage)
                viewModel?.updateApkInstallStatus(unknownMessage)
            }
        }
    }
}
