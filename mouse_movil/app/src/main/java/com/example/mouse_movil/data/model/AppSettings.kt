package com.example.mouse_movil.data.model

import com.example.mouse_movil.ui.theme.ThemeMode

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = false,
    val sensitivity: Float = 1.2f,
    val acceleration: Float = 0.35f,
    val scrollSpeed: Float = 1.2f,
    val naturalScroll: Boolean = true,
    val haptics: Boolean = true,
    val keepScreenOn: Boolean = true,
    val showMouseButtons: Boolean = true,
    val firstLaunchGuideSeen: Boolean = false
)
