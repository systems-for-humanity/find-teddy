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
import androidx.compose.runtime.remember
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
import com.messytable.findteddy.platform.ShakeListener
import com.messytable.findteddy.platform.SpeechSynthesizer

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

@Composable
private fun GamePlayField(speech: SpeechSynthesizer, onWin: () -> Unit) {
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

        val controller = remember(widthPx, heightPx) {
            GameController(widthPx, heightPx, speak = speech::speak, onWin = onWin)
                .also { it.startRound() }
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
                        "📳 Shake to mix!",
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
