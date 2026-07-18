package com.messytable.findteddy.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.withFrameNanos
import com.messytable.findteddy.game.BallColor
import com.messytable.findteddy.game.GameController
import com.messytable.findteddy.game.VoiceLine
import com.messytable.findteddy.game.clipFile
import com.messytable.findteddy.game.text
import com.messytable.findteddy.i18n.GameStrings
import com.messytable.findteddy.i18n.loadGameStrings
import com.messytable.findteddy.platform.GameSoundPlayer
import com.messytable.findteddy.platform.HapticFeedback
import com.messytable.findteddy.platform.ShakeListener
import com.messytable.findteddy.platform.SpeechSynthesizer
import com.messytable.findteddy.platform.VoicePlayer
import kotlinx.coroutines.withTimeoutOrNull
import messytable.composeapp.generated.resources.Res
import messytable.composeapp.generated.resources.shake_hint
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.stringResource

val RoomBackground = Brush.verticalGradient(
    0f to Color(0xFF81D4FA),
    0.65f to Color(0xFFE1F5FE),
    1f to Color(0xFFFFE0B2),
)

@Composable
fun GameScreen(speech: SpeechSynthesizer, onWin: () -> Unit) {
    Box(Modifier.fillMaxSize().background(RoomBackground)) {
        GamePlayField(speech, onWin)
    }
}

@OptIn(ExperimentalResourceApi::class)
@Composable
private fun GamePlayField(speech: SpeechSynthesizer, onWin: () -> Unit) {
    // Strings and voice clips must be resolved before the controller exists:
    // it speaks the first prompt immediately and cannot use suspend resource
    // APIs itself. `strings` is set last so the game starts fully loaded.
    var strings by remember { mutableStateOf<GameStrings?>(null) }
    var voice by remember { mutableStateOf<VoicePlayer?>(null) }
    LaunchedEffect(Unit) {
        val loaded = loadGameStrings()
        if (loaded.voicePrerendered) {
            val clips = buildMap {
                for (line in VoiceLine.all()) {
                    val file = line.clipFile()
                    // A clip may be absent (e.g. a partially recorded custom
                    // voice); those lines fall back to runtime TTS.
                    runCatching { Res.readBytes("files/voice/$file") }
                        .onSuccess { put(file, it) }
                }
            }
            val player = VoicePlayer(clips)
            // Android decodes clips asynchronously; the controller speaks the
            // first prompt the moment it exists, so wait until clips can play
            // (capped in case a decode fails) or that line comes out as TTS.
            withTimeoutOrNull(5_000) { player.awaitReady() }
            voice = player
        }
        strings = loaded
    }
    val gameStrings = strings ?: return

    // Keep the physics floor above the system navigation bar / home indicator
    // so the bottom row of balls stays visible and touchable.
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
    ) {
        val density = LocalDensity.current
        val widthPx = with(density) { maxWidth.toPx() }
        val heightPx = with(density) { maxHeight.toPx() }

        var sound by remember { mutableStateOf<GameSoundPlayer?>(null) }
        LaunchedEffect(Unit) {
            sound = GameSoundPlayer(
                pops = (0 until 6).map { Res.readBytes("files/pop_$it.mp3") },
                boom = Res.readBytes("files/boom.mp3"),
                bigBoom = Res.readBytes("files/boom_big.mp3"),
            )
        }
        val haptics = remember { HapticFeedback() }
        val controller = remember(widthPx, heightPx, gameStrings) {
            GameController(
                width = widthPx,
                height = heightPx,
                strings = gameStrings,
                speak = { line ->
                    if (voice?.play(line.clipFile()) != true) {
                        speech.speak(line.text(gameStrings))
                    }
                },
                onWin = onWin,
                onPop = { sizeNorm ->
                    sound?.playPop(sizeNorm)
                    haptics.tap(0.25f + 0.65f * sizeNorm)
                },
                onExplode = { big ->
                    if (big) sound?.playBigBoom() else sound?.playBoom()
                    haptics.tap(if (big) 1f else 0.85f)
                },
            ).also { it.startRound() }
        }

        LaunchedEffect(controller) {
            var last = 0L
            while (true) {
                withFrameNanos { now ->
                    if (last != 0L) controller.update((now - last) / 1_000_000_000f)
                    last = now
                }
            }
        }

        ShakeListener(enabled = true) { controller.shake() }

        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(controller) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            for (change in event.changes) {
                                if (change.changedToDown()) {
                                    controller.tap(change.position.x, change.position.y)
                                    change.consume()
                                }
                            }
                        }
                    }
                }
        ) {
            controller.frameTick // state read: redraw every physics step
            drawTeddy(controller.teddy)
            for (ball in controller.balls) {
                drawBall(ball)
            }
            for (particle in controller.particles) {
                drawParticle(particle)
            }
        }

        Column(
            Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))
                .padding(top = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Banner(target = controller.targetColor, message = controller.message)
            if (controller.showShakeHint) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color(0xB3FFFFFF),
                ) {
                    Text(
                        stringResource(Res.string.shake_hint),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF546E7A),
                    )
                }
            }
        }
    }
}

@Composable
private fun Banner(target: BallColor?, message: String) {
    if (message.isEmpty()) return
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = Color(0xF2FFFFFF),
        shadowElevation = 8.dp,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
        ) {
            if (target != null) {
                Box(
                    Modifier
                        .size(34.dp)
                        .background(
                            brush = Brush.radialGradient(
                                0f to lerp(target.color, Color.White, 0.55f),
                                0.5f to target.color,
                                1f to lerp(target.color, Color.Black, 0.3f),
                                center = Offset(11f, 11f),
                                radius = 48f,
                            ),
                            shape = CircleShape,
                        )
                )
                Spacer(Modifier.width(12.dp))
            }
            Text(
                message,
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color(0xFF37474F),
            )
        }
    }
}
