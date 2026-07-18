package com.messytable.findteddy.platform

import platform.AVFAudio.AVSpeechBoundary
import platform.AVFAudio.AVSpeechSynthesizer
import platform.AVFAudio.AVSpeechSynthesisVoice
import platform.AVFAudio.AVSpeechUtterance

actual class SpeechSynthesizer actual constructor() {
    private val synthesizer = AVSpeechSynthesizer()

    actual fun speak(text: String) {
        if (synthesizer.speaking) {
            synthesizer.stopSpeakingAtBoundary(AVSpeechBoundary.AVSpeechBoundaryImmediate)
        }
        val utterance = AVSpeechUtterance.speechUtteranceWithString(text)
        utterance.voice = AVSpeechSynthesisVoice.voiceWithLanguage("en-US")
        utterance.rate = 0.45f
        synthesizer.speakUtterance(utterance)
    }
}
