package com.messytable.findteddy.i18n

import com.messytable.findteddy.game.BallColor
import messytable.composeapp.generated.resources.Res
import messytable.composeapp.generated.resources.banner_find_teddy
import messytable.composeapp.generated.resources.banner_touch
import messytable.composeapp.generated.resources.color_blue
import messytable.composeapp.generated.resources.color_green
import messytable.composeapp.generated.resources.color_orange
import messytable.composeapp.generated.resources.color_purple
import messytable.composeapp.generated.resources.color_red
import messytable.composeapp.generated.resources.color_yellow
import messytable.composeapp.generated.resources.speak_all_clean
import messytable.composeapp.generated.resources.speak_determined
import messytable.composeapp.generated.resources.speak_first_prompt
import messytable.composeapp.generated.resources.speak_next_prompt
import messytable.composeapp.generated.resources.speak_win
import messytable.composeapp.generated.resources.speak_wrong
import messytable.composeapp.generated.resources.voice_prerendered
import org.jetbrains.compose.resources.getString

/**
 * All strings GameController needs, resolved once up front because plain
 * game logic cannot call suspend/composable resource APIs at speak time.
 * Patterns carry {color} and {target} placeholders.
 */
class GameStrings(
    val bannerTouch: String,
    val bannerFindTeddy: String,
    val speakFirstPrompt: String,
    val speakNextPrompt: String,
    val speakWrong: String,
    val speakAllClean: String,
    val speakDetermined: String,
    val speakWin: String,
    val colorNames: Map<BallColor, String>,
    /** True when this locale ships pre-rendered voice clips (English). */
    val voicePrerendered: Boolean,
)

suspend fun loadGameStrings(): GameStrings = GameStrings(
    bannerTouch = getString(Res.string.banner_touch),
    bannerFindTeddy = getString(Res.string.banner_find_teddy),
    speakFirstPrompt = getString(Res.string.speak_first_prompt),
    speakNextPrompt = getString(Res.string.speak_next_prompt),
    speakWrong = getString(Res.string.speak_wrong),
    speakAllClean = getString(Res.string.speak_all_clean),
    speakDetermined = getString(Res.string.speak_determined),
    speakWin = getString(Res.string.speak_win),
    voicePrerendered = getString(Res.string.voice_prerendered) == "true",
    colorNames = mapOf(
        BallColor.RED to getString(Res.string.color_red),
        BallColor.BLUE to getString(Res.string.color_blue),
        BallColor.GREEN to getString(Res.string.color_green),
        BallColor.YELLOW to getString(Res.string.color_yellow),
        BallColor.PURPLE to getString(Res.string.color_purple),
        BallColor.ORANGE to getString(Res.string.color_orange),
    ),
)
