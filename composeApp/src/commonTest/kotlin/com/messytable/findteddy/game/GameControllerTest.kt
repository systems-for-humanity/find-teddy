package com.messytable.findteddy.game

import com.messytable.findteddy.i18n.GameStrings
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

private const val W = 1000f
private const val H = 2000f
private const val DT = 1f / 60f

class GameControllerTest {

    private fun testStrings() = GameStrings(
        bannerTouch = "Touch the {color} balls!",
        bannerFindTeddy = "Find Teddy!",
        speakFirstPrompt = "Teddy is lost under the balls! Touch the {color} balls!",
        speakNextPrompt = "Great job! Now touch the {color} balls!",
        speakWrong = "No, that is {color}. Touch the {target} balls!",
        speakAllClean = "All clean! Where is Teddy? Touch Teddy!",
        speakDetermined = "Wow! You are determined, partner!",
        speakWin = "Hooray! You found Teddy!",
        colorNames = BallColor.entries.associateWith { it.label },
    )

    private fun controller(
        onWin: () -> Unit = {},
        speak: (String) -> Unit = {},
    ): GameController =
        GameController(W, H, strings = testStrings(), speak = speak, onWin = onWin)
            .also { it.startRound() }

    private fun settle(c: GameController, frames: Int = 900) {
        repeat(frames) { c.update(DT) }
    }

    @Test
    fun ballsSettleInsideWalls() {
        val c = controller()
        settle(c)
        for (b in c.balls) {
            assertTrue(b.x >= b.radius - 1f, "ball ${b.id} through left wall: x=${b.x}")
            assertTrue(b.x <= W - b.radius + 1f, "ball ${b.id} through right wall: x=${b.x}")
            assertTrue(b.y <= H - b.radius + 2f, "ball ${b.id} through floor: y=${b.y}")
        }
    }

    @Test
    fun tappingTargetColorPopsBall() {
        val c = controller()
        settle(c)
        val target = c.targetColor!!
        // Topmost ball of the target color is guaranteed to receive the tap.
        val ball = c.balls.last { it.color == target }
        c.tap(ball.x, ball.y)
        val tapped = c.balls.last { b ->
            val dx = ball.x - b.x
            val dy = ball.y - b.y
            dx * dx + dy * dy <= b.radius * b.radius
        }
        if (tapped.color == target) {
            assertTrue(tapped.popping, "target-colored ball should pop")
            settle(c, frames = 60)
            assertFalse(c.balls.contains(tapped), "popped ball should be removed")
        }
    }

    @Test
    fun tappingWrongColorWobblesButDoesNotPop() {
        val c = controller()
        settle(c)
        val target = c.targetColor!!
        val wrong = c.balls.lastOrNull { it.color != target } ?: return
        // Only meaningful if the wrong ball is topmost at its own center.
        val topmost = c.balls.last { b ->
            val dx = wrong.x - b.x
            val dy = wrong.y - b.y
            dx * dx + dy * dy <= b.radius * b.radius
        }
        if (topmost !== wrong) return
        c.tap(wrong.x, wrong.y)
        assertFalse(wrong.popping, "wrong-colored ball must not pop")
        assertTrue(wrong.wobble > 0f, "wrong-colored ball should wobble")
    }

    @Test
    fun tapNearButNotOnBallDoesNotWinWhileTeddyCovered() {
        var won = false
        val c = controller(onWin = { won = true })
        val t = c.teddy
        c.balls.clear()
        // A ball just beside the teddy's head: the tap misses the ball itself
        // (distance 1.2r > r) but the opening is too small (1.2r < 1.35r).
        val r = W * 0.06f
        c.balls += Ball(0, t.headCx - r * 1.2f, t.headCy, r, BallColor.RED, BallType.PLAIN, 1)
        c.tap(t.headCx, t.headCy)
        assertFalse(won, "pinhole gap between balls must not reveal teddy")
    }

    @Test
    fun tappingUncoveredTeddyWins() {
        var won = false
        val c = controller(onWin = { won = true })
        c.balls.clear()
        c.tap(c.teddy.headCx, c.teddy.headCy)
        assertTrue(won, "tapping the fully uncovered teddy should win")
    }

    @Test
    fun clearingTargetColorAdvancesToNextColor() {
        val c = controller()
        settle(c)
        val target = c.targetColor!!
        for (b in c.balls) {
            if (b.color == target) {
                b.popping = true
                b.popProgress = 1f
            }
        }
        c.update(DT)
        assertNotEquals(target, c.targetColor, "a new target color should be chosen")
        assertTrue(c.balls.none { it.color == target }, "old target balls should be gone")
    }

    @Test
    fun clearingAllBallsAsksToFindTeddy() {
        val c = controller()
        for (b in c.balls) {
            b.popping = true
            b.popProgress = 1f
        }
        c.update(DT)
        assertTrue(c.balls.isEmpty())
        assertTrue(c.targetColor == null, "no target color once every ball is gone")
        assertTrue(c.message.contains("Teddy"), "player should be told to find teddy")
    }

