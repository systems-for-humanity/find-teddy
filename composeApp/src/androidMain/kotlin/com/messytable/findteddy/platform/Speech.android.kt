package com.messytable.findteddy.platform

import android.annotation.SuppressLint
import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

@SuppressLint("StaticFieldLeak")
lateinit var appContext: Context

actual class SpeechSynthesizer actual constructor() {
    private var ready = false
    private var pending: String? = null
    private var tts: TextToSpeech? = null

    init {
        tts = TextToSpeech(appContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.getDefault()
                ready = true
                pending?.let { speak(it) }
                pending = null
            }
        }
    }

    actual fun speak(text: String) {
        if (ready) {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "find-teddy")
        } else {
            pending = text
        }
    }
}
