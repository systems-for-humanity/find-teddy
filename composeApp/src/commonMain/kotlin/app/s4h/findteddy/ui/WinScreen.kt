package app.s4h.findteddy.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.s4h.findteddy.game.BallColor
import app.s4h.findteddy.game.Teddy
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random
import app.s4h.findteddy.resources.Res
import app.s4h.findteddy.resources.play_again
import app.s4h.findteddy.resources.win_title
import org.jetbrains.compose.resources.stringResource

private class Confetto(
    var x: Float,
    var y: Float,
    val size: Float,
    val color: Color,
    val vy: Float,
    val sway: Float,
    val phase: Float,
)

@Composable
fun WinScreen(onReplay: () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize().background(RoomBackground)) {
        val density = LocalDensity.current
        val widthPx = with(density) { maxWidth.toPx() }
        val heightPx = with(density) { maxHeight.toPx() }

        val confetti = remember(widthPx, heightPx) {
            val rnd = Random.Default
            List(90) {
                Confetto(
                    x = rnd.nextFloat() * widthPx,
                    y = rnd.nextFloat() * heightPx - heightPx,
                    size = widthPx * (0.008f + rnd.nextFloat() * 0.014f),
                    color = BallColor.entries[rnd.nextInt(BallColor.entries.size)].color,
                    vy = heightPx * (0.15f + rnd.nextFloat() * 0.3f),
                    sway = widthPx * 0.04f * rnd.nextFloat(),
                    phase = rnd.nextFloat() * 6.28f,
                )
            }
        }
        var tick by remember { mutableLongStateOf(0L) }
        LaunchedEffect(confetti) {
            var last = 0L
            var elapsed = 0f
            while (true) {
                withFrameNanos { now ->
                    if (last != 0L) {
                        val dt = (now - last) / 1_000_000_000f
                        elapsed += dt
                        for (c in confetti) {
                            c.y += c.vy * dt
                            c.x += sin(elapsed * 2f + c.phase) * c.sway * dt
                            if (c.y > heightPx + c.size) c.y = -c.size
                        }
                        tick++
                    }
                    last = now
                }
            }
        }

        Column(
            Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                stringResource(Res.string.win_title),
                fontSize = 34.sp,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center,
                color = Color(0xFF37474F),
            )
            Canvas(Modifier.size(280.dp).padding(vertical = 12.dp)) {
                drawTeddy(
                    Teddy(
                        cx = size.width / 2f,
                        bottom = size.height,
                        scale = min(size.width, size.height) * 2.1f,
                    )
                )
            }
            Button(
                onClick = onReplay,
                shape = RoundedCornerShape(32.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFB8C00)),
            ) {
                Text(
                    stringResource(Res.string.play_again),
                    fontSize = 26.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
            }
        }

        Box(Modifier.fillMaxSize()) {
            Canvas(Modifier.fillMaxSize()) {
                tick // state read: redraw as confetti falls
                for (c in confetti) {
                    drawRect(
                        color = c.color,
                        topLeft = Offset(c.x, c.y),
                        size = Size(c.size, c.size * 1.6f),
                    )
                }
            }
        }
    }
}
