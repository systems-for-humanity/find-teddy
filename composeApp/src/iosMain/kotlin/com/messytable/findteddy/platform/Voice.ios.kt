package com.messytable.findteddy.platform

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.AVFAudio.AVAudioPlayer
import platform.Foundation.NSData
import platform.Foundation.dataWithBytes

@OptIn(ExperimentalForeignApi::class)
actual class VoicePlayer actual constructor(clips: Map<String, ByteArray>) {
    private val players: Map<String, AVAudioPlayer> = clips.mapValues { (_, bytes) ->
        val data = bytes.usePinned { pinned ->
            NSData.dataWithBytes(pinned.addressOf(0), bytes.size.toULong())
        }
        AVAudioPlayer(data = data, error = null).apply { prepareToPlay() }
    }
    private var current: AVAudioPlayer? = null

    actual fun play(clip: String): Boolean {
        val player = players[clip] ?: return false
        current?.stop()
        player.currentTime = 0.0
        player.play()
        current = player
        return true
    }
}
