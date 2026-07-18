package app.s4h.findteddy.i18n

import app.s4h.findteddy.game.BallColor
import app.s4h.findteddy.resources.Res
import app.s4h.findteddy.resources.banner_find_teddy
import app.s4h.findteddy.resources.banner_touch
import app.s4h.findteddy.resources.color_blue
import app.s4h.findteddy.resources.color_green
import app.s4h.findteddy.resources.color_orange
import app.s4h.findteddy.resources.color_purple
import app.s4h.findteddy.resources.color_red
import app.s4h.findteddy.resources.color_yellow
import app.s4h.findteddy.resources.speak_all_clean
import app.s4h.findteddy.resources.speak_determined
import app.s4h.findteddy.resources.speak_first_prompt
import app.s4h.findteddy.resources.speak_next_prompt
import app.s4h.findteddy.resources.speak_win
import app.s4h.findteddy.resources.speak_wrong
import app.s4h.findteddy.resources.voice_prerendered
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
