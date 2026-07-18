package com.messytable.findteddy.game

import androidx.compose.ui.graphics.Color

enum class BallColor(val label: String, val color: Color) {
    RED("red", Color(0xFFE53935)),
    BLUE("blue", Color(0xFF1E88E5)),
    GREEN("green", Color(0xFF43A047)),
    YELLOW("yellow", Color(0xFFFDD835)),
    PURPLE("purple", Color(0xFF8E24AA)),
    ORANGE("orange", Color(0xFFFB8C00)),
}

enum class BallType {
    PLAIN,
    STRIPED,
    DOTTED,
    STAR,
}

class Ball(
    val id: Int,
    var x: Float,
    var y: Float,
    val radius: Float,
    val color: BallColor,
    val type: BallType,
    val seed: Int,
) {
    var vx = 0f
    var vy = 0f

    /** 0 = normal; grows to 1 while the ball pops, then it is removed. */
    var popProgress = 0f
    var popping = false

    /** 1 -> 0 wrong-tap wiggle feedback. */
    var wobble = 0f
}
