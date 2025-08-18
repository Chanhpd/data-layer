package com.example.wear

import android.app.Application
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner

class MainApplication : Application(), ViewModelStoreOwner {

    private val appViewModelStore: ViewModelStore by lazy {
        ViewModelStore()
    }

    override val viewModelStore: ViewModelStore
        get() = appViewModelStore

    private var _mainViewModel: MainViewModel? = null

    fun getMainViewModel(): MainViewModel {
        if (_mainViewModel == null) {
            _mainViewModel = ViewModelProvider(
                this,
                ViewModelProvider.AndroidViewModelFactory.getInstance(this)
            )[MainViewModel::class.java]
        }
        return _mainViewModel!!
    }

    override fun onCreate() {
        super.onCreate()
        // Initialize the MainViewModel early
        getMainViewModel()
    }
}
