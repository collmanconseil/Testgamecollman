package com.testgamecollman.luciole.platformer

import kotlin.math.floor

class Body {
    var x = 0f
    var y = 0f
    var vx = 0f
    var vy = 0f
    var onGround = false
    var coyote = 0f
    var jumpBuffer = 0f
    var facing = 1

    fun copyFrom(o: Body) {
        x = o.x; y = o.y; vx = o.vx; vy = o.vy
        onGround = o.onGround; coyote = o.coyote; jumpBuffer = o.jumpBuffer; facing = o.facing
    }
}

object Physics {
    const val W = 0.6f
    const val H = 0.9f
    const val SPEED = 6f
    const val ACCEL = 45f
    const val GRAVITY = 38f
    const val JUMP = 13.5f
    const val MAX_FALL = 18f
    const val STEP = 1f / 120f
    private const val EPS = 0.001f

    private fun fl(v: Float) = floor(v).toInt()

    private fun hitsSolid(level: Level, x: Float, y: Float): Boolean {
        for (ty in fl(y)..fl(y + H - EPS)) for (tx in fl(x)..fl(x + W - EPS)) {
            if (level.solid(tx, ty)) return true
        }
        return false
    }

    /**
     * Avance le héros d'un pas [dt] (au plus [STEP]). [dir] vaut -1, 0 ou 1.
     * Renvoie false s'il meurt (pointes ou chute dans le vide).
     */
    fun step(level: Level, b: Body, dir: Int, jumpHeld: Boolean, jumpPressed: Boolean, dt: Float): Boolean {
        // Course.
        val target = dir * SPEED
        val dv = ACCEL * dt
        b.vx = if (b.vx < target) minOf(b.vx + dv, target) else maxOf(b.vx - dv, target)
        if (dir != 0) b.facing = dir

        // Saut, avec petite tolérance avant l'atterrissage et après le bord.
        b.jumpBuffer = if (jumpPressed) 0.12f else b.jumpBuffer - dt
        b.coyote = if (b.onGround) 0.1f else b.coyote - dt
        if (b.jumpBuffer > 0f && b.coyote > 0f) {
            b.vy = -JUMP
            b.jumpBuffer = 0f
            b.coyote = 0f
            b.onGround = false
        }
        // Relâcher le bouton raccourcit le saut.
        val g = if (b.vy < 0f && !jumpHeld) GRAVITY * 2.5f else GRAVITY
        b.vy = minOf(b.vy + g * dt, MAX_FALL)

        // Axe horizontal.
        val nx = b.x + b.vx * dt
        if (hitsSolid(level, nx, b.y)) {
            b.x = if (b.vx > 0f) fl(nx + W) - W - EPS else fl(nx) + 1f
            b.vx = 0f
        } else b.x = nx

        // Axe vertical.
        val ny = b.y + b.vy * dt
        b.onGround = false
        if (b.vy >= 0f) {
            val oldBottom = b.y + H
            val row = fl(ny + H)
            var landed = false
            if (row != fl(oldBottom - EPS) || oldBottom <= row + EPS) {
                for (tx in fl(b.x)..fl(b.x + W - EPS)) {
                    if (level.solid(tx, row) || (level.oneWay(tx, row) && oldBottom <= row + EPS)) landed = true
                }
            }
            if (landed) {
                b.y = row - H
                b.vy = 0f
                b.onGround = true
            } else b.y = ny
        } else if (hitsSolid(level, b.x, ny)) {
            b.y = fl(ny) + 1f
            b.vy = 0f
        } else b.y = ny

        // Dangers.
        if (b.y > level.height + 1f) return false
        for (ty in fl(b.y)..fl(b.y + H - EPS)) for (tx in fl(b.x)..fl(b.x + W - EPS)) {
            if (level.spike(tx, ty) &&
                b.x + W > tx + 0.15f && b.x < tx + 0.85f && b.y + H > ty + 0.45f
            ) return false
        }
        return true
    }

    fun atDoor(level: Level, b: Body): Boolean =
        b.x + W > level.doorX + 0.2f && b.x < level.doorX + 0.8f &&
            b.y + H > level.doorY - 1f && b.y < level.doorY + 1f
}
