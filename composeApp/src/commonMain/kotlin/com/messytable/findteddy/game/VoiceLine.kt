package com.messytable.findteddy.game

import com.messytable.findteddy.i18n.GameStrings

/**
 * Everything the game can say, as data. The UI resolves a line either to a
 * pre-rendered clip (English, see tools/generate_voice_prompts.py) or to a
 * TTS string built from [GameStrings] for other locales.
 */
sealed interface VoiceLine {
    data class FirstPrompt(val color: BallColor) : VoiceLine
    data class NextPrompt(val color: BallColor) : VoiceLine
    data class Wrong(val target: BallColor, val actual: BallColor) : VoiceLine
    data object AllClean : VoiceLine

    /**
     * The praise alone; the game queues a [NextPrompt] right after it, so
     * one clip covers every color.
     */
    data object Determined : VoiceLine
    data object Win : VoiceLine

    companion object {
        /** Every line that has a pre-rendered clip. */
        fun all(): List<VoiceLine> = buildList {
            add(AllClean)
            add(Win)
            add(Determined)
            for (c in BallColor.entries) {
                add(FirstPrompt(c))
                add(NextPrompt(c))
                for (a in BallColor.entries) {
                    if (a != c) add(Wrong(c, a))
                }
            }
        }
    }
}

/** Must match the clip names produced by tools/generate_voice_prompts.py. */
fun VoiceLine.clipFile(): String = when (this) {
    is VoiceLine.FirstPrompt -> "first_${color.name.lowercase()}"
    is VoiceLine.NextPrompt -> "next_${color.name.lowercase()}"
    is VoiceLine.Wrong -> "wrong_${target.name.lowercase()}_${actual.name.lowercase()}"
    VoiceLine.AllClean -> "all_clean"
    VoiceLine.Determined -> "determined"
    VoiceLine.Win -> "win"
} + ".mp3"

/** Localized fallback text for runtime TTS. */
fun VoiceLine.text(strings: GameStrings): String {
    fun name(c: BallColor) = strings.colorNames[c] ?: c.label
    return when (this) {
        is VoiceLine.FirstPrompt -> strings.speakFirstPrompt.replace("{color}", name(color))
        is VoiceLine.NextPrompt -> strings.speakNextPrompt.replace("{color}", name(color))
        is VoiceLine.Wrong ->
            strings.speakWrong
                .replace("{target}", name(target))
                .replace("{color}", name(actual))
        VoiceLine.AllClean -> strings.speakAllClean
        VoiceLine.Determined -> strings.speakDetermined
        VoiceLine.Win -> strings.speakWin
    }
}
