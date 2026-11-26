package com.testgamecollman.luciole.platformer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.roundToInt

class PlatformTest {

    private class Node(val body: Body, val held: Boolean)

    /** Cherche une suite de commandes menant à la porte (ennemis ignorés). */
    private var far = 0f
    private fun solvable(level: Level): Boolean {
        far = 0f
        val start = Body().apply {
            x = level.startX + (1f - Physics.W) / 2f
            y = level.startY + 1f - Physics.H
        }
        val seen = HashSet<Long>()
        val queue = ArrayDeque<Node>()
        queue.add(Node(start, false))
        while (queue.isNotEmpty() && seen.size < 3_000_000) {
            val n = queue.removeFirst()
            for (dir in -1..1) for (jump in listOf(false, true)) {
                val b = Body().apply { copyFrom(n.body) }
                var alive = true
                var held = n.held
                for (i in 0 until 10) {
                    alive = Physics.step(level, b, dir, jump, jump && !held, Physics.STEP)
                    held = jump
                    if (!alive) break
                    if (Physics.atDoor(level, b)) return true
                }
                if (!alive) continue
                if (b.onGround && b.x > far) far = b.x
                val key = ((b.x * 5).roundToInt().toLong() shl 40) or
                    (((b.y * 5).roundToInt() + 50).toLong() shl 24) or
                    (((b.vx / 2f).roundToInt() + 8).toLong() shl 16) or
                    (((b.vy / 3f).roundToInt() + 16).toLong() shl 4) or
                    (if (b.onGround) 2L else 0L) or (if (held) 1L else 0L)
                if (seen.add(key)) queue.add(Node(b, held))
            }
        }
        return false
    }

    @Test
    fun everyLevelIsWellFormedAndSolvable() {
        Levels.ALL.forEachIndexed { i, src ->
            val level = Level(src)
            assertEquals("niveau ${i + 1}: lignes", 12, level.height)
            assertEquals("niveau ${i + 1}: un départ", 1, src.sumOf { r -> r.count { it == 'S' } })
            assertEquals("niveau ${i + 1}: une porte", 1, src.sumOf { r -> r.count { it == 'D' } })
            assertTrue("niveau ${i + 1}: départ sur du sol", level.standable(level.startX, level.startY + 1))
            assertTrue("niveau ${i + 1}: porte sur du sol", level.standable(level.doorX, level.doorY + 1))
            for ((x, y) in level.walkerSpawns) {
                assertTrue("niveau ${i + 1}: robot en ($x,$y) sans sol", level.standable(x, y + 1))
            }
            assertTrue("niveau ${i + 1}: des rouages", level.gearTotal > 0)
            val ok = solvable(level)
            assertTrue("niveau ${i + 1} infaisable (bloqué vers x=$far)", ok)
        }
    }

    @Test
    fun heroStandsStillOnGround() {
        val game = PlatformGame()
        val y = game.hero.y
        repeat(120) { game.update(1f / 60f) }
        assertEquals(PlatformGame.State.PLAYING, game.state)
        assertEquals(y, game.hero.y, 0.01f)
        assertTrue(game.hero.onGround)
    }

    @Test
    fun heroRunsAndJumps() {
        val game = PlatformGame()
        val x = game.hero.x
        val y = game.hero.y
        game.right = true
        repeat(20) { game.update(1f / 60f) }
        assertTrue(game.hero.x > x + 1f)
        game.right = false
        game.jump = true
        var top = y
        repeat(60) { game.update(1f / 60f); top = minOf(top, game.hero.y) }
        assertTrue("saut trop bas : ${y - top}", y - top > 2.1f)
        assertTrue(game.hero.onGround)
    }

    @Test
    fun walkersNeverLeaveTheirPlatform() {
        val game = PlatformGame()
        repeat(60 * 60) {
            game.update(1f / 60f)
            if (game.state != PlatformGame.State.PLAYING) game.tap()
        }
        for (e in game.enemies) if (!e.hover) {
            val row = (e.y + Enemy.H).roundToInt()
            assertTrue(game.level.standable((e.x + Enemy.W / 2).toInt(), row))
        }
    }

    @Test
    fun runningBlindlyLosesAllLivesThenRestarts() {
        val game = PlatformGame()
        game.right = true
        var steps = 0
        while (game.state != PlatformGame.State.GAME_OVER && steps++ < 100_000) game.update(1f / 60f)
        assertEquals(PlatformGame.State.GAME_OVER, game.state)
        game.tap()
        assertEquals(PlatformGame.State.PLAYING, game.state)
        assertEquals(PlatformGame.START_LIVES, game.lives)
    }
}
