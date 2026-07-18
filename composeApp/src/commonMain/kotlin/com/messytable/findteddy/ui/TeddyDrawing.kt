package com.messytable.findteddy.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import com.messytable.findteddy.game.Teddy

private val FUR = Color(0xFFB07B4F)
private val MUZZLE = Color(0xFFEFDDC5)
private val DETAIL = Color(0xFF4E342E)

fun DrawScope.drawTeddy(t: Teddy) {
    // Ears first so the head overlaps them.
    furCircle(t.earLx, t.earY, t.earR)
    furCircle(t.earRx, t.earY, t.earR)
    drawCircle(MUZZLE, radius = t.earR * 0.55f, center = Offset(t.earLx, t.earY))
    drawCircle(MUZZLE, radius = t.earR * 0.55f, center = Offset(t.earRx, t.earY))

    // Limbs behind the body.
    furCircle(t.armLx, t.armY, t.armR)
    furCircle(t.armRx, t.armY, t.armR)
    furCircle(t.legLx, t.legY, t.legR)
    furCircle(t.legRx, t.legY, t.legR)

    // Body and belly patch.
    furCircle(t.bodyCx, t.bodyCy, t.bodyR)
    drawOval(
        color = MUZZLE,
        topLeft = Offset(t.bodyCx - t.bodyR * 0.55f, t.bodyCy - t.bodyR * 0.45f),
        size = Size(t.bodyR * 1.1f, t.bodyR * 1.2f),
    )

    // Head.
    furCircle(t.headCx, t.headCy, t.headR)
    drawOval(
        color = MUZZLE,
        topLeft = Offset(t.headCx - t.headR * 0.48f, t.headCy + t.headR * 0.02f),
        size = Size(t.headR * 0.96f, t.headR * 0.72f),
    )

    // Eyes, nose, smile.
    val eyeY = t.headCy - t.headR * 0.18f
    drawCircle(DETAIL, radius = t.headR * 0.1f, center = Offset(t.headCx - t.headR * 0.36f, eyeY))
    drawCircle(DETAIL, radius = t.headR * 0.1f, center = Offset(t.headCx + t.headR * 0.36f, eyeY))
    drawCircle(
        Color.White,
        radius = t.headR * 0.035f,
        center = Offset(t.headCx - t.headR * 0.33f, eyeY - t.headR * 0.03f),
    )
    drawCircle(
        Color.White,
        radius = t.headR * 0.035f,
        center = Offset(t.headCx + t.headR * 0.39f, eyeY - t.headR * 0.03f),
    )
    drawOval(
        color = DETAIL,
        topLeft = Offset(t.headCx - t.headR * 0.14f, t.headCy + t.headR * 0.08f),
        size = Size(t.headR * 0.28f, t.headR * 0.2f),
    )
    drawArc(
        color = DETAIL,
        startAngle = 30f,
        sweepAngle = 120f,
        useCenter = false,
        topLeft = Offset(t.headCx - t.headR * 0.22f, t.headCy + t.headR * 0.18f),
        size = Size(t.headR * 0.44f, t.headR * 0.34f),
        style = Stroke(width = t.headR * 0.06f),
    )
}

private fun DrawScope.furCircle(cx: Float, cy: Float, r: Float) {
    val lightCenter = Offset(cx - r * 0.3f, cy - r * 0.35f)
    drawCircle(
        brush = Brush.radialGradient(
            0f to lerp(FUR, Color.White, 0.35f),
            0.55f to FUR,
            1f to lerp(FUR, Color.Black, 0.3f),
            center = lightCenter,
            radius = r * 1.7f,
        ),
        radius = r,
        center = Offset(cx, cy),
    )
}
