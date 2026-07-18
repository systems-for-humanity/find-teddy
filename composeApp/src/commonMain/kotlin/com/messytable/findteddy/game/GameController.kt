package com.messytable.findteddy.game

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.messytable.findteddy.i18n.GameStrings
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random
import kotlin.time.TimeSource

class Particle(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    val radius: Float,
    val color: BallColor,
    /** 0..1 blend toward white, so fragments get varied shades. */
    val shade: Float,
    var life: Float,
    val maxLife: Float,
)

/**
 * Holds all mutable game state and runs the simulation. Physics works in
 * canvas pixels; every size is derived from the screen dimensions so the
 * game scales to any device.
 */
class GameController(
    val width: Float,
    val height: Float,
    private val strings: GameStrings,
    private val speak: (String) -> Unit,
    private val onWin: () -> Unit,
    private val onPop: () -> Unit = {},
    private val onExplode: (big: Boolean) -> Unit = {},
) {
    val balls = mutableListOf<Ball>()
    val particles = mutableListOf<Particle>()
    val teddy = Teddy(cx = width / 2f, bottom = height, scale = width)

    /** Bumped every physics step so the canvas redraws each frame. */
    var frameTick by mutableLongStateOf(0L)
        private set

    var targetColor by mutableStateOf<BallColor?>(null)
        private set
    var message by mutableStateOf("")
        private set
    var showShakeHint by mutableStateOf(true)
        private set

    private val rnd = Random.Default
    private val clock = TimeSource.Monotonic
    private val startMark = clock.markNow()
    private var lastWrongSpeak = startMark
    private var won = false

    // "Determined partner": tapping the same wrong ball enough times in a
    // row blows it up. The required count escalates 3, 4, 6, 7, 9, 10, ...
    private var wrongBallId = -1
    private var wrongStreak = 0
    private var determinedThreshold = 3
    private var thresholdStep = 1

    fun startRound() {
        balls.clear()
        particles.clear()
        won = false
        wrongBallId = -1
        wrongStreak = 0
        determinedThreshold = 3
        thresholdStep = 1
        // Enough balls to cover roughly the lower ~60% of the screen once
        // settled, which buries the teddy completely.
        val minR = width * 0.045f
        val maxR = width * 0.070f
        val avgArea = (PI * ((minR + maxR) / 2f) * ((minR + maxR) / 2f)).toFloat()
        val count = ((width * height * 0.72f * 0.82f) / avgArea).toInt().coerceIn(40, 150)
        repeat(count) { i ->
            val r = minR + rnd.nextFloat() * (maxR - minR)
            balls += Ball(
                id = i,
                x = r + rnd.nextFloat() * (width - 2f * r),
                y = -rnd.nextFloat() * height * 1.2f - r,
                radius = r,
                color = BallColor.entries[rnd.nextInt(BallColor.entries.size)],
                type = BallType.entries[rnd.nextInt(BallType.entries.size)],
                seed = rnd.nextInt(),
            )
        }
        pickNextTarget(first = true)
    }

    fun update(dtRaw: Float) {
        val dt = dtRaw.coerceIn(0f, 1f / 30f)
        integrate(dt)
        var removed = false
        balls.removeAll { ball ->
            (ball.popping && ball.popProgress >= 1f).also { if (it) removed = true }
        }
        repeat(4) { solveConstraints() }
        clampToWalls()
        updateParticles(dt)
        if (removed) checkTargetCleared()
        if (showShakeHint && startMark.elapsedNow().inWholeSeconds >= 8) {
            showShakeHint = false
        }
        frameTick++
    }

    private fun integrate(dt: Float) {
        val g = height * 2.2f
        val maxV = height * 3f
        for (b in balls) {
            if (b.popping) {
                b.popProgress += dt * 3.5f
                continue
            }
            b.vy = (b.vy + g * dt).coerceIn(-maxV, maxV)
            b.vx = (b.vx * (1f - 0.15f * dt)).coerceIn(-maxV, maxV)
            b.x += b.vx * dt
            b.y += b.vy * dt
            if (b.wobble > 0f) b.wobble = (b.wobble - dt * 2.5f).coerceAtLeast(0f)
        }
    }

    private fun solveConstraints() {
        for (i in balls.indices) {
            val a = balls[i]
            if (a.popping) continue
            // Side and bottom walls. The top stays open so shaken balls can fly up.
            if (a.x < a.radius) {
                a.x = a.radius
                if (a.vx < 0f) a.vx = -a.vx * 0.3f
            } else if (a.x > width - a.radius) {
                a.x = width - a.radius
                if (a.vx > 0f) a.vx = -a.vx * 0.3f
            }
            if (a.y > height - a.radius) {
                a.y = height - a.radius
                if (a.vy > 0f) a.vy = -a.vy * 0.2f
                a.vx *= 0.92f
            }
            if (a.y < -height * 2f) a.y = -height * 2f

            for (j in i + 1 until balls.size) {
                val b = balls[j]
                if (!b.popping) collide(a, b)
            }
        }
    }

    private fun collide(a: Ball, b: Ball) {
        val dx = b.x - a.x
        val dy = b.y - a.y
        val minDist = a.radius + b.radius
        val d2 = dx * dx + dy * dy
        if (d2 >= minDist * minDist || d2 < 1e-6f) return
        val d = sqrt(d2)
        val nx = dx / d
        val ny = dy / d
        val overlap = minDist - d
        val ma = a.radius * a.radius
        val mb = b.radius * b.radius
        val total = ma + mb
        a.x -= nx * overlap * (mb / total)
        a.y -= ny * overlap * (mb / total)
        b.x += nx * overlap * (ma / total)
        b.y += ny * overlap * (ma / total)
        val relVn = (b.vx - a.vx) * nx + (b.vy - a.vy) * ny
        if (relVn > 0f) return
        val e = 0.12f
        val j = -(1f + e) * relVn / (1f / ma + 1f / mb)
        val ix = j * nx
        val iy = j * ny
        a.vx -= ix / ma
        a.vy -= iy / ma
        b.vx += ix / mb
        b.vy += iy / mb
    }

    /**
     * Final position-only pass: ball-ball resolution running after the wall
     * checks inside [solveConstraints] can leave a ball slightly inside a
     * wall, so walls get the last word.
     */
    private fun clampToWalls() {
        for (b in balls) {
            if (b.popping) continue
            b.x = b.x.coerceIn(b.radius, width - b.radius)
            if (b.y > height - b.radius) b.y = height - b.radius
        }
    }

    fun shake() {
        if (won) return
        for (b in balls) {
            if (b.popping) continue
            b.vy -= (0.9f + rnd.nextFloat() * 1.3f) * height
            b.vx += (rnd.nextFloat() - 0.5f) * width * 3.6f
        }
        showShakeHint = false
    }

    private fun updateParticles(dt: Float) {
        if (particles.isEmpty()) return
        val g = height * 2.2f
        for (p in particles) {
            p.vy += g * dt
            p.x += p.vx * dt
            p.y += p.vy * dt
            p.life -= dt
        }
        particles.removeAll { it.life <= 0f }
    }

    /**
     * A ball bursts into flying fragments with a shockwave radiating from
     * its position. At scale 1 the total kick is almost as strong as a
     * shake; the "determined partner" blast uses scale 2.
     */
    private fun explode(b: Ball, scale: Float = 1f) {
        onExplode(scale > 1f)
        val blastRadius = (width + height) * 0.35f * scale
        val blastStrength = height * 2.2f * scale
        for (other in balls) {
            if (other === b || other.popping) continue
            val dx = other.x - b.x
            val dy = other.y - b.y
            val d = sqrt(dx * dx + dy * dy).coerceAtLeast(1f)
            if (d > blastRadius) continue
            val falloff = 1f - d / blastRadius
            other.vx += dx / d * blastStrength * falloff
            other.vy += (dy / d * blastStrength - height * 0.5f * scale) * falloff
        }
        repeat((48 * scale).toInt()) {
            val angle = rnd.nextFloat() * 2f * PI.toFloat()
            val speed = (0.3f + rnd.nextFloat() * 0.9f) * width * (0.5f + 0.5f * scale)
            val life = 0.7f + rnd.nextFloat() * 0.8f
            particles += Particle(
                x = b.x,
                y = b.y,
                vx = cos(angle) * speed,
                vy = sin(angle) * speed - height * 0.35f,
                radius = b.radius * (0.08f + rnd.nextFloat() * 0.16f),
                color = b.color,
                shade = rnd.nextFloat() * 0.7f,
                life = life,
                maxLife = life,
            )
        }
    }

    fun tap(x: Float, y: Float) {
        if (won) return
        val hit = balls.lastOrNull { b ->
            if (b.popping) return@lastOrNull false
            val dx = x - b.x
            val dy = y - b.y
            dx * dx + dy * dy <= b.radius * b.radius
        }
        if (hit != null) {
            val target = targetColor ?: return
            if (hit.color == target) {
                wrongBallId = -1
                wrongStreak = 0
                hit.popping = true
                onPop()
                if (balls.none { !it.popping && it.color == hit.color }) explode(hit)
            } else {
                if (hit.id == wrongBallId) wrongStreak++ else {
                    wrongBallId = hit.id
                    wrongStreak = 1
                }
                if (wrongStreak >= determinedThreshold) {
                    wrongBallId = -1
                    wrongStreak = 0
                    determinedThreshold += thresholdStep
                    thresholdStep = 3 - thresholdStep // alternate +1, +2
                    hit.popping = true
                    explode(hit, scale = 2f)
                    speak(strings.speakDetermined)
                } else {
                    hit.wobble = 1f
                    hit.vx += (rnd.nextFloat() - 0.5f) * width * 0.2f
                    if (lastWrongSpeak.elapsedNow().inWholeMilliseconds > 2500) {
                        lastWrongSpeak = clock.markNow()
                        speak(
                            strings.speakWrong
                                .replace("{color}", colorName(hit.color))
                                .replace("{target}", colorName(target))
                        )
                    }
                }
            }
            return
        }
        if (teddy.contains(x, y) && isUncoveredAt(x, y)) {
            won = true
            speak(strings.speakWin)
            onWin()
        }
    }

    private fun colorName(c: BallColor): String = strings.colorNames[c] ?: c.label

    /**
     * True when there is a real opening at (x, y), not just the sliver of a
     * gap between touching balls. Prevents winning through a pinhole while
     * the teddy is still buried.
     */
    private fun isUncoveredAt(x: Float, y: Float): Boolean = balls.none { b ->
        if (b.popping) return@none false
        val dx = x - b.x
        val dy = y - b.y
        val reach = b.radius * 1.35f
        dx * dx + dy * dy < reach * reach
    }

    private fun checkTargetCleared() {
        val target = targetColor ?: return
        if (balls.any { it.color == target && !it.popping }) return
        if (balls.isEmpty()) {
            targetColor = null
            message = strings.bannerFindTeddy
            speak(strings.speakAllClean)
        } else {
            pickNextTarget(first = false)
        }
    }

    private fun pickNextTarget(first: Boolean) {
        val available = BallColor.entries.filter { c -> balls.any { it.color == c && !it.popping } }
        if (available.isEmpty()) return
        val next = available[rnd.nextInt(available.size)]
        targetColor = next
        message = strings.bannerTouch.replace("{color}", colorName(next).uppercase())
        val pattern = if (first) strings.speakFirstPrompt else strings.speakNextPrompt
        speak(pattern.replace("{color}", colorName(next)))
    }
}
