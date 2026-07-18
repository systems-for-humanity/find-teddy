package com.messytable.findteddy.platform

import android.media.AudioAttributes
import android.media.SoundPool
import java.io.File
import kotlin.math.roundToInt

actual class GameSoundPlayer actual constructor(
    popWavs: List<ByteArray>,
    boomWav: ByteArray,
    bigBoomWav: ByteArray,
) {
    private val soundPool = SoundPool.Builder()
        .setMaxStreams(5)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()
    private val loadedIds = mutableSetOf<Int>()
    private val popIds: List<Int>
    private val boomId: Int
    private val bigBoomId: Int

    init {
        soundPool.setOnLoadCompleteListener { _, sampleId, status ->
            if (status == 0) loadedIds += sampleId
        }
        popIds = popWavs.mapIndexed { i, wav -> load("pop_$i.wav", wav) }
        boomId = load("boom.wav", boomWav)
        bigBoomId = load("boom_big.wav", bigBoomWav)
    }

    private fun load(name: String, wav: ByteArray): Int {
        val file = File(appContext.cacheDir, name)
        file.writeBytes(wav)
        return soundPool.load(file.path, 1)
    }

    actual fun playPop(sizeNorm: Float) {
        if (popIds.isEmpty()) return
        val index = (sizeNorm.coerceIn(0f, 1f) * (popIds.size - 1)).roundToInt()
        val id = popIds[index]
        if (id in loadedIds) soundPool.play(id, 0.9f, 0.9f, 1, 0, 1f)
    }

    actual fun playBoom() {
        if (boomId in loadedIds) soundPool.play(boomId, 1f, 1f, 2, 0, 1f)
    }

    actual fun playBigBoom() {
        if (bigBoomId in loadedIds) soundPool.play(bigBoomId, 1f, 1f, 2, 0, 1f)
    }
}
