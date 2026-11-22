package com.example.mouse_movil.core.protocol

enum class FrameType(val code: Byte) {
    JSON(0x01),
    MOUSE_MOVE(0x10),
    SCROLL(0x11),
    ZOOM(0x12);

    companion object {
        fun fromCode(code: Byte): FrameType? = entries.firstOrNull { it.code == code }
    }
}
