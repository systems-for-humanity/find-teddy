package com.messytable.findteddy.platform

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

actual class HapticFeedback actual constructor() {
    private val vibrator: Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager =
                appContext.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            manager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            appContext.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }

    actual fun tap(strength: Float) {
        val v = vibrator ?: return
        val s = strength.coerceIn(0f, 1f)
        val durationMs = (12 + s * 38).toLong()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val amplitude = (60 + s * 195).toInt().coerceIn(1, 255)
            v.vibrate(VibrationEffect.createOneShot(durationMs, amplitude))
        } else {
            @Suppress("DEPRECATION")
            v.vibrate(durationMs)
        }
    }
}
