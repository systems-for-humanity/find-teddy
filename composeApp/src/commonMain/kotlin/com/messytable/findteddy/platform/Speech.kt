package com.messytable.findteddy.platform

/** Speaks game prompts out loud. A new call interrupts the previous one. */
expect class SpeechSynthesizer() {
    fun speak(text: String)
}