    @Test
    fun poppingLastBallOfAColorExplodesIntoParticlesThatFade() {
        val c = controller()
        settle(c, frames = 120)
        val target = c.targetColor!!
        val keep = c.balls.first { it.color == target }
        for (b in c.balls) {
            if (b.color == target && b !== keep) {
                b.popping = true
                b.popProgress = 1f
            }
        }
        c.update(DT) // removes the others; keep survives so the target is unchanged
        // move the survivor into open air so the tap can only hit it
        keep.x = W / 2f
        keep.y = 100f
        c.tap(keep.x, keep.y)
        assertTrue(keep.popping, "last ball of the target color should pop")
        assertTrue(c.particles.isNotEmpty(), "last ball of a color should explode")
        assertTrue(c.balls.any { it.color != target }, "other colors still remain")
        settle(c, frames = 240)
        assertTrue(c.particles.isEmpty(), "explosion particles should fade away")
    }

    @Test
    fun explosionPushesNearbyBallsAway() {
        val c = controller()
        settle(c, frames = 300)
        val target = c.targetColor!!
        val keep = c.balls.first { it.color == target }
        for (b in c.balls) {
            if (b.color == target && b !== keep) {
                b.popping = true
                b.popProgress = 1f
            }
        }
        c.update(DT)
        // stage the blast in open air with one resting neighbor to its right
        keep.x = W / 2f
        keep.y = 300f
        val neighbor = c.balls.first { it !== keep }
        neighbor.x = keep.x + keep.radius + neighbor.radius + 5f
        neighbor.y = keep.y
        neighbor.vx = 0f
        neighbor.vy = 0f
        c.tap(keep.x, keep.y)
        assertTrue(keep.popping, "last ball of the target color should pop")
        assertTrue(neighbor.vx > 0f, "neighbor should be shoved away from the blast")
        assertTrue(neighbor.vy < 0f, "shockwave should kick the neighbor upward")
    }

    @Test
    fun determinedPartnerExplodesStubbornBallWithEscalatingThreshold() {
        val spoken = mutableListOf<String>()
        val c = controller(speak = { spoken += it })
        settle(c, frames = 300)
        val target = c.targetColor!!

        fun stageWrongBall(y: Float): Ball {
            val b = c.balls.first { it.color != target && !it.popping }
            b.x = W / 2f
            b.y = y
            return b
        }

        // Stage 1: threshold 3.
        val w1 = stageWrongBall(150f)
        repeat(2) { c.tap(w1.x, w1.y) }
        assertFalse(w1.popping, "two taps should not explode yet")
        c.tap(w1.x, w1.y)
        assertTrue(w1.popping, "third tap on the same wrong ball should explode it")
        assertTrue(c.particles.isNotEmpty(), "determined blast should throw fragments")
        assertTrue(spoken.any { it.contains("determined") }, "should praise determination")

        // Stage 2: threshold escalates to 4.
        val w2 = stageWrongBall(350f)
        repeat(3) { c.tap(w2.x, w2.y) }
        assertFalse(w2.popping, "threshold is now 4, three taps are not enough")
        c.tap(w2.x, w2.y)
        assertTrue(w2.popping, "fourth tap should explode the next stubborn ball")

        // Stage 3: threshold escalates to 6.
        val w3 = stageWrongBall(550f)
        repeat(5) { c.tap(w3.x, w3.y) }
        assertFalse(w3.popping, "threshold is now 6, five taps are not enough")
        c.tap(w3.x, w3.y)
        assertTrue(w3.popping, "sixth tap should explode")
    }

    @Test
    fun tappingDifferentWrongBallsDoesNotBuildAStreak() {
        val c = controller()
        settle(c, frames = 300)
        val target = c.targetColor!!
        val wrongs = c.balls.filter { it.color != target }.take(2)
        if (wrongs.size < 2) return
        val (a, b) = wrongs
        a.x = W / 2f
        a.y = 150f
        b.x = W / 2f
        b.y = 450f
        repeat(4) {
            c.tap(a.x, a.y)
            c.tap(b.x, b.y)
        }
        assertFalse(a.popping, "alternating taps must not trigger the determined blast")
        assertFalse(b.popping, "alternating taps must not trigger the determined blast")
    }

    @Test
    fun poppingANonLastBallOfAColorDoesNotExplode() {
        val c = controller()
        settle(c, frames = 120)
        val target = c.targetColor!!
        assertTrue(c.balls.count { it.color == target } >= 2)
        val ball = c.balls.first { it.color == target }
        ball.x = W / 2f
        ball.y = 100f
        c.tap(ball.x, ball.y)
        assertTrue(ball.popping)
        assertTrue(c.particles.isEmpty(), "explosion is reserved for the last ball of a color")
    }

    @Test
    fun shakeThrowsSettledBallsUpward() {
        val c = controller()
        settle(c)
        val before = c.balls.associate { it.id to it.y }
        c.shake()
        assertTrue(c.balls.all { it.vy < 0f }, "shake should toss every ball upward")
        settle(c, frames = 30)
        val moved = c.balls.count { kotlin.math.abs(before[it.id]!! - it.y) > 1f }
        assertTrue(moved > c.balls.size / 2, "shake should visibly move the pile")
    }
}
