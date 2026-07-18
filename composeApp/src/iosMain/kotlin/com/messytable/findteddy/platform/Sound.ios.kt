package com.messytable.findteddy.platform

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.AVFAudio.AVAudioPlayer
import platform.Foundation.NSData
import platform.Foundation.dataWithBytes

@OptIn(ExperimentalForeignApi::class)
actual class GameSoundPlayer actual constructor(
    popWav: ByteArray,
    boomWav: ByteArray,
    bigBoomWav: ByteArray,
) {
    // Small pools so rapid sounds overlap instead of cutting off.
    private val popPlayers = makePlayers(popWav, count = 3)
    private val boomPlayers = makePlayers(boomWav, count = 2)
    private val bigBoomPlayers = makePlayers(bigBoomWav, count = 2)
    private var nextPop = 0
    private var nextBoom = 0
    private var nextBigBoom = 0

    private fun makePlayers(wav: ByteArray, count: Int): List<AVAudioPlayer> {
        val data = wav.usePinned { pinned ->
            NSData.dataWithBytes(pinned.addressOf(0), wav.size.toULong())
        }
        return List(count) {
            AVAudioPlayer(data = data, error = null).apply { prepareToPlay() }
        }
    }

    private fun playFrom(players: List<AVAudioPlayer>, index: Int): Int {
        val player = players[index]
        player.currentTime = 0.0
        player.play()
        return (index + 1) % players.size
    }

    actual fun playPop() {
        nextPop = playFrom(popPlayers, nextPop)
    }

    actual fun playBoom() {
        nextBoom = playFrom(boomPlayers, nextBoom)
    }

    actual fun playBigBoom() {
        nextBigBoom = playFrom(bigBoomPlayers, nextBigBoom)
    }
}
