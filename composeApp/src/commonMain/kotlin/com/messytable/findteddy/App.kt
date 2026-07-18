package com.messytable.findteddy

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.messytable.findteddy.platform.SpeechSynthesizer
import com.messytable.findteddy.ui.GameScreen
import com.messytable.findteddy.ui.StartScreen
import com.messytable.findteddy.ui.WinScreen

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
