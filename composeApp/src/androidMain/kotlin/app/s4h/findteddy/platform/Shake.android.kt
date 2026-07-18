package app.s4h.findteddy.platform

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import kotlin.math.sqrt

@Composable
actual fun ShakeListener(enabled: Boolean, onShake: () -> Unit) {
    val context = LocalContext.current
    val currentOnShake by rememberUpdatedState(onShake)
    DisposableEffect(enabled) {
        if (!enabled) {
            onDispose { }
        } else {
            val sensorManager =
                context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
            val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
            val listener = object : SensorEventListener {
                private var lastShakeMs = 0L

                override fun onSensorChanged(event: SensorEvent) {
                    val gx = event.values[0] / SensorManager.GRAVITY_EARTH
                    val gy = event.values[1] / SensorManager.GRAVITY_EARTH
                    val gz = event.values[2] / SensorManager.GRAVITY_EARTH
                    val gForce = sqrt(gx * gx + gy * gy + gz * gz)
                    if (gForce > 2.2f) {
                        val now = SystemClock.elapsedRealtime()
                        if (now - lastShakeMs > 700) {
                            lastShakeMs = now
                            currentOnShake()
                        }
                    }
                }

                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
            }
            if (accelerometer != null) {
                sensorManager.registerListener(
                    listener, accelerometer, SensorManager.SENSOR_DELAY_GAME
                )
            }
            onDispose { sensorManager.unregisterListener(listener) }
        }
    }
}
