package com.testgamecollman.luciole.platformer

import kotlin.math.floor
import kotlin.math.sin

class Enemy(val hover: Boolean, var x: Float, var y: Float) {
    var alive = true
    var dir = -1
    val baseY = y
    var t = 0f

    companion object {
        const val W = 0.8f
        const val H = 0.7f
    }
}

class PlatformGame {

    enum class State { PLAYING, PAUSED, DYING, LEVEL_CLEAR, GAME_OVER, WON }

    var levelIndex = 0
        private set
    var level = Level(Levels.ALL[0])
        private set
    val hero = Body()
    val enemies = ArrayList<Enemy>()

    var state = State.PLAYING
        private set
    var stateTimer = 0f
        private set
    var score = 0
        private set
    var lives = START_LIVES
        private set
    var time = 0f
        private set

    // Commandes, posées par la vue.
    var left = false
    var right = false
    var jump = false
    private var prevJump = false
    private var scoreAtLevelStart = 0

    init {
        newGame()
    }

    fun newGame() {
        score = 0
        lives = START_LIVES
        loadLevel(0)
    }

    private fun loadLevel(i: Int) {
        levelIndex = i
        level = Level(Levels.ALL[i])
        scoreAtLevelStart = score
        hero.copyFrom(Body())
        hero.x = level.startX + (1f - Physics.W) / 2f
        hero.y = level.startY + 1f - Physics.H
        enemies.clear()
        level.walkerSpawns.forEach { (x, y) -> enemies.add(Enemy(false, x + 0.1f, y + 1f - Enemy.H)) }
        level.hoverSpawns.forEach { (x, y) -> enemies.add(Enemy(true, x + 0.1f, y + 0.15f)) }
        prevJump = jump
        state = State.PLAYING
    }

    fun tap() {
        when (state) {
            State.PAUSED -> state = State.PLAYING
            State.GAME_OVER, State.WON -> newGame()
            else -> {}
        }
    }

    fun pause() {
        if (state == State.PLAYING) state = State.PAUSED
    }

    fun update(dt: Float) {
        when (state) {
            State.PAUSED, State.GAME_OVER, State.WON -> return
            State.DYING -> {
                time += dt
                stateTimer -= dt
                // Le héros tombe hors de l'écran.
                hero.vy += Physics.GRAVITY * dt
                hero.y += hero.vy * dt
                if (stateTimer <= 0f) {
                    lives--
                    if (lives <= 0) state = State.GAME_OVER else {
                        score = scoreAtLevelStart
                        loadLevel(levelIndex)
                    }
                }
            }
            State.LEVEL_CLEAR -> {
                time += dt
                stateTimer -= dt
                if (stateTimer <= 0f) {
                    if (levelIndex + 1 < Levels.ALL.size) loadLevel(levelIndex + 1) else state = State.WON
                }
            }
            State.PLAYING -> {
                var remaining = dt
                while (remaining > 0f && state == State.PLAYING) {
                    val h = minOf(remaining, Physics.STEP)
                    remaining -= h
                    time += h
                    step(h)
                }
            }
        }
    }

    private fun die() {
        state = State.DYING
        stateTimer = DYING_TIME
        hero.vy = -9f
        hero.vx = 0f
    }

    private fun step(dt: Float) {
        val dir = (if (right) 1 else 0) - (if (left) 1 else 0)
        val pressed = jump && !prevJump
        prevJump = jump
        val prevBottom = hero.y + Physics.H
        if (!Physics.step(level, hero, dir, jump, pressed, dt)) {
            die()
            return
        }

        // Rouages.
        val x0 = floor(hero.x).toInt()
        val x1 = floor(hero.x + Physics.W).toInt()
        val y0 = floor(hero.y).toInt()
        val y1 = floor(hero.y + Physics.H).toInt()
        for (ty in y0..y1) for (tx in x0..x1) {
            if (level.gears.isEmpty()) break
            val cx = tx + 0.5f
            val cy = ty + 0.5f
            if (cx > hero.x - 0.25f && cx < hero.x + Physics.W + 0.25f &&
                cy > hero.y - 0.25f && cy < hero.y + Physics.H + 0.25f &&
                level.gears.remove(level.index(tx, ty))
            ) score += 100
        }

        // Ennemis.
        for (e in enemies) {
            if (!e.alive) continue
            moveEnemy(e, dt)
            val overlap = hero.x < e.x + Enemy.W && hero.x + Physics.W > e.x &&
                hero.y < e.y + Enemy.H && hero.y + Physics.H > e.y
            if (!overlap) continue
            if (hero.vy > 0f && prevBottom <= e.y + Enemy.H * 0.6f) {
                e.alive = false
                score += 200
                hero.vy = if (jump) -Physics.JUMP else -9f
            } else {
                die()
                return
            }
        }

        if (Physics.atDoor(level, hero)) {
            score += 500
            if (level.gears.isEmpty()) score += 1000
            state = State.LEVEL_CLEAR
            stateTimer = CLEAR_TIME
        }
    }

    private fun moveEnemy(e: Enemy, dt: Float) {
        e.t += dt
        if (e.hover) {
            e.y = e.baseY + sin(e.t * 2f) * 1.4f
            return
        }
        val nx = e.x + e.dir * WALKER_SPEED * dt
        val lead = floor(if (e.dir > 0) nx + Enemy.W else nx).toInt()
        val row = floor(e.y + Enemy.H * 0.5f).toInt()
        if (level.solid(lead, row) || !level.standable(lead, row + 1) || level.spike(lead, row)) {
            e.dir = -e.dir
        } else e.x = nx
    }

    companion object {
        const val START_LIVES = 3
        const val DYING_TIME = 1.2f
        const val CLEAR_TIME = 1.5f
        const val WALKER_SPEED = 2f
    }
}
