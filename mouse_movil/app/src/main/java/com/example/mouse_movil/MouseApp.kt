package com.example.mouse_movil

import android.app.Application
import com.example.mouse_movil.di.AppContainer

class MouseApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
