package com.messytable.findteddy.platform

import android.media.AudioAttributes
import android.media.SoundPool
import java.io.File
import kotlinx.coroutines.CompletableDeferred

actual class VoicePlayer actual constructor(clips: Map<String, ByteArray>) {
    private val soundPool = SoundPool.Builder()
        .setMaxStreams(2)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
        )
        .build()
    private val loadedIds = mutableSetOf<Int>()
    private val soundIds: Map<String, Int>
    private var currentStream = 0
    private val ready = CompletableDeferred<Unit>()

    init {
        var completed = 0
        soundPool.setOnLoadCompleteListener { _, sampleId, status ->
            if (status == 0) loadedIds += sampleId
            if (++completed == clips.size) ready.complete(Unit)
        }
        val dir = File(appContext.cacheDir, "voice").apply { mkdirs() }
        soundIds = clips.mapValues { (name, bytes) ->
            val file = File(dir, name)
            file.writeBytes(bytes)
            soundPool.load(file.path, 1)
        }
        if (clips.isEmpty()) ready.complete(Unit)
    }

    actual suspend fun awaitReady() = ready.await()

    actual fun play(clip: String): Boolean {
        val id = soundIds[clip] ?: return false
        if (id !in loadedIds) return false
        if (currentStream != 0) soundPool.stop(currentStream)
        currentStream = soundPool.play(id, 1f, 1f, 3, 0, 1f)
        return currentStream != 0
    }
}
