package com.messytable.findteddy.platform

/**
 * Plays pre-rendered voice clips (mp3 bytes keyed by clip file name). A new
 * line interrupts the previous one. [play] returns false if the clip is not
 * ready yet so the caller can fall back to runtime TTS. Decoding is
 * asynchronous on android — [awaitReady] suspends until every clip can
 * actually play, so callers should await it before the game starts speaking.
 */
expect class VoicePlayer(clips: Map<String, ByteArray>) {
    suspend fun awaitReady()
    fun play(clip: String): Boolean
}
