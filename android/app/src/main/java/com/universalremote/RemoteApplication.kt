package com.universalremote

import android.app.Application
import androidx.lifecycle.ProcessLifecycleOwner
import com.universalremote.core.lifecycle.AppForegroundObserver
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class RemoteApplication : Application() {

    @Inject
    lateinit var foregroundObserver: AppForegroundObserver

    override fun onCreate() {
        super.onCreate()
        ProcessLifecycleOwner.get().lifecycle.addObserver(foregroundObserver)
    }
}
