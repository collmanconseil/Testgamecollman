package com.testgamecollman.luciole.platformer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Build
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import kotlin.math.floor
import kotlin.math.min

/** Rendu façon micro-ordinateur 8 bits : gros pixels, palette réduite, fond noir. */
class PlatformView(context: Context) : View(context) {

    private val game = PlatformGame()
    private val prefs = context.getSharedPreferences("bip", Context.MODE_PRIVATE)
    private var best = prefs.getInt("best", 0)

    private var running = false
    private var lastNanos = 0L

    private var tile = 1f
    private var camX = 0f
    private var insetLeft = 0f
    private var insetRight = 0f

    private val pixel = Paint().apply { isFilterBitmap = false; isAntiAlias = false }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
    }
    private val dst = RectF()

    // --- Sprites ---

    private val palette = mapOf(
        'k' to Color.BLACK, 'w' to Color.WHITE, 'y' to YELLOW, 'c' to CYAN, 'b' to BLUE,
        'r' to RED, 'm' to MAGENTA, 'g' to GREEN, 'o' to ORANGE, 'd' to DARK,
    )

    private fun sprite(rows: List<String>, swap: Map<Char, Int> = emptyMap()): Bitmap {
        val bmp = Bitmap.createBitmap(rows[0].length, rows.size, Bitmap.Config.ARGB_8888)
        for (y in rows.indices) for (x in rows[y].indices) {
            val c = rows[y][x]
            val color = swap[c] ?: palette[c] ?: Color.TRANSPARENT
            bmp.setPixel(x, y, color)
        }
        return bmp
    }

    private val brickRows = listOf(
        "ooookooo", "ooookooo", "ooookooo", "kkkkkkkk",
        "okoooooo", "okoooooo", "okoooooo", "kkkkkkkk",
    )
    private val bricks = listOf(
        sprite(brickRows),
        sprite(brickRows, mapOf('o' to BLUE)),
        sprite(brickRows, mapOf('o' to MAGENTA)),
    )
    private val bridge = sprite(
        listOf("yyyyyyyy", "ykykykyk", "dddddddd", "........", "........", "........", "........", "........")
    )
    private val spikes = sprite(
        listOf("........", "........", "........", ".w...w..", ".w...w..", "www.www.", "www.www.", "wwwwwww.")
    )
    private val gear = sprite(
        listOf("..y..y..", ".yyyyyy.", "yyy..yyy", ".y....y.", ".y....y.", "yyy..yyy", ".yyyyyy.", "..y..y..")
    )
    private val door = sprite(
        listOf("gggggggg", "gddddddg", "gddddddg", "gddddddg", "gddddddg", "gddddddg", "gddddddg", "gddddddg") +
            listOf("gdddddyg", "gdddddyg", "gddddddg", "gddddddg", "gddddddg", "gddddddg", "gddddddg", "gddddddg")
    )
    private val heroTop = listOf(
        "...rr...", ".cccccc.", ".cwkcwk.", ".cccccc.", "..cccc..", ".bbbbbb.", "bbbbbbbb", ".bbbbbb.",
    )
    private val heroFrames = listOf(
        sprite(heroTop + listOf("..b..b..", ".ww..ww.")),
        sprite(heroTop + listOf(".b....b.", "ww....ww")),
    )
    private val walkerFrames = listOf(
        sprite(listOf("........", "..rrrr..", ".rrrrrr.", "rwkrrwkr", "rrrrrrrr", "rrrrrrrr", ".r.rr.r.", "r..rr..r")),
        sprite(listOf("........", "..rrrr..", ".rrrrrr.", "rwkrrwkr", "rrrrrrrr", "rrrrrrrr", ".r.rr.r.", ".r.rr.r.")),
    )
    private val hoverFrames = listOf(
        sprite(listOf("m......m", ".mmmmmm.", "mmmmmmmm", "mwkmmwkm", "mmmmmmmm", ".mmmmmm.", "..m..m..", "........")),
        sprite(listOf("........", "mmmmmmmm", "mmmmmmmm", "mwkmmwkm", "mmmmmmmm", ".mmmmmm.", "..m..m..", "........")),
    )

    // --- Cycle de vie ---

    fun resume() {
        running = true
        lastNanos = 0L
        invalidate()
    }

    fun pause() {
        running = false
        game.pause()
        releaseControls()
        saveBest()
    }

    private fun saveBest() {
        if (game.score > best) {
            best = game.score
            prefs.edit().putInt("best", best).apply()
        }
    }

    // --- Commandes tactiles ---

    private fun releaseControls() {
        game.left = false
        game.right = false
        game.jump = false
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val action = event.actionMasked
        if (action == MotionEvent.ACTION_DOWN) {
            if (game.state != PlatformGame.State.PLAYING) {
                game.tap()
                return true
            }
            if (event.y < height * 0.22f && event.x > width * 0.4f && event.x < width * 0.6f) {
                game.pause()
                return true
            }
        }
        var left = false
        var right = false
        var jump = false
        if (action != MotionEvent.ACTION_CANCEL && action != MotionEvent.ACTION_UP) {
            val lifted = if (action == MotionEvent.ACTION_POINTER_UP) event.actionIndex else -1
            val split = insetLeft + (width - insetLeft - insetRight) * 0.17f
            for (i in 0 until event.pointerCount) {
                if (i == lifted) continue
                val x = event.getX(i)
                val low = event.getY(i) > height * 0.3f
                when {
                    x > width * 0.55f -> jump = true
                    low && x < split -> left = true
                    low && x < width * 0.45f -> right = true
                }
            }
        }
        game.left = left
        game.right = right
        game.jump = jump
        return true
    }

    // --- Rendu ---

    override fun onDraw(canvas: Canvas) {
        val now = System.nanoTime()
        val dt = if (lastNanos == 0L) 0f else min((now - lastNanos) / 1e9f, 0.05f)
        lastNanos = now
        if (running) {
            game.update(dt)
            if (game.state == PlatformGame.State.GAME_OVER || game.state == PlatformGame.State.WON) saveBest()
        }

        val insets = rootWindowInsets
        if (insets != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val i = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
            insetLeft = i.left.toFloat()
            insetRight = i.right.toFloat()
        }

        val level = game.level
        tile = height.toFloat() / level.height
        val viewTiles = width / tile
        if (game.state != PlatformGame.State.DYING) {
            camX = (game.hero.x + Physics.W / 2f - viewTiles * 0.45f)
                .coerceIn(0f, (level.width - viewTiles).coerceAtLeast(0f))
        }

        canvas.drawColor(Color.BLACK)
        drawWorld(canvas, level, viewTiles)
        drawControls(canvas)
        drawHud(canvas)
        drawOverlay(canvas)

        if (running) postInvalidateOnAnimation()
    }

    private fun blit(canvas: Canvas, bmp: Bitmap, x: Float, y: Float, w: Float, h: Float, flip: Boolean = false) {
        val l = (x - camX) * tile
        val t = y * tile
        dst.set(l, t, l + w * tile, t + h * tile)
        if (flip) {
            canvas.save()
            canvas.scale(-1f, 1f, dst.centerX(), dst.centerY())
            canvas.drawBitmap(bmp, null, dst, pixel)
            canvas.restore()
        } else canvas.drawBitmap(bmp, null, dst, pixel)
    }

    private fun drawWorld(canvas: Canvas, level: Level, viewTiles: Float) {
        val brick = bricks[game.levelIndex % bricks.size]
        val x0 = floor(camX).toInt()
        val x1 = floor(camX + viewTiles).toInt() + 1

        // Quelques points lumineux fixes en arrière-plan.
        fill.color = DARK
        val dot = tile / 8f
        for (y in 0 until level.height) for (x in x0..x1) {
            if ((x * 31 + y * 17) % 11 == 0 && level.tile(x, y) == '.') {
                val px = (x - camX) * tile + dot * 3f
                val py = y * tile + dot * 4f
                canvas.drawRect(px, py, px + dot, py + dot, fill)
            }
        }

        blit(canvas, door, level.doorX.toFloat(), level.doorY - 1f, 1f, 2f)

        for (y in 0 until level.height) for (x in x0..x1) {
            when (level.tile(x, y)) {
                '#' -> blit(canvas, brick, x.toFloat(), y.toFloat(), 1f, 1f)
                '=' -> blit(canvas, bridge, x.toFloat(), y.toFloat(), 1f, 1f)
                '^' -> blit(canvas, spikes, x.toFloat(), y.toFloat(), 1f, 1f)
            }
        }

        val bob = if ((game.time * 4f).toInt() % 2 == 0) 0f else 0.06f
        for (i in level.gears) {
            val gx = i % level.width
            val gy = i / level.width
            if (gx in x0..x1) blit(canvas, gear, gx + 0.2f, gy + 0.2f - bob, 0.6f, 0.6f)
        }

        val frame = (game.time * 8f).toInt() % 2
        for (e in game.enemies) {
            if (!e.alive) continue
            val frames = if (e.hover) hoverFrames else walkerFrames
            blit(canvas, frames[frame], e.x, e.y + Enemy.H - 0.8f, 0.8f, 0.8f, flip = e.dir > 0)
        }

        val h = game.hero
        val moving = h.onGround && (h.vx > 0.5f || h.vx < -0.5f)
        val heroFrame = if (!h.onGround) 1 else if (moving) (game.time * 12f).toInt() % 2 else 0
        blit(canvas, heroFrames[heroFrame], h.x - 0.1f, h.y + Physics.H - 1f, 0.8f, 1f, flip = h.facing < 0)
    }

    private fun drawControls(canvas: Canvas) {
        if (game.state != PlatformGame.State.PLAYING) return
        val r = height * 0.11f
        val y = height - r * 1.5f
        val usable = width - insetLeft - insetRight
        val lx = insetLeft + usable * 0.085f
        val rx = insetLeft + usable * 0.255f
        val jx = width - insetRight - usable * 0.1f
        button(canvas, lx, y, r, game.left, "<")
        button(canvas, rx, y, r, game.right, ">")
        button(canvas, jx, y, r, game.jump, "^")
    }

    private fun button(canvas: Canvas, x: Float, y: Float, r: Float, pressed: Boolean, label: String) {
        fill.color = Color.WHITE
        fill.alpha = if (pressed) 110 else 45
        canvas.drawCircle(x, y, r, fill)
        text.color = Color.WHITE
        text.alpha = 170
        text.textAlign = Paint.Align.CENTER
        text.textSize = r * 1.1f
        canvas.drawText(label, x, y + r * 0.38f, text)
        text.alpha = 255
    }

    private fun drawHud(canvas: Canvas) {
        val size = height * 0.055f
        val y = size * 1.5f
        text.textSize = size
        text.textAlign = Paint.Align.LEFT
        text.color = YELLOW
        canvas.drawText("SCORE %06d".format(game.score), insetLeft + size, y, text)
        text.color = CYAN
        text.textAlign = Paint.Align.CENTER
        val gears = game.level.gearTotal - game.level.gears.size
        canvas.drawText("II   ROUAGES $gears/${game.level.gearTotal}", width / 2f, y, text)
        text.color = GREEN
        text.textAlign = Paint.Align.RIGHT
        canvas.drawText(
            "NIVEAU ${game.levelIndex + 1}/${Levels.ALL.size}  VIES ${game.lives}",
            width - insetRight - size, y, text,
        )
    }

    private fun drawOverlay(canvas: Canvas) {
        val title: String
        val subtitle: String
        when (game.state) {
            PlatformGame.State.PAUSED -> { title = "PAUSE"; subtitle = "TOUCHEZ POUR REPRENDRE" }
            PlatformGame.State.LEVEL_CLEAR -> { title = "NIVEAU TERMINE"; subtitle = "SCORE ${game.score}" }
            PlatformGame.State.GAME_OVER -> {
                title = "PARTIE TERMINEE"
                subtitle = "SCORE ${game.score}  RECORD ${maxOf(best, game.score)}  TOUCHEZ POUR REJOUER"
            }
            PlatformGame.State.WON -> {
                title = "BRAVO !"
                subtitle = "SCORE ${game.score}  RECORD ${maxOf(best, game.score)}  TOUCHEZ POUR REJOUER"
            }
            else -> return
        }
        val mid = height / 2f
        fill.color = Color.BLACK
        fill.alpha = 200
        canvas.drawRect(0f, mid - height * 0.2f, width.toFloat(), mid + height * 0.2f, fill)
        fill.alpha = 255
        text.textAlign = Paint.Align.CENTER
        text.color = YELLOW
        text.textSize = height * 0.12f
        canvas.drawText(title, width / 2f, mid, text)
        text.color = Color.WHITE
        text.textSize = height * 0.05f
        canvas.drawText(subtitle, width / 2f, mid + height * 0.1f, text)
    }

    private companion object {
        val YELLOW = 0xFFFFE23D.toInt()
        val CYAN = 0xFF3DE0FF.toInt()
        val BLUE = 0xFF3D6BFF.toInt()
        val RED = 0xFFFF4A3D.toInt()
        val MAGENTA = 0xFFE04DFF.toInt()
        val GREEN = 0xFF4DFF6A.toInt()
        val ORANGE = 0xFFFF9A2E.toInt()
        val DARK = 0xFF2A2A4A.toInt()
    }
}
