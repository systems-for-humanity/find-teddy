package com.messytable.findteddy.platform

import platform.AVFAudio.AVSpeechBoundary
import platform.AVFAudio.AVSpeechSynthesizer
import platform.AVFAudio.AVSpeechUtterance

actual class SpeechSynthesizer actual constructor() {
    private val synthesizer = AVSpeechSynthesizer()

    actual fun speak(text: String) {
        if (synthesizer.speaking) {
            synthesizer.stopSpeakingAtBoundary(AVSpeechBoundary.AVSpeechBoundaryImmediate)
        }
        // No explicit voice: the default follows the device language, which
        // matches the localized strings being spoken.
        val utterance = AVSpeechUtterance.speechUtteranceWithString(text)
        utterance.rate = 0.45f
        synthesizer.speakUtterance(utterance)
    }
}
