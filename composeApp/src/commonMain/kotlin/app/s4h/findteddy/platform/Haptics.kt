package app.s4h.findteddy.platform

/**
 * Amplitude-scaled haptic feedback. [tap] takes a strength in 0..1:
 * a light tick for small balloons up to a heavy thump for big ones
 * and explosions.
 */
expect class HapticFeedback() {
    fun tap(strength: Float)
}
