package app.s4h.findteddy.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import platform.CoreMotion.CMMotionManager
import platform.Foundation.NSDate
import platform.Foundation.NSOperationQueue
import platform.Foundation.timeIntervalSince1970
import kotlin.math.sqrt

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun ShakeListener(enabled: Boolean, onShake: () -> Unit) {
    val currentOnShake by rememberUpdatedState(onShake)
    DisposableEffect(enabled) {
        val manager = CMMotionManager()
        if (enabled && manager.accelerometerAvailable) {
            manager.accelerometerUpdateInterval = 1.0 / 30.0
            var lastShake = 0.0
            manager.startAccelerometerUpdatesToQueue(NSOperationQueue.mainQueue) { data, _ ->
                val acceleration = data?.acceleration ?: return@startAccelerometerUpdatesToQueue
                acceleration.useContents {
                    val gForce = sqrt(x * x + y * y + z * z)
                    if (gForce > 2.2) {
                        val now = NSDate().timeIntervalSince1970
                        if (now - lastShake > 0.7) {
                            lastShake = now
                            currentOnShake()
                        }
                    }
                }
            }
        }
        onDispose { manager.stopAccelerometerUpdates() }
    }
}
