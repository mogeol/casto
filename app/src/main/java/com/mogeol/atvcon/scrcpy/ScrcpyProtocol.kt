package com.mogeol.atvcon.scrcpy

import okio.BufferedSink
import java.io.IOException

/**
 * Protocol constants and packet serializers for scrcpy-server v2.x
 */
object ScrcpyProtocol {
    const val CONTROL_MSG_TYPE_INJECT_KEYCODE = 0
    const val CONTROL_MSG_TYPE_INJECT_TOUCH_EVENT = 2
    const val CONTROL_MSG_TYPE_INJECT_SCROLL_EVENT = 3
    const val CONTROL_MSG_TYPE_BACK_OR_SCREEN_ON = 4

    const val ACTION_DOWN = 0
    const val ACTION_UP = 1
    const val ACTION_MOVE = 2

    const val BUTTON_PRIMARY = 1

    /**
     * Write a 32-byte INJECT_TOUCH_EVENT packet to the scrcpy control sink.
     *
     * Format (v2.x):
     * [0]      type (1 byte = 2)
     * [1]      action (1 byte = 0:DOWN, 1:UP, 2:MOVE)
     * [2..9]   pointerId (8 bytes = 0L)
     * [10..13] x (4 bytes int32)
     * [14..17] y (4 bytes int32)
     * [18..19] screen width (2 bytes uint16)
     * [20..21] screen height (2 bytes uint16)
     * [22..23] pressure (2 bytes uint16 = 0xFFFF for 1.0f)
     * [24..27] action button (4 bytes int32 = BUTTON_PRIMARY)
     * [28..31] buttons state (4 bytes int32 = BUTTON_PRIMARY)
     */
    @Synchronized
    fun writeTouchEvent(
        sink: BufferedSink,
        action: Int,
        pointerId: Long,
        x: Int,
        y: Int,
        screenWidth: Int,
        screenHeight: Int,
        pressure: Float = 1.0f
    ) {
        try {
            sink.writeByte(CONTROL_MSG_TYPE_INJECT_TOUCH_EVENT)
            sink.writeByte(action)
            sink.writeLong(pointerId)
            sink.writeInt(x)
            sink.writeInt(y)
            sink.writeShort(screenWidth)
            sink.writeShort(screenHeight)
            val pressureU16 = (pressure.coerceIn(0f, 1f) * 0xFFFF).toInt()
            sink.writeShort(pressureU16)
            sink.writeInt(BUTTON_PRIMARY)
            sink.writeInt(if (action == ACTION_UP) 0 else BUTTON_PRIMARY)
            sink.flush()
        } catch (_: IOException) {
            // Socket may be closed or resetting
        }
    }

    /**
     * Write a 14-byte INJECT_KEYCODE packet to the scrcpy control sink.
     */
    @Synchronized
    fun writeKeyCode(
        sink: BufferedSink,
        action: Int,
        keyCode: Int,
        repeat: Int = 0,
        metaState: Int = 0
    ) {
        try {
            sink.writeByte(CONTROL_MSG_TYPE_INJECT_KEYCODE)
            sink.writeByte(action)
            sink.writeInt(keyCode)
            sink.writeInt(repeat)
            sink.writeInt(metaState)
            sink.flush()
        } catch (_: IOException) {
            // Socket may be closed
        }
    }
}
