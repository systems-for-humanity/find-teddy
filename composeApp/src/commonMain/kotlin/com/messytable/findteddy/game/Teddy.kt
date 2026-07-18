package com.messytable.findteddy.game

class Circle(val cx: Float, val cy: Float, val r: Float)

/**
 * The teddy bear sitting at the bottom of the screen, waiting to be dug out.
 * [scale] is the reference length (screen width) all proportions derive from.
 */
class Teddy(val cx: Float, val bottom: Float, val scale: Float) {
    val bodyR = scale * 0.135f
    val headR = scale * 0.10f
    val bodyCx = cx
    val bodyCy = bottom - bodyR
    val headCx = cx
    val headCy = bodyCy - bodyR * 0.85f - headR * 0.55f
    val earR = headR * 0.42f
    val earLx = headCx - headR * 0.72f
    val earRx = headCx + headR * 0.72f
    val earY = headCy - headR * 0.78f
    val armR = bodyR * 0.38f
    val armLx = bodyCx - bodyR * 0.95f
    val armRx = bodyCx + bodyR * 0.95f
    val armY = bodyCy - bodyR * 0.2f
    val legR = bodyR * 0.42f
    val legLx = bodyCx - bodyR * 0.62f
    val legRx = bodyCx + bodyR * 0.62f
    val legY = bottom - legR * 0.9f

    // Balls deliberately do NOT collide with the teddy: he is drawn beneath
    // them, so letting balls settle over him is what buries him.

    private val touchParts = listOf(
        Circle(bodyCx, bodyCy, bodyR),
        Circle(headCx, headCy, headR),
        Circle(earLx, earY, earR),
        Circle(earRx, earY, earR),
        Circle(armLx, armY, armR),
        Circle(armRx, armY, armR),
        Circle(legLx, legY, legR),
        Circle(legRx, legY, legR),
    )

    fun contains(x: Float, y: Float): Boolean = touchParts.any {
        val dx = x - it.cx
        val dy = y - it.cy
        dx * dx + dy * dy <= it.r * it.r
    }
}
