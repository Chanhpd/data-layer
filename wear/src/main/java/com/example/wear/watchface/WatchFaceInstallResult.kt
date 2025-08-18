package com.example.wear.watchface

sealed class WatchFaceInstallResult {
    data class Success(val manifest: WatchFaceManifest) : WatchFaceInstallResult()
    data class Error(val message: String) : WatchFaceInstallResult()
}
