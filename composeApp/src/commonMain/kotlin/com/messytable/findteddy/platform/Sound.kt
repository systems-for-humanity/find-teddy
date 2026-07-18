package com.messytable.findteddy.platform

/**
 * Plays the game's sounds from WAV bytes (CC0 samples bundled in compose
 * resources — see art/SOUNDS.md for provenance). Safe to call in rapid
 * succession.
 */
expect class GameSoundPlayer(popWav: ByteArray, boomWav: ByteArray, bigBoomWav: ByteArray) {
    fun playPop()
    fun playBoom()
    fun playBigBoom()
}
