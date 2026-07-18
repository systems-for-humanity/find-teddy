package com.messytable.findteddy.platform

/**
 * Plays pre-rendered voice clips (mp3 bytes keyed by clip file name). A new
 * line interrupts the previous one. [play] returns false if the clip is not
 * ready yet so the caller can fall back to runtime TTS.
 */
expect class VoicePlayer(clips: Map<String, ByteArray>) {
    fun play(clip: String): Boolean
}
