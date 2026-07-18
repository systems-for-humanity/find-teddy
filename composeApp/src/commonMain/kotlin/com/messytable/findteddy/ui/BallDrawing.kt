package com.messytable.findteddy.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.lerp
import com.messytable.findteddy.game.Ball
import com.messytable.findteddy.game.BallType
import com.messytable.findteddy.game.Particle
import kotlin.math.cos
import kotlin.math.sin

fun DrawScope.drawParticle(p: Particle) {
    val alpha = (p.life / p.maxLife).coerceIn(0f, 1f)
    drawCircle(
        color = lerp(p.color.color, Color.White, p.shade).copy(alpha = alpha),
        radius = p.radius * (0.5f + 0.5f * alpha),
        center = Offset(p.x, p.y),
    )
}

/**
 * Draws a ball with radial-gradient shading, a per-type pattern, a dark rim
 * and a specular highlight so it reads as a glossy 3D sphere.
 */
fun DrawScope.drawBall(ball: Ball) {
    val p = ball.popProgress.coerceIn(0f, 1f)
    val alpha = 1f - p
    if (alpha <= 0f) return
    val scale = 1f + p * 0.55f
    val wobbleOffset = sin(ball.wobble * 26f) * ball.wobble * ball.radius * 0.16f
    val cx = ball.x + wobbleOffset
    val cy = ball.y
    val r = ball.radius * scale
    val center = Offset(cx, cy)
    val base = ball.color.color
    val light = lerp(base, Color.White, 0.55f)
    val dark = lerp(base, Color.Black, 0.35f)
    val lightCenter = Offset(cx - r * 0.35f, cy - r * 0.42f)

    drawCircle(
        brush = Brush.radialGradient(
            0f to light, 0.5f to base, 1f to dark,
            center = lightCenter,
            radius = r * 1.7f,
        ),
        radius = r,
        center = center,
        alpha = alpha,
    )

    if (ball.type != BallType.PLAIN) {
        val clip = Path().apply { addOval(Rect(cx - r, cy - r, cx + r, cy + r)) }
        clipPath(clip) {
            drawPattern(ball, cx, cy, r, alpha)
        }
    }

    // Dark rim keeps the sphere illusion on top of the pattern.
    drawCircle(
        brush = Brush.radialGradient(
            0.72f to Color.Transparent,
            1f to Color.Black.copy(alpha = 0.28f * alpha),
            center = center,
            radius = r,
        ),
        radius = r,
        center = center,
    )

    // Specular highlight.
    drawCircle(
        brush = Brush.radialGradient(
            0f to Color.White.copy(alpha = 0.8f * alpha),
            1f to Color.White.copy(alpha = 0f),
            center = lightCenter,
            radius = r * 0.55f,
        ),
        radius = r * 0.55f,
        center = lightCenter,
    )

    if (p > 0f) {
        drawCircle(
            color = Color.White.copy(alpha = 0.8f * alpha),
            radius = r * (1f + p * 0.9f),
            center = center,
            style = Stroke(width = r * 0.12f * (1f - p * 0.5f)),
        )
    }
}

private fun DrawScope.drawPattern(ball: Ball, cx: Float, cy: Float, r: Float, alpha: Float) {
    val white = Color.White.copy(alpha = 0.75f * alpha)
    when (ball.type) {
        BallType.PLAIN -> Unit

        BallType.STRIPED -> {
            for (fx in floatArrayOf(-0.5f, 0f, 0.5f)) {
                drawOval(
                    color = white,
                    topLeft = Offset(cx + fx * r - r * 0.14f, cy - r),
                    size = Size(r * 0.28f, r * 2f),
                )
            }
        }

        BallType.DOTTED -> {
            val start = (ball.seed % 360) * (3.1415927f / 180f)
            for (k in 0 until 6) {
                val a = start + k * (3.1415927f / 3f)
                drawCircle(
                    color = white,
                    radius = r * 0.16f,
                    center = Offset(cx + cos(a) * r * 0.58f, cy + sin(a) * r * 0.58f),
                )
            }
            drawCircle(color = white, radius = r * 0.16f, center = Offset(cx, cy))
        }

        BallType.STAR -> {
            drawPath(starPath(cx, cy, r * 0.52f), color = white)
        }
    }
}

private fun starPath(cx: Float, cy: Float, outer: Float): Path {
    val inner = outer * 0.45f
    val path = Path()
    for (k in 0 until 10) {
        val radius = if (k % 2 == 0) outer else inner
        val a = -3.1415927f / 2f + k * (3.1415927f / 5f)
        val x = cx + cos(a) * radius
        val y = cy + sin(a) * radius
        if (k == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    return path
}
