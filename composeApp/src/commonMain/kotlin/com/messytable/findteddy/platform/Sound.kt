package com.messytable.findteddy.platform

/**
 * Plays the game's sounds from WAV bytes (CC0 samples bundled in compose
 * resources — see art/SOUNDS.md for provenance). Safe to call in rapid
 * succession.
 *
 * [popWavs] are pitch variants of the balloon pop ordered highest (small
 * balloon) to lowest (big balloon); [playPop] picks one by the popped
 * ball's normalized size (0 = smallest, 1 = biggest).
 */
expect class GameSoundPlayer(
    popWavs: List<ByteArray>,
    boomWav: ByteArray,
    bigBoomWav: ByteArray,
) {
    fun playPop(sizeNorm: Float)
    fun playBoom()
    fun playBigBoom()
}
