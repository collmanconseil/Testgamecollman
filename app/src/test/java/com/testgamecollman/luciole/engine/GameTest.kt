package com.testgamecollman.luciole.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class GameTest {

    @Test
    fun mazeIsRectangularAndFullyReachable() {
        val maze = Maze()
        assertTrue(maze.rows.all { it.length == maze.width })
        val seen = HashSet<Pair<Int, Int>>()
        val queue = ArrayDeque(listOf(maze.playerStart))
        seen.add(maze.playerStart)
        while (queue.isNotEmpty()) {
            val (x, y) = queue.removeFirst()
            for (d in Dir.MOVES) {
                val n = maze.wrapX(x + d.dx) to y + d.dy
                if (maze.passable(n.first, n.second) && seen.add(n)) queue.add(n)
            }
        }
        for (y in 0 until maze.height) for (x in 0 until maze.width) {
            if (maze.passable(x, y)) assertTrue("case ($x,$y) inaccessible", (x to y) in seen)
        }
        assertTrue(maze.passable(maze.exit.first, maze.exit.second))
        assertEquals(3, maze.nest.size)
    }

    @Test
    fun noDeadEnds() {
        val maze = Maze()
        for (y in 0 until maze.height) for (x in 0 until maze.width) {
            if (!maze.passable(x, y)) continue
            val exits = Dir.MOVES.count { maze.passable(x + it.dx, y + it.dy) }
            assertTrue("impasse en ($x,$y)", exits >= 2)
        }
    }

    @Test
    fun playerMovesAndEatsPollen() {
        val game = Game(Random(1))
        game.steer(Dir.LEFT)
        repeat(40) { game.update(0.05f) } // 1.5 s de "prêt" + un peu de jeu
        assertTrue(game.score > 0)
        assertTrue(game.player.x < game.maze.playerStart.first)
    }

    @Test
    fun spidersLeaveNestAndStayInBounds() {
        val game = Game(Random(2))
        val rng = Random(7)
        val roamed = HashSet<SpiderKind>()
        repeat(20 * 600) {
            if (rng.nextInt(8) == 0) game.steer(Dir.MOVES[rng.nextInt(4)])
            game.update(0.05f)
            if (game.state == Game.State.GAME_OVER) game.tap()
            for (s in game.spiders) {
                assertTrue(s.x in 0 until game.maze.width && s.y in 0 until game.maze.height)
                assertFalse(game.maze.isWall(s.x, s.y))
                if (s.mode == SpiderMode.ROAMING && s.progress == 0f) {
                    assertTrue(game.maze.passable(s.x, s.y))
                }
            }
            game.spiders.filter { it.mode == SpiderMode.ROAMING }.forEach { roamed.add(it.kind) }
        }
        assertEquals(SpiderKind.values().toSet(), roamed)
    }

    @Test
    fun idlePlayerEventuallyLosesAndCanRestart() {
        val game = Game(Random(3))
        var steps = 0
        while (game.state != Game.State.GAME_OVER && steps++ < 200_000) game.update(0.05f)
        assertEquals(Game.State.GAME_OVER, game.state)
        assertEquals(0, game.lives)
        game.tap()
        assertEquals(Game.State.READY, game.state)
        assertEquals(Game.START_LIVES, game.lives)
    }

    @Test
    fun randomPlayNeverCrashes() {
        val rng = Random(4)
        val game = Game(Random(5))
        repeat(100_000) {
            if (rng.nextInt(10) == 0) game.steer(Dir.MOVES[rng.nextInt(4)])
            if (game.state == Game.State.GAME_OVER) game.tap()
            game.update(rng.nextFloat() * 0.05f)
            val p = game.player
            assertTrue(game.maze.passable(p.x, p.y))
            assertTrue(p.progress in 0f..1f)
        }
    }

    @Test
    fun nectarFrightensRoamingSpiders() {
        val game = Game(Random(6))
        // La luciole descend chercher le nectar en bas à gauche.
        var steps = 0
        while (game.frightTimer == 0f && steps++ < 2000) {
            val p = game.player
            game.steer(if (p.y < 17) (if (p.x > 5) Dir.LEFT else Dir.DOWN) else Dir.LEFT)
            game.update(0.02f)
        }
        assertTrue(game.frightTimer > 0f)
        assertTrue(game.spiders.any { it.frightened })
        assertTrue(game.score >= 50)
    }
}
