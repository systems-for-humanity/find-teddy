package app.s4h.findteddy.platform

import platform.UIKit.UIImpactFeedbackGenerator
import platform.UIKit.UIImpactFeedbackStyle

actual class HapticFeedback actual constructor() {
    private val light = UIImpactFeedbackGenerator(style = UIImpactFeedbackStyle.UIImpactFeedbackStyleLight)
    private val medium = UIImpactFeedbackGenerator(style = UIImpactFeedbackStyle.UIImpactFeedbackStyleMedium)
    private val heavy = UIImpactFeedbackGenerator(style = UIImpactFeedbackStyle.UIImpactFeedbackStyleHeavy)

    init {
        light.prepare()
        medium.prepare()
        heavy.prepare()
    }

    actual fun tap(strength: Float) {
        val s = strength.coerceIn(0f, 1f)
        val generator = when {
            s < 0.4f -> light
            s < 0.75f -> medium
            else -> heavy
        }
        generator.impactOccurredWithIntensity(0.4 + 0.6 * s.toDouble())
        generator.prepare()
    }
}
