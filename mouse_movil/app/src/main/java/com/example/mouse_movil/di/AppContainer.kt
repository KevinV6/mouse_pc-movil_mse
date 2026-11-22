package com.example.mouse_movil.di

import android.content.Context

/**
 * Manual dependency container initialized once in [com.example.mouse_movil.MouseApp].
 * Subsequent blocks register singletons (ConnectionManager, InputSender, Repositories) here.
 */
class AppContainer(val appContext: Context)
