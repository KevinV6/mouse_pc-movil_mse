package com.example.mouse_movil.data.model

import kotlinx.serialization.Serializable

@Serializable
data class PairedPc(
    val serverId: String,
    val name: String,
    val host: String,
    val port: Int,
    val fp: String,
    val tokenEnc: String,
    val lastConnected: Long
)
