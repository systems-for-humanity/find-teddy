package app.s4h.findteddy

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import app.s4h.findteddy.platform.SpeechSynthesizer
import app.s4h.findteddy.ui.GameScreen
import app.s4h.findteddy.ui.StartScreen
import app.s4h.findteddy.ui.WinScreen

private enum class Phase { START, PLAYING, WON }

@Composable
fun App() {
    MaterialTheme {
        val speech = remember { SpeechSynthesizer() }
        var phase by remember { mutableStateOf(Phase.START) }
        when (phase) {
            Phase.START -> StartScreen(onPlay = { phase = Phase.PLAYING })
            Phase.PLAYING -> GameScreen(speech = speech, onWin = { phase = Phase.WON })
            Phase.WON -> WinScreen(onReplay = { phase = Phase.PLAYING })
        }
    }
}
