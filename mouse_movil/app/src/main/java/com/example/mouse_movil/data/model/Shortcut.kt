package com.example.mouse_movil.data.model

import kotlinx.serialization.Serializable

@Serializable
data class Shortcut(
    val id: String,
    val name: String,
    val iconName: String,
    val keys: List<String>
)
