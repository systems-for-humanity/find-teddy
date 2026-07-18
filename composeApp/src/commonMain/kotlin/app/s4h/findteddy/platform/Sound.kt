package app.s4h.findteddy.platform

/**
 * Plays the game's sounds from encoded audio bytes (mp3; CC0 samples
 * bundled in compose resources — see art/SOUNDS.md for provenance). Safe to
 * call in rapid succession.
 *
 * [pops] are pitch variants of the balloon pop ordered highest (small
 * balloon) to lowest (big balloon); [playPop] picks one by the popped
 * ball's normalized size (0 = smallest, 1 = biggest).
 */
expect class GameSoundPlayer(
    pops: List<ByteArray>,
    boom: ByteArray,
    bigBoom: ByteArray,
) {
    fun playPop(sizeNorm: Float)
    fun playBoom()
    fun playBigBoom()
}
